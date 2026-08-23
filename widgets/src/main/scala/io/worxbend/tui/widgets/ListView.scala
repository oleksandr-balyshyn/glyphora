package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Line, Rect, StatefulWidget, Style}

/** Caller-owned list state: the selection and the scroll offset. Mutable on purpose — the widget adjusts the offset
  * during render to keep the selection visible, and the app mutates the selection from key handlers (the
  * `StatefulWidget` contract).
  */
final class ListState(var selected: Option[Int] = None, var offset: Int = 0):

  def selectNext(itemCount: Int): Unit =
    if itemCount > 0 then selected = Selection.next(selected, itemCount)

  def selectPrevious(itemCount: Int): Unit =
    if itemCount > 0 then selected = Selection.previous(selected, itemCount)

/** A scrollable list of single-row items with an optional highlighted selection.
  *
  * Named `ListView` rather than the reference libraries' `List` to avoid colliding with `scala.List` at every call
  * site.
  *
  * An item is either a plain `String` or a multi-style [[Line]], and the two may be mixed in one list. The union type
  * saves every caller with plain text the `items.map(Line.raw)` ceremony without closing the door on a styled row; it
  * follows the shape `Layout.apply` already uses for its `Int | Double | Constraint` constraints. The normalisation
  * happens once, in [[lines]], rather than on every render.
  */
final case class ListView(
    items: Seq[String | Line],
    style: Style = Style.Default,
    highlightStyle: Style = Style.Default.reverse,
    highlightSymbol: String = "> ",
) extends StatefulWidget[ListState]:

  private val lines: Seq[Line] = items.map {
    case content: String => Line.raw(content)
    case line: Line      => line
  }

  def render(area: Rect, buffer: Buffer, state: ListState): Unit =
    if !area.isEmpty && lines.nonEmpty then
      val selected    = state.selected.map(index => math.max(0, math.min(index, lines.size - 1)))
      state.selected = selected
      state.offset = ScrollWindow.offsetFor(state.offset, selected, lines.size, area.height)
      val symbolWidth = CharWidth.of(highlightSymbol)
      val padding     = " ".repeat(symbolWidth)
      lines.slice(state.offset, state.offset + area.height).zipWithIndex.foreach { (line, row) =>
        val index      = state.offset + row
        val isSelected = selected.contains(index)
        val rowStyle   = if isSelected then style.patch(highlightStyle) else style
        val prefix     = if isSelected then highlightSymbol else padding
        val y          = area.y + row
        // clip to the area, not just to the buffer: a highlight symbol wider than a narrow list would otherwise be
        // written straight over whatever owns the columns to the right
        buffer.setString(area.x, y, CharWidth.substringByWidth(prefix, area.width), rowStyle)
        val _          = LineRenderer.render(buffer, area.x + symbolWidth, y, line, area.width - symbolWidth, rowStyle)
      }
