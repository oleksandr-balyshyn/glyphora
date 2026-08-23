package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, StatefulWidget, Style}

/** Caller-owned single-line editing state. The text is stored as grapheme clusters, so the cursor can never land inside
  * a combining sequence or split an emoji; the cursor is a cluster index in `[0, length]` (the top value meaning
  * "append here").
  *
  * Control characters are dropped on the way in — by the constructor as well as by [[insert]], because a field is just
  * as often seeded from a file or an HTTP response as it is typed into. A control is zero columns wide but still fills
  * a whole `Cell`, so storing one desynchronises the backend's cursor model from the terminal's.
  *
  * Render-thread-only, and mutating it does not by itself schedule a frame. This is a plain mutable object, invisible
  * to the reactive layer: a background result written straight into it stays off screen until something unrelated
  * happens to repaint. Pair the mutation with a `Signal` write, or call `TuiApp.requestRedraw()` from the same
  * render-thread callback that made it.
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
        state.scrollCluster = ClusterRow.scrolledTo(clusters, state.scrollCluster, state.cursor, area.width)
        renderClusters(area, buffer, state, clusters)

  private def renderEmpty(area: Rect, buffer: Buffer): Unit =
    buffer.setString(area.x, area.y, CharWidth.substringByWidth(placeholder, area.width), placeholderStyle)
    if showCursor then buffer.set(area.x, area.y, Cell(ClusterRow.drawnSymbol(placeholderCursorSymbol), cursorStyle))

  private def placeholderCursorSymbol: String =
    val clusters = CharWidth.graphemeClusters(placeholder)
    if clusters.hasNext then clusters.next() else " "

  /** Paints the row, ending with a blank cell past the last cluster so the field's own styling runs to the end of the
    * text rather than stopping mid-line — see [[ClusterRow.draw]] for the wide-glyph and cursor rules.
    */
  private def renderClusters(area: Rect, buffer: Buffer, state: TextInputState, clusters: Vector[String]): Unit =
    ClusterRow.draw(
      buffer,
      x0 = area.x,
      y = area.y,
      right = area.right,
      clusters = clusters,
      scroll = state.scrollCluster,
      cursorAt = state.cursor,
      showCursor = showCursor,
      style = style,
      cursorStyle = cursorStyle,
      paintEndCell = true,
    )
