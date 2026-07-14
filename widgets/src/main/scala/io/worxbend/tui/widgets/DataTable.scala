package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Constraint, Direction, Layout, Line, Rect, Span, StatefulWidget, Style}

/** Caller-owned [[DataTable]] state: sort column/direction, a substring filter, selection, and scroll.
  *
  * Selection indexes into the *view* (the filtered, sorted rows) — use [[DataTable.visibleRows]] to map it back to
  * data.
  */
final class DataTableState:
  var sortColumn: Option[Int] = None
  var sortAscending: Boolean  = true
  var filter: String          = ""
  var selected: Option[Int]   = None
  var offset: Int             = 0
  var pageSize: Option[Int]   = None
  var page: Int               = 0

  /** Moves to the next/previous page (no-ops without a `pageSize`); `totalFiltered` bounds the last page. */
  def nextPage(totalFiltered: Int): Unit =
    pageSize.foreach { size =>
      val lastPage = math.max(0, (totalFiltered - 1) / math.max(1, size))
      page = math.min(page + 1, lastPage)
      selected = None
      offset = 0
    }

  def previousPage(): Unit =
    if pageSize.nonEmpty then
      page = math.max(0, page - 1)
      selected = None
      offset = 0

  /** Sorts by `column`; sorting the same column again flips the direction. */
  def sortBy(column: Int): Unit =
    if sortColumn.contains(column) then sortAscending = !sortAscending
    else
      sortColumn = Some(column)
      sortAscending = true

  def setFilter(text: String): Unit =
    filter = text
    selected = None
    offset = 0

  def selectNext(visibleCount: Int): Unit =
    if visibleCount > 0 then selected = Some(selected.fold(0)(index => math.min(index + 1, visibleCount - 1)))

  def selectPrevious(visibleCount: Int): Unit =
    if visibleCount > 0 then selected = Some(selected.fold(0)(index => math.max(index - 1, 0)))

/** A sortable, filterable table with a selectable, scrollable body (the Tier 5 upgrade over [[Table]]).
  *
  * The header shows a `▲`/`▼` indicator on the sorted column; the filter keeps rows where *any* cell contains the text
  * (case-insensitive); sorting compares numerically when both cells parse as numbers, else as text.
  */
final case class DataTable(
    columns: Seq[String],
    rows: Seq[Seq[String]],
    widths: Seq[Constraint],
    columnSpacing: Int = 1,
    headerStyle: Style = Style.Default.bold,
    style: Style = Style.Default,
    highlightStyle: Style = Style.Default.reverse,
) extends StatefulWidget[DataTableState]:

  /** Every row surviving the filter, in sort order — the domain paging windows over. */
  def filteredRows(state: DataTableState): Seq[Seq[String]] =
    val filtered =
      if state.filter.isEmpty then rows
      else
        val needle = state.filter.toLowerCase
        rows.filter(_.exists(_.toLowerCase.contains(needle)))
    state.sortColumn match
      case None         => filtered
      case Some(column) =>
        val sorted = filtered.sortWith((a, b) => cellLess(a.lift(column), b.lift(column)))
        if state.sortAscending then sorted else sorted.reverse

  /** The rows the widget is currently showing: filtered, sorted, and windowed to the current page — what a selection
    * indexes.
    */
  def visibleRows(state: DataTableState): Seq[Seq[String]] =
    val all = filteredRows(state)
    state.pageSize match
      case None       => all
      case Some(size) =>
        val lastPage = math.max(0, (all.size - 1) / math.max(1, size))
        state.page = math.min(state.page, lastPage)
        all.slice(state.page * size, (state.page + 1) * size)

  def render(area: Rect, buffer: Buffer, state: DataTableState): Unit =
    if !area.isEmpty then
      val view       = visibleRows(state)
      val segments   = Layout(Direction.Horizontal, widths, columnSpacing).split(area)
      renderHeader(buffer, segments, state)
      val bodyHeight = area.height - 1
      if bodyHeight > 0 && view.nonEmpty then
        val selected = state.selected.map(index => math.max(0, math.min(index, view.size - 1)))
        state.selected = selected
        state.offset = scrolledOffset(state.offset, selected, view.size, bodyHeight)
        view.slice(state.offset, state.offset + bodyHeight).zipWithIndex.foreach { (cells, row) =>
          val index    = state.offset + row
          val rowStyle = if selected.contains(index) then style.patch(highlightStyle) else style
          renderRow(buffer, segments, cells, area.y + 1 + row, rowStyle)
        }

  private def renderHeader(buffer: Buffer, segments: Seq[Rect], state: DataTableState): Unit =
    columns.zipWithIndex.foreach { (title, index) =>
      segments.lift(index).filterNot(_.isEmpty).foreach { segment =>
        val indicator =
          if state.sortColumn.contains(index) then if state.sortAscending then " ▲" else " ▼"
          else ""
        val line      = Line(Seq(Span(title + indicator, headerStyle)))
        val _         = LineRenderer.render(buffer, segment.x, segment.y, line, segment.width)
      }
    }

  private def renderRow(buffer: Buffer, segments: Seq[Rect], cells: Seq[String], y: Int, rowStyle: Style): Unit =
    segments.zip(cells).foreach { (segment, cell) =>
      if !segment.isEmpty then
        val _ = LineRenderer.render(buffer, segment.x, y, Line.styled(cell, rowStyle), segment.width)
    }

  /** Numeric-aware ordering: numbers compare as numbers, everything else as case-insensitive text. */
  private def cellLess(a: Option[String], b: Option[String]): Boolean =
    val left  = a.getOrElse("")
    val right = b.getOrElse("")
    (left.toDoubleOption, right.toDoubleOption) match
      case (Some(x), Some(y)) => x < y
      case _                  => left.compareToIgnoreCase(right) < 0

  private def scrolledOffset(offset: Int, selected: Option[Int], total: Int, height: Int): Int =
    val maxOffset = math.max(0, total - height)
    val clamped   = math.max(0, math.min(offset, maxOffset))
    selected match
      case None        => clamped
      case Some(index) =>
        if index < clamped then index
        else if index >= clamped + height then index - height + 1
        else clamped
