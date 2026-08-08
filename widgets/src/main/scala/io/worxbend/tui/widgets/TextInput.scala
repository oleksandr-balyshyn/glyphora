package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, StatefulWidget, Style}

/** Caller-owned single-line editing state. The text is stored as grapheme clusters, so the cursor can never land inside
  * a combining sequence or split an emoji; the cursor is a cluster index in `[0, length]` (the top value meaning
  * "append here").
  *
  * Control characters are dropped on the way in — by the constructor as well as by [[insert]], because a field is just
  * as often seeded from a file or an HTTP response as it is typed into. A control is zero columns wide but still fills
  * a whole `Cell`, so storing one desynchronises the backend's cursor model from the terminal's.
  */
final class TextInputState(initial: String = ""):

  private var clusters: Vector[String]    = clustersOf(initial)
  private var cursorIndex: Int            = clusters.size
  private[widgets] var scrollCluster: Int = 0

  def value: String = clusters.mkString

  /** Cursor position as a cluster index. */
  def cursor: Int = cursorIndex

  def insert(text: String): Unit =
    val inserted        = clustersOf(text)
    val (before, after) = clusters.splitAt(cursorIndex)
    clusters = before ++ inserted ++ after
    cursorIndex += inserted.size

  def backspace(): Unit =
    if cursorIndex > 0 then
      clusters = clusters.patch(cursorIndex - 1, Nil, 1)
      cursorIndex -= 1

  def delete(): Unit =
    if cursorIndex < clusters.size then clusters = clusters.patch(cursorIndex, Nil, 1)

  def moveLeft(): Unit = cursorIndex = math.max(0, cursorIndex - 1)

  def moveRight(): Unit = cursorIndex = math.min(clusters.size, cursorIndex + 1)

  def moveHome(): Unit = cursorIndex = 0

  def moveEnd(): Unit = cursorIndex = clusters.size

  def clear(): Unit =
    clusters = Vector.empty
    cursorIndex = 0
    scrollCluster = 0

  private[widgets] def clusterSeq: Vector[String] = clusters

  /** The single choke point every entry path goes through: reads no field, so the field initializer may call it. */
  private def clustersOf(text: String): Vector[String] =
    CharWidth.graphemeClusters(CharWidth.withoutControls(text)).toVector

/** A single-line text input with horizontal scrolling and an optional visible cursor.
  *
  * The cursor is drawn by styling the cluster under it (or a trailing space) with `cursorStyle` — pass
  * `showCursor = false` for unfocused inputs so only the focused field shows a cursor.
  */
final case class TextInput(
    placeholder: String = "",
    showCursor: Boolean = true,
    style: Style = Style.Default,
    cursorStyle: Style = Style.Default.reverse,
    placeholderStyle: Style = Style.Default.dim,
) extends StatefulWidget[TextInputState]:

  def render(area: Rect, buffer: Buffer, state: TextInputState): Unit =
    if !area.isEmpty then
      val clusters = state.clusterSeq
      if clusters.isEmpty then renderEmpty(area, buffer)
      else
        state.scrollCluster = scrolledTo(state, area.width)
        renderClusters(area, buffer, state, clusters)

  private def renderEmpty(area: Rect, buffer: Buffer): Unit =
    buffer.setString(area.x, area.y, CharWidth.substringByWidth(placeholder, area.width), placeholderStyle)
    if showCursor then buffer.set(area.x, area.y, Cell(drawnSymbol(placeholderCursorSymbol), cursorStyle))

  private def placeholderCursorSymbol: String =
    val clusters = CharWidth.graphemeClusters(placeholder)
    if clusters.hasNext then clusters.next() else " "

  /** Scrolls just enough that the cursor stays visible, reserving the display width of the cluster the cursor sits on —
    * one column when the cursor is past the end of the text, which renders a trailing space. Reserving a flat column
    * would let the solver call a two-column cluster visible that [[renderClusters]] then refuses to draw.
    *
    * The scroll offset is caller-owned state that outlives any one frame, so it has to be pulled *back* as well as
    * pushed forward: deleting text or widening the terminal both leave an offset that is further right than the text
    * now needs, and the field would render blank with its content off the left edge.
    */
  private def scrolledTo(state: TextInputState, width: Int): Int =
    val clusters    = state.clusterSeq
    val cursorWidth = if state.cursor < clusters.size then renderedWidth(clusters(state.cursor)) else 1
    var scroll      = math.min(math.min(state.scrollCluster, state.cursor), rightmostUsefulScroll(clusters, width))
    while visibleWidth(clusters, scroll, state.cursor) + cursorWidth > width && scroll < state.cursor do scroll += 1
    scroll

  /** The furthest-right offset worth scrolling to: the one that just fits the end of the text, cursor column included.
    * Scrolling past it only pushes text off the left edge to leave blanks on the right.
    */
  private def rightmostUsefulScroll(clusters: Vector[String], width: Int): Int =
    var index = clusters.size
    var used  = 1 // the end-of-text cursor always occupies one column
    while index > 0 && used + renderedWidth(clusters(index - 1)) <= width do
      used += renderedWidth(clusters(index - 1))
      index -= 1
    index

  private def visibleWidth(clusters: Vector[String], from: Int, until: Int): Int =
    clusters.slice(from, until).map(renderedWidth).sum

  /** Measurement has to agree with [[renderClusters]], which gives every cluster at least one cell — a zero-width
    * cluster measured as 0 but drawn in 1 makes the scroll arithmetic drift until the cursor is pushed off-screen.
    */
  private def renderedWidth(cluster: String): Int = math.max(1, CharWidth.of(cluster))

  /** The symbol actually drawn for `cluster`. A zero-width cluster — a bare combining mark, or a control that predates
    * [[TextInputState]]'s filter — is drawn as a blank: [[renderedWidth]] gives every cluster a whole cell, and a
    * symbol whose width disagrees with the cell it occupies desynchronises the backend's cursor model from the
    * terminal's. The cell is still written, so the cursor stays visible on it.
    */
  private def drawnSymbol(cluster: String): String = if CharWidth.of(cluster) == 0 then " " else cluster

  private def renderClusters(area: Rect, buffer: Buffer, state: TextInputState, clusters: Vector[String]): Unit =
    var x     = area.x
    var index = state.scrollCluster
    while index <= clusters.size && x < area.right do
      val atEnd  = index == clusters.size
      val symbol = if atEnd then " " else clusters(index)
      val width  = renderedWidth(symbol)
      if x + width <= area.right then
        val isCursor  = showCursor && index == state.cursor
        val cellStyle = if isCursor then style.patch(cursorStyle) else style
        buffer.set(x, area.y, Cell(drawnSymbol(symbol), cellStyle))
        if width == 2 then buffer.set(x + 1, area.y, Cell.Empty)
      x += width
      index += 1
