package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Line, Rect, StatefulWidget, Style}

/** Caller-owned list state: the selection and the scroll offset. Mutable on purpose — the widget adjusts the
  * offset during render to keep the selection visible, and the app mutates the selection from key handlers
  * (the `StatefulWidget` contract).
  */
final class ListState(var selected: Option[Int] = None, var offset: Int = 0):

  def selectNext(itemCount: Int): Unit =
    if itemCount > 0 then selected = Some(selected.fold(0)(current => math.min(current + 1, itemCount - 1)))

  def selectPrevious(itemCount: Int): Unit =
    if itemCount > 0 then selected = Some(selected.fold(0)(current => math.max(current - 1, 0)))

/** A scrollable list of single-row items with an optional highlighted selection.
  *
  * Named `ListView` rather than the reference libraries' `List` to avoid colliding with `scala.List` at every
  * call site.
  */
final case class ListView(
    items: Seq[Line],
    highlightStyle: Style = Style.Default.reverse,
    highlightSymbol: String = "> ",
    style: Style = Style.Default,
) extends StatefulWidget[ListState]:

  def render(area: Rect, buffer: Buffer, state: ListState): Unit =
    if !area.isEmpty && items.nonEmpty then
      val selected = state.selected.map(index => math.max(0, math.min(index, items.size - 1)))
      state.selected = selected
      state.offset = scrolledOffset(state.offset, selected, area.height)
      val symbolWidth = CharWidth.of(highlightSymbol)
      val padding = " ".repeat(symbolWidth)
      items.slice(state.offset, state.offset + area.height).zipWithIndex.foreach { (line, row) =>
        val index = state.offset + row
        val isSelected = selected.contains(index)
        val rowStyle = if isSelected then style.patch(highlightStyle) else style
        val prefix = if isSelected then highlightSymbol else padding
        val y = area.y + row
        buffer.setString(area.x, y, prefix, rowStyle)
        val _ = LineRenderer.render(buffer, area.x + symbolWidth, y, line, area.width - symbolWidth, rowStyle)
      }

  private def scrolledOffset(offset: Int, selected: Option[Int], height: Int): Int =
    val maxOffset = math.max(0, items.size - height)
    val clamped = math.max(0, math.min(offset, maxOffset))
    selected match
      case None => clamped
      case Some(index) =>
        if index < clamped then index
        else if index >= clamped + height then index - height + 1
        else clamped
