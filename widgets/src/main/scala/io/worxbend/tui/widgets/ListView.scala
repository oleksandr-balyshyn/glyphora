package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Line, Rect, StatefulWidget, Style}

/** Caller-owned list state: the selection, the scroll offset and how close to the edge the selection is allowed to get.
  * Mutable on purpose — the widget adjusts the offset during render to keep the selection visible, and the app mutates
  * the selection from key handlers (the `StatefulWidget` contract).
  *
  * `scrollPadding` is the number of further items kept visible on each side of the selection whenever the list is long
  * enough to show them. It defaults to `0`, which is the behaviour every 0.12.0 list already had: the highlight can
  * come to rest on the top or bottom visible row, with more items just out of sight. Set it to `2` and the list starts
  * scrolling under the highlight two rows before the highlight would reach the edge. See [[ScrollWindow.offsetFor]] for
  * the exact rule, including how it degrades at the two ends of the list.
  *
  * Render-thread-only, and mutating it does not by itself schedule a frame. This is a plain mutable object, invisible
  * to the reactive layer: a background result written straight into it stays off screen until something unrelated
  * happens to repaint. Pair the mutation with a `Signal` write, or call `TuiApp.requestRedraw()` from the same
  * render-thread callback that made it.
  */
final class ListState(var selected: Option[Int] = None, var offset: Int = 0, var scrollPadding: Int = 0):

  def selectNext(itemCount: Int): Unit =
    if itemCount > 0 then selected = Selection.next(selected, itemCount)

  def selectPrevious(itemCount: Int): Unit =
    if itemCount > 0 then selected = Selection.previous(selected, itemCount)

  /** Drops the selection *and* scrolls back to the top.
    *
    * Setting `selected = None` on its own leaves `offset` wherever the last selection had scrolled it, so a list the
    * app has just deselected — after deleting the highlighted row, or clearing a search — keeps showing whatever page
    * it happened to be on with nothing highlighted on it. "No selection" and "back to the top" are the same intent in
    * every case this library has seen, so this method does both; an app that genuinely wants one without the other can
    * still write the two fields directly.
    */
  def clearSelection(): Unit =
    selected = None
    offset = 0

  /** Selects the first item — the Home key's move. A no-op on an empty list. */
  def selectFirst(itemCount: Int): Unit =
    if itemCount > 0 then selected = Selection.first(itemCount)

  /** Selects the last item — the End key's move. A no-op on an empty list.
    *
    * This deliberately leaves `offset` alone. The widget re-derives the offset during render to bring the selection
    * back into view, so the list scrolls to the bottom on the next frame without this method having to guess at a
    * viewport height it cannot see from here.
    */
  def selectLast(itemCount: Int): Unit =
    if itemCount > 0 then selected = Selection.last(itemCount)

  /** Moves the selection `delta` items, clamped at both ends — the screenful jump PageUp and PageDown make. A negative
    * `delta` moves toward the first item. A no-op on an empty list.
    */
  def selectBy(itemCount: Int, delta: Int): Unit =
    if itemCount > 0 then selected = Selection.by(selected, itemCount, delta)

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
    highlightSpacing: HighlightSpacing = HighlightSpacing.Always,
    style: Style = Style.Default,
    highlightStyle: Style = Style.Default.reverse,
    highlightSymbol: String = "> ",
) extends StatefulWidget[ListState]:

  private def lineOf(item: String | Line): Line =
    item match
      case content: String => Line.raw(content)
      case line: Line      => line

  def render(area: Rect, buffer: Buffer, state: ListState): Unit =
    if !area.isEmpty && items.isEmpty then
      // a list that has been emptied — a filter matched nothing, the last row was deleted — used to keep its stale
      // selection and offset, which reappeared the moment items came back and pointed at whatever now sat at that
      // index. Nothing can be selected in an empty list, so repair the state rather than leaving it to the app.
      state.clearSelection()
    else if !area.isEmpty then
      val selected    = state.selected.map(index => math.max(0, math.min(index, items.size - 1)))
      state.selected = selected
      state.offset = ScrollWindow.offsetFor(state.offset, selected, items.size, area.height, state.scrollPadding)
      // how many columns the marker gutter takes off the text on *every* row. Nothing is drawn in it on an unselected
      // row, but the columns still have to be subtracted so the text of the selected row and the text of the others
      // start in the same place.
      val gutterWidth = highlightSpacing match
        case HighlightSpacing.Always       => CharWidth.of(highlightSymbol)
        case HighlightSpacing.WhenSelected => if selected.isDefined then CharWidth.of(highlightSymbol) else 0
        case HighlightSpacing.Never        => 0
      val padding     = " ".repeat(gutterWidth)
      items.slice(state.offset, state.offset + area.height).map(lineOf).zipWithIndex.foreach { (line, row) =>
        val index      = state.offset + row
        val isSelected = selected.contains(index)
        val rowStyle   = if isSelected then style.patch(highlightStyle) else style
        // with no gutter reserved there is no room for the marker either, so the highlight style is the only cue
        val prefix     = if isSelected && gutterWidth > 0 then highlightSymbol else padding
        // `row` counts visible rows away from the anchored edge, so only the edge changes between the two directions
        // and the scroll arithmetic above cannot drift apart from what is drawn. `area.bottom - 1 - row` never falls
        // above the area because the slice is capped at `area.height` rows.
        val y          = direction match
          case ListDirection.TopToBottom => area.y + row
          case ListDirection.BottomToTop => area.bottom - 1 - row
        // clip to the area, not just to the buffer: a highlight symbol wider than a narrow list would otherwise be
        // written straight over whatever owns the columns to the right
        buffer.setString(area.x, y, CharWidth.substringByWidth(prefix, area.width), rowStyle)
        val _          = LineRenderer.render(buffer, area.x + gutterWidth, y, line, area.width - gutterWidth, rowStyle)
      }
