package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Line, Rect, StatefulWidget, Style}

/** Caller-owned list state: the selection and the scroll offset. Mutable on purpose — the widget adjusts the offset
  * during render to keep the selection visible, and the app mutates the selection from key handlers (the
  * `StatefulWidget` contract).
  *
  * Render-thread-only, and mutating it does not by itself schedule a frame. This is a plain mutable object, invisible
  * to the reactive layer: a background result written straight into it stays off screen until something unrelated
  * happens to repaint. Pair the mutation with a `Signal` write, or call `TuiApp.requestRedraw()` from the same
  * render-thread callback that made it.
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
  * follows the shape `Layout.apply` already uses for its `Int | Double | Constraint` constraints. Normalising a plain
  * `String` into a [[Line]] happens per drawn row, in [[lineOf]], and only for the rows the viewport actually shows:
  * the DSL builds a fresh `ListView` value every frame, so converting the whole `items` sequence up front would put the
  * cost of the entire list — 50 000 items is 3 MiB of `Line`s — on every repaint to draw at most a screenful.
  *
  * `direction` picks which edge of the area the rows are anchored to; see [[ListDirection]]. It changes only where the
  * visible rows are painted — the selection clamping and the scroll offset are index arithmetic and are identical in
  * both directions.
  */
final case class ListView(
    items: Seq[String | Line],
    direction: ListDirection = ListDirection.TopToBottom,
    style: Style = Style.Default,
    highlightStyle: Style = Style.Default.reverse,
    highlightSymbol: String = "> ",
) extends StatefulWidget[ListState]:

  private def lineOf(item: String | Line): Line =
    item match
      case content: String => Line.raw(content)
      case line: Line      => line

  def render(area: Rect, buffer: Buffer, state: ListState): Unit =
    if !area.isEmpty && items.nonEmpty then
      val selected    = state.selected.map(index => math.max(0, math.min(index, items.size - 1)))
      state.selected = selected
      state.offset = ScrollWindow.offsetFor(state.offset, selected, items.size, area.height)
      val symbolWidth = CharWidth.of(highlightSymbol)
      val padding     = " ".repeat(symbolWidth)
      items.slice(state.offset, state.offset + area.height).map(lineOf).zipWithIndex.foreach { (line, row) =>
        val index      = state.offset + row
        val isSelected = selected.contains(index)
        val rowStyle   = if isSelected then style.patch(highlightStyle) else style
        val prefix     = if isSelected then highlightSymbol else padding
        // `row` counts visible rows away from the anchored edge, so only the edge changes between the two directions
        // and the scroll arithmetic above cannot drift apart from what is drawn. `area.bottom - 1 - row` never falls
        // above the area because the slice is capped at `area.height` rows.
        val y          = direction match
          case ListDirection.TopToBottom => area.y + row
          case ListDirection.BottomToTop => area.bottom - 1 - row
        // clip to the area, not just to the buffer: a highlight symbol wider than a narrow list would otherwise be
        // written straight over whatever owns the columns to the right
        buffer.setString(area.x, y, CharWidth.substringByWidth(prefix, area.width), rowStyle)
        val _          = LineRenderer.render(buffer, area.x + symbolWidth, y, line, area.width - symbolWidth, rowStyle)
      }
