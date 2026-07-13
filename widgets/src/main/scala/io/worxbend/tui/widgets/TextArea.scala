package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, StatefulWidget, Style}

import scala.collection.mutable

/** Caller-owned multi-line editing state (the Tier 5 editor model).
  *
  * Text is a vector of lines, each a vector of grapheme clusters — the cursor is `(line, column)` in cluster
  * coordinates and can never split a combining sequence or emoji. Every editing operation snapshots onto a bounded undo
  * stack.
  */
final class TextAreaState(initial: String = ""):

  private var lines: Vector[Vector[String]] =
    initial.split("\n", -1).toVector.map(line => CharWidth.graphemeClusters(line).toVector)
  private var line                          = lines.size - 1
  private var column                        = lines.last.size
  private[widgets] var scrollRow: Int       = 0
  private[widgets] var scrollColumn: Int    = 0
  private val undoStack                     = mutable.Stack[(Vector[Vector[String]], Int, Int)]()

  def value: String = lines.map(_.mkString).mkString("\n")

  /** Cursor as `(line, column)` in cluster coordinates. */
  def cursor: (Int, Int) = (line, column)

  def lineCount: Int = lines.size

  private[widgets] def clusterLines: Vector[Vector[String]] = lines

  def insert(text: String): Unit =
    pushUndo()
    val segments        = text.split("\n", -1).toVector.map(seg => CharWidth.graphemeClusters(seg).toVector)
    val (before, after) = lines(line).splitAt(column)
    if segments.size == 1 then
      lines = lines.updated(line, before ++ segments.head ++ after)
      column += segments.head.size
    else
      val first  = before ++ segments.head
      val last   = segments.last ++ after
      val middle = segments.drop(1).dropRight(1)
      lines = lines.take(line) ++ (first +: middle :+ last) ++ lines.drop(line + 1)
      line += segments.size - 1
      column = segments.last.size

  def newline(): Unit = insert("\n")

  def backspace(): Unit =
    if column > 0 then
      pushUndo()
      lines = lines.updated(line, lines(line).patch(column - 1, Nil, 1))
      column -= 1
    else if line > 0 then
      pushUndo()
      val previousLength = lines(line - 1).size
      lines = lines.updated(line - 1, lines(line - 1) ++ lines(line)).patch(line, Nil, 1)
      line -= 1
      column = previousLength

  def delete(): Unit =
    if column < lines(line).size then
      pushUndo()
      lines = lines.updated(line, lines(line).patch(column, Nil, 1))
    else if line < lines.size - 1 then
      pushUndo()
      lines = lines.updated(line, lines(line) ++ lines(line + 1)).patch(line + 1, Nil, 1)

  def moveLeft(): Unit =
    if column > 0 then column -= 1
    else if line > 0 then
      line -= 1
      column = lines(line).size

  def moveRight(): Unit =
    if column < lines(line).size then column += 1
    else if line < lines.size - 1 then
      line += 1
      column = 0

  def moveUp(): Unit =
    if line > 0 then
      line -= 1
      column = math.min(column, lines(line).size)

  def moveDown(): Unit =
    if line < lines.size - 1 then
      line += 1
      column = math.min(column, lines(line).size)

  def moveHome(): Unit = column = 0

  def moveEnd(): Unit = column = lines(line).size

  /** Restores the text and cursor from before the most recent edit; no-op on an empty history. */
  def undo(): Unit =
    if undoStack.nonEmpty then
      val (savedLines, savedLine, savedColumn) = undoStack.pop()
      lines = savedLines
      line = savedLine
      column = savedColumn

  private def pushUndo(): Unit =
    undoStack.push((lines, line, column))
    if undoStack.size > TextAreaState.UndoLimit then
      val kept = undoStack.take(TextAreaState.UndoLimit)
      undoStack.clear()
      undoStack.pushAll(kept.reverse)

object TextAreaState:
  private val UndoLimit = 100

/** A multi-line text editor view: vertical and horizontal scroll follow the cursor, which renders as a highlighted cell
  * (`showCursor = false` for unfocused areas). No syntax highlighting.
  */
final case class TextArea(
    showCursor: Boolean = true,
    style: Style = Style.Default,
    cursorStyle: Style = Style.Default.reverse,
) extends StatefulWidget[TextAreaState]:

  def render(area: Rect, buffer: Buffer, state: TextAreaState): Unit =
    if !area.isEmpty then
      val (cursorLine, cursorColumn) = state.cursor
      state.scrollRow = scrolled(state.scrollRow, cursorLine, area.height)
      state.scrollColumn = scrolledHorizontally(state, cursorColumn, area.width)
      state.clusterLines.slice(state.scrollRow, state.scrollRow + area.height).zipWithIndex.foreach { (clusters, row) =>
        val lineIndex = state.scrollRow + row
        renderLine(buffer, area, clusters, area.y + row, state, lineIndex == cursorLine)
      }

  private def renderLine(
      buffer: Buffer,
      area: Rect,
      clusters: Vector[String],
      y: Int,
      state: TextAreaState,
      isCursorLine: Boolean,
  ): Unit =
    val cursorColumn = state.cursor._2
    var x            = area.x
    var index        = state.scrollColumn
    while index <= clusters.size && x < area.right do
      val atEnd  = index == clusters.size
      val symbol = if atEnd then " " else clusters(index)
      val width  = math.max(1, CharWidth.of(symbol))
      if x + width <= area.right then
        val isCursor  = showCursor && isCursorLine && index == cursorColumn
        val cellStyle = if isCursor then style.patch(cursorStyle) else style
        if !atEnd || isCursor then
          buffer.set(x, y, Cell(symbol, cellStyle))
          if width == 2 then buffer.set(x + 1, y, Cell.Empty)
      x += width
      index += 1

  private def scrolled(offset: Int, cursorLine: Int, height: Int): Int =
    if cursorLine < offset then cursorLine
    else if cursorLine >= offset + height then cursorLine - height + 1
    else offset

  /** Scrolls all lines left just enough that the cursor's column (measured on its own line) stays visible. */
  private def scrolledHorizontally(state: TextAreaState, cursorColumn: Int, width: Int): Int =
    val clusters                                 = state.clusterLines(state.cursor._1)
    var scroll                                   = math.min(state.scrollColumn, cursorColumn)
    def visibleWidth(from: Int, until: Int): Int = clusters.slice(from, until).map(CharWidth.of).sum
    while visibleWidth(scroll, cursorColumn) + 1 > width && scroll < cursorColumn do scroll += 1
    scroll
