package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, StatefulWidget, Style}

/** Caller-owned single-line editing state. The text is stored as grapheme clusters, so the cursor can never
  * land inside a combining sequence or split an emoji; the cursor is a cluster index in `[0, length]` (the
  * top value meaning "append here").
  */
final class TextInputState(initial: String = ""):

  private var clusters: Vector[String] = CharWidth.graphemeClusters(initial).toVector
  private var cursorIndex: Int = clusters.size
  private[widgets] var scrollCluster: Int = 0

  def value: String = clusters.mkString

  /** Cursor position as a cluster index. */
  def cursor: Int = cursorIndex

  def insert(text: String): Unit =
    val inserted = CharWidth.graphemeClusters(text).toVector
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
    if showCursor then buffer.set(area.x, area.y, Cell(placeholderCursorSymbol, cursorStyle))

  private def placeholderCursorSymbol: String =
    val clusters = CharWidth.graphemeClusters(placeholder)
    if clusters.hasNext then clusters.next() else " "

  /** Scrolls just enough that the cursor stays visible (one column is reserved for the end-of-text cursor). */
  private def scrolledTo(state: TextInputState, width: Int): Int =
    val clusters = state.clusterSeq
    var scroll = math.min(state.scrollCluster, state.cursor)
    def visibleWidth(from: Int, until: Int): Int = clusters.slice(from, until).map(CharWidth.of).sum
    while visibleWidth(scroll, state.cursor).+(1) > width && scroll < state.cursor do scroll += 1
    scroll

  private def renderClusters(area: Rect, buffer: Buffer, state: TextInputState, clusters: Vector[String]): Unit =
    var x = area.x
    var index = state.scrollCluster
    while index <= clusters.size && x < area.right do
      val atEnd = index == clusters.size
      val symbol = if atEnd then " " else clusters(index)
      val width = math.max(1, CharWidth.of(symbol))
      if x + width <= area.right then
        val isCursor = showCursor && index == state.cursor
        val cellStyle = if isCursor then style.patch(cursorStyle) else style
        buffer.set(x, area.y, Cell(symbol, cellStyle))
        if width == 2 then buffer.set(x + 1, area.y, Cell.Empty)
      x += width
      index += 1
