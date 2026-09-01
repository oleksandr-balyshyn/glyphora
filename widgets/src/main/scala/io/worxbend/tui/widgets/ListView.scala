package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Line, Measured, Rect, StatefulWidget, Style, Text}

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
  * An item is a plain `String`, a multi-style [[Line]], or a whole [[Text]], and the three may be mixed in one list.
  * The union type saves every caller with plain text the `items.map(Line.raw)` ceremony without closing the door on a
  * styled row; it follows the shape `Layout.apply` already uses for its `Int | Double | Constraint` constraints.
  * Normalising a plain `String` into a [[Line]] happens per drawn row, in [[linesOf]], and only for the rows the
  * viewport actually shows: the DSL builds a fresh `ListView` value every frame, so converting the whole `items`
  * sequence up front would put the cost of the entire list — 50 000 items is 3 MiB of `Line`s — on every repaint to
  * draw at most a screenful.
  *
  * A [[Text]] item occupies one row per line it holds, which is how a row says a title on one line and a dimmed
  * subtitle under it. Selection, the keyboard moves and the scroll offset all still count *items*, not rows, so one
  * press of Down moves past the whole block; the offset arithmetic that keeps the selection on screen is
  * [[ScrollWindow.offsetForItems]]. A `Text` with no lines at all still takes one blank row, so an item can never
  * become invisible and therefore unreachable. The last item on screen may be cut off part way through, which is what
  * lets a block taller than the whole viewport still show its top.
  *
  * `repeatHighlightSymbol` decides whether the marker is drawn on every row of a selected multi-row item or only on its
  * first. The default draws it once, so the marker reads as pointing at the item; repeating it reads as marking each
  * row, which suits an item whose rows are a list of their own.
  *
  * `direction` picks which edge of the area the rows are anchored to; see [[ListDirection]]. It changes only where the
  * visible rows are painted — the selection clamping and the scroll offset are index arithmetic and are identical in
  * both directions.
  */
final case class ListView(
    items: Seq[String | Line | Text],
    direction: ListDirection = ListDirection.TopToBottom,
    highlightSpacing: HighlightSpacing = HighlightSpacing.Always,
    style: Style = Style.Default,
    highlightStyle: Style = Style.Default.reverse,
    highlightSymbol: String = "> ",
    // Appended rather than placed in the layout-and-behaviour slot: inserting a parameter mid-list would silently
    // change what every positional caller written against an earlier release means.
    repeatHighlightSymbol: Boolean = false,
) extends StatefulWidget[ListState]
    with Measured:

  /** The rows `item` is drawn as. A `String` and a `Line` are one row each; a `Text` is one row per line it holds, and
    * an empty `Text` is one blank row rather than none — an item that drew nothing could still be selected, and a
    * selection the reader cannot see is worse than a blank row.
    */
  private def linesOf(item: String | Line | Text): Seq[Line] =
    item match
      case content: String => Seq(Line.raw(content))
      case line: Line      => Seq(line)
      case text: Text      => if text.lines.isEmpty then Seq(Line.raw("")) else text.lines

  /** How many rows `item` occupies, without building any of them — the counterpart of [[linesOf]] used by the scroll
    * arithmetic, which needs every item's height and draws none of them.
    */
  private def heightOf(item: String | Line | Text): Int =
    item match
      case text: Text => math.max(1, text.lines.size)
      case _          => 1

  def render(area: Rect, buffer: Buffer, state: ListState): Unit =
    if !area.isEmpty && items.isEmpty then
      // a list that has been emptied — a filter matched nothing, the last row was deleted — used to keep its stale
      // selection and offset, which reappeared the moment items came back and pointed at whatever now sat at that
      // index. Nothing can be selected in an empty list, so repair the state rather than leaving it to the app.
      state.clearSelection()
    else if !area.isEmpty then
      val selected    = state.selected.map(index => math.max(0, math.min(index, items.size - 1)))
      state.selected = selected
      // Uniform lists take the padding-aware rule they always did; a list with a multi-row item takes the row-counting
      // one, which has no padding to offer because "two more items" is not a fixed number of rows there.
      val heights     = items.map(heightOf)
      state.offset =
        if heights.forall(_ == 1) then
          ScrollWindow.offsetFor(state.offset, selected, items.size, area.height, state.scrollPadding)
        else ScrollWindow.offsetForItems(state.offset, selected, heights, area.height)
      // how many columns the marker gutter takes off the text on *every* row. Nothing is drawn in it on an unselected
      // row, but the columns still have to be subtracted so the text of the selected row and the text of the others
      // start in the same place.
      val gutterWidth = highlightSpacing match
        case HighlightSpacing.Always       => CharWidth.of(highlightSymbol)
        case HighlightSpacing.WhenSelected => if selected.isDefined then CharWidth.of(highlightSymbol) else 0
        case HighlightSpacing.Never        => 0
      val padding     = " ".repeat(gutterWidth)
      // One counter of visible rows across all the items drawn, so a multi-row item pushes the next item down by its
      // own height rather than by one. Rows past the area are never reached, which is what cuts the last item short.
      var row         = 0
      items.iterator.drop(state.offset).zipWithIndex.takeWhile(_ => row < area.height).foreach { (item, ordinal) =>
        val index      = state.offset + ordinal
        val isSelected = selected.contains(index)
        val rowStyle   = if isSelected then style.patch(highlightStyle) else style
        linesOf(item).iterator.zipWithIndex.takeWhile(_ => row < area.height).foreach { (line, lineIndex) =>
          // with no gutter reserved there is no room for the marker either, so the highlight style is the only cue.
          // The marker belongs to the item, so on a multi-row item it is drawn once, at the top, unless the caller
          // asked for it on every row.
          val marks  = isSelected && gutterWidth > 0 && (lineIndex == 0 || repeatHighlightSymbol)
          val prefix = if marks then highlightSymbol else padding
          // `row` counts visible rows away from the anchored edge, so only the edge changes between the two directions
          // and the scroll arithmetic above cannot drift apart from what is drawn. `area.bottom - 1 - row` never falls
          // above the area because the walk stops at `area.height` rows.
          val y      = direction match
            case ListDirection.TopToBottom => area.y + row
            case ListDirection.BottomToTop => area.bottom - 1 - row
          // clip to the area, not just to the buffer: a highlight symbol wider than a narrow list would otherwise be
          // written straight over whatever owns the columns to the right
          buffer.setString(area.x, y, CharWidth.substringByWidth(prefix, area.width), rowStyle)
          val _      = LineRenderer.render(buffer, area.x + gutterWidth, y, line, area.width - gutterWidth, rowStyle)
          row += 1
        }
      }

  /** The rows every item together occupies — the sum of the items' own heights, which is one row each until a [[Text]]
    * item brings more. Always an answer: a list knows its items without a layout pass. The `width` is ignored because a
    * list clips rather than wraps, so no width changes how many rows an item takes.
    */
  override def heightAt(width: Int): Option[Int] = Some(items.map(heightOf).sum)
