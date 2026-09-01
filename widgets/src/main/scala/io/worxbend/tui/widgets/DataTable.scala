package io.worxbend.tui.widgets

import io.worxbend.tui.core.{
  Buffer,
  CharWidth,
  Constraint,
  Direction,
  Flex,
  Layout,
  Line,
  Rect,
  Span,
  StatefulWidget,
  Style,
}

/** Which way a [[DataTable]] column is sorted. */
enum SortDirection:
  case Ascending, Descending

  /** The other direction — what sorting an already-sorted column again does. */
  def flipped: SortDirection = this match
    case Ascending  => Descending
    case Descending => Ascending

/** A column index paired with the direction it is sorted in.
  *
  * The two travel together because neither means anything alone: a direction with no column says nothing at all, and
  * before this pairing existed an unsorted table still carried an `ascending` flag that could be flipped — changing the
  * memoization key, and so recomputing an identical view.
  */
final case class ColumnSort(column: Int, direction: SortDirection)

/** A page window over a [[DataTable]]: how many rows a page holds, and which page is showing.
  *
  * Also a pair for a reason: a page number with no page size describes nothing the widget can render, which is what a
  * separate `pageSize: Option[Int]` plus `page: Int` allowed anyone to write.
  */
final case class Paging(size: Int, page: Int)

/** Caller-owned [[DataTable]] state: the sort (if any), a substring filter, selection, scroll, and paging (if any).
  *
  * Selection indexes into the *view* (the filtered, sorted rows) — use [[DataTable.visibleRows]] to map it back to
  * data.
  *
  * Render-thread-only, and mutating it does not by itself schedule a frame. This is a plain mutable object, invisible
  * to the reactive layer: a background result written straight into it stays off screen until something unrelated
  * happens to repaint. Pair the mutation with a `Signal` write, or call `TuiApp.requestRedraw()` from the same
  * render-thread callback that made it.
  */
final class DataTableState:
  var sort: Option[ColumnSort] = None
  var selected: Option[Int]    = None
  var offset: Int              = 0
  var paging: Option[Paging]   = None

  /** The selected column, or `None` for no column cursor — the horizontal half of a spreadsheet-style cursor.
    *
    * Independent of [[selected]] on purpose. A column selection with no row selection highlights a whole column, a row
    * selection with no column selection highlights a whole row (which is all a `DataTable` could do before), and the
    * two together identify one cell. Nothing is drawn for it unless the widget was given a `columnHighlightStyle` or a
    * `cellHighlightStyle`, so a table that does not want a column cursor is unaffected by its existence.
    */
  var selectedColumn: Option[Int] = None

  /** The substring rows are filtered by, or `""` for no filter. Read-only: change it through [[setFilter]], which is
    * the only place that also resets the selection and the scroll to match the new result set.
    */
  def filter: String = filterText

  private var filterText: String = ""

  /** Moves to the next/previous page (no-ops while `paging` is unset, the one state where no-op is the honest answer);
    * `totalFiltered` bounds the last page.
    */
  def nextPage(totalFiltered: Int): Unit =
    paging.foreach { window =>
      val lastPage = math.max(0, (totalFiltered - 1) / math.max(1, window.size))
      paging = Some(window.copy(page = math.min(window.page + 1, lastPage)))
      selected = None
      offset = 0
    }

  def previousPage(): Unit =
    paging.foreach { window =>
      paging = Some(window.copy(page = math.max(0, window.page - 1)))
      selected = None
      offset = 0
    }

  /** Sorts by `column`; sorting the same column again flips the direction. */
  def sortBy(column: Int): Unit =
    sort = sort match
      case Some(current) if current.column == column => Some(current.copy(direction = current.direction.flipped))
      case _                                         => Some(ColumnSort(column, SortDirection.Ascending))

  /** Drops the memoized filtered/sorted view.
    *
    * Only needed when the row data changes without changing its length — the cache key cannot see through a `Seq` to
    * its contents, so `DataTable(columns, updatedRows, widths)` with the same row count would otherwise keep showing
    * the previous ordering.
    */
  def invalidate(): Unit = view = None

  private var view: Option[(DataTableState.ViewKey, Seq[Seq[String]])] = None

  /** Returns the cached view when `key` still matches, otherwise recomputes and stores it. */
  private[widgets] def cachedView(key: DataTableState.ViewKey)(compute: => Seq[Seq[String]]): Seq[Seq[String]] =
    view match
      case Some((cached, rows)) if cached == key => rows
      case _                                     =>
        val fresh = compute
        view = Some((key, fresh))
        fresh

  /** Filters the rows to those with `text` in any cell, and clears the selection and the scroll offset.
    *
    * Clearing both is the point of routing every filter change through here. A selection is an index into the *view*,
    * so keeping it across a filter change lands the highlight on whatever unrelated row now happens to sit at that
    * index, and keeping the offset scrolls a short result set to a position that no longer exists.
    */
  def setFilter(text: String): Unit =
    filterText = text
    selected = None
    offset = 0

  def selectNext(visibleCount: Int): Unit =
    if visibleCount > 0 then selected = Selection.next(selected, visibleCount)

  def selectPrevious(visibleCount: Int): Unit =
    if visibleCount > 0 then selected = Selection.previous(selected, visibleCount)

  /** Moves the column cursor right/left, clamping at the ends the same way row selection does — see [[Selection]] for
    * why these clamp rather than wrap. With no column selected yet, the first move lands on the first column.
    */
  def selectNextColumn(columnCount: Int): Unit =
    if columnCount > 0 then selectedColumn = Selection.next(selectedColumn, columnCount)

  def selectPreviousColumn(columnCount: Int): Unit =
    if columnCount > 0 then selectedColumn = Selection.previous(selectedColumn, columnCount)

  /** Puts the cursor on one cell: row `row` of the current view, column `column`.
    *
    * The pair is set together because a caller placing a cell cursor — a mouse click, a "jump to this field" command —
    * wants both halves to move at once, and setting them one at a time draws an intermediate frame with the cursor on a
    * cell nobody asked for. Negative indices clamp to zero; an index past the end is left for [[DataTable.render]] to
    * clamp, because only the render knows how many rows survived the filter.
    */
  def selectCell(row: Int, column: Int): Unit =
    selected = Some(math.max(0, row))
    selectedColumn = Some(math.max(0, column))

object DataTableState:
  /** Everything that can change the filtered/sorted view, used as the memoization key. */
  private[widgets] final case class ViewKey(
      sort: Option[ColumnSort],
      filter: String,
      rowCount: Int,
  )

/** A sortable, filterable table with a selectable, scrollable body — [[Table]] plus the interaction a data grid needs.
  *
  * The header shows a `▲`/`▼` indicator on the sorted column; the filter keeps rows where *any* cell contains the text
  * (case-insensitive); sorting compares numerically when both cells parse as numbers, else as text.
  *
  * @param columnHighlightStyle
  *   layered over the row's style for every body cell in the selected column, or `None` — the default — to draw no
  *   column cursor at all. Together with `cellHighlightStyle` this is what turns a row-selecting table into a
  *   spreadsheet-style grid cursor; a table that sets neither renders exactly as it did before they existed.
  * @param cellHighlightStyle
  *   layered on last, over the cell where the selected row and the selected column meet. Painting it after both means
  *   the intersection can be told apart from the row and the column that cross there, which is the whole point of
  *   having three styles rather than one.
  * @param highlightSymbol
  *   text drawn to the left of the selected row, in a gutter reserved for it on *every* row so the columns do not jump
  *   as the selection moves. `highlightStyle` alone marks the selection by reversing the row's colours, which two kinds
  *   of terminal do not show: one that ignores reverse video, and one where the row already carries a background colour
  *   of its own that the reversal blends into. A symbol survives both. The default is `""` — an empty symbol reserves a
  *   zero-width gutter, so a table written before this parameter existed draws exactly the same cells in exactly the
  *   same columns as before. [[ListView]] defaults to `"> "` instead, because a list has no column grid to keep still.
  * @param footer
  *   an optional summary row — totals, a record count — pinned to the *bottom* of the area rather than following the
  *   last data row, and laid out on the same solved columns as the body. It costs one row of the scrollable body. A
  *   `DataTable` always draws its header, so the footer is dropped on an area only one row tall, where the header has
  *   already taken the only row there is.
  * @param footerStyle
  *   the style the footer row is drawn in, bold by default, matching the header.
  * @param widths
  *   one [[Constraint]] per column. An empty sequence means "equal columns": each of the `columns` titles gets an equal
  *   share of the area. Before that fallback existed an empty sequence drew a blank rectangle instead.
  * @param flex
  *   where the columns sit when they do not fill the area — see [[Table]] for the full explanation. The reserved
  *   `highlightSymbol` gutter is taken off the left first, so the flex distributes only what is left over after it.
  */
final case class DataTable(
    columns: Seq[String],
    rows: Seq[Seq[String]],
    widths: Seq[Constraint],
    footer: Option[Seq[String]] = None,
    columnSpacing: Int = 1,
    flex: Flex = Flex.Start,
    style: Style = Style.Default,
    headerStyle: Style = Style.Default.bold,
    footerStyle: Style = Style.Default.bold,
    highlightStyle: Style = Style.Default.reverse,
    columnHighlightStyle: Option[Style] = None,
    cellHighlightStyle: Option[Style] = None,
    highlightSymbol: String = "",
) extends StatefulWidget[DataTableState]:

  /** Every row surviving the filter, in sort order — the domain paging windows over.
    *
    * Memoized on `state`: scrolling changes only the offset, and re-sorting ten thousand rows on every frame is what
    * pushes a redraw past the tick budget. The cache key covers everything that can change the result, with the row
    * count standing in for the data itself — see [[DataTableState.invalidate]] for when that is not enough.
    */
  def filteredRows(state: DataTableState): Seq[Seq[String]] =
    val key = DataTableState.ViewKey(state.sort, state.filter, rows.size)
    state.cachedView(key) {
      val filtered =
        if state.filter.isEmpty then rows
        else
          val needle = state.filter.toLowerCase
          rows.filter(_.exists(_.toLowerCase.contains(needle)))
      state.sort match
        case None                                => filtered
        case Some(ColumnSort(column, direction)) =>
          val cells   = filtered.map(row => row.lift(column).getOrElse(""))
          val ordered = ordering(cells)
          val sorted  =
            filtered.sortWith((a, b) => ordered.lt(a.lift(column).getOrElse(""), b.lift(column).getOrElse("")))
          direction match
            case SortDirection.Ascending  => sorted
            case SortDirection.Descending => sorted.reverse
    }

  /** The rows the widget is currently showing: filtered, sorted, and windowed to the current page — what a selection
    * indexes.
    */
  def visibleRows(state: DataTableState): Seq[Seq[String]] =
    val all = filteredRows(state)
    state.paging match
      case None         => all
      case Some(window) =>
        val size = pageSizeOf(window)
        val page = pageOf(window, all.size)
        all.slice(page * size, (page + 1) * size)

  /** Writes back the page [[visibleRows]] would show, so a page left past the end of a shrunken result set does not
    * stay there once the user turns it.
    *
    * The one write [[visibleRows]] used to make itself, moved out so that reading the rows stays a read. [[render]]
    * calls it on every frame alongside the selection and offset clamps, which is the moment all three state repairs
    * belong at.
    */
  private[widgets] def clampPage(state: DataTableState): Unit =
    val total = filteredRows(state).size
    state.paging = state.paging.map(window => window.copy(page = pageOf(window, total)))

  /** One page size for every use: `Paging(0, …)` arises naturally from `area.height - 2` on a short terminal, and
    * paging by 0 shows no rows at all on every page.
    */
  private def pageSizeOf(window: Paging): Int = math.max(1, window.size)

  private def pageOf(window: Paging, total: Int): Int =
    val lastPage = math.max(0, (total - 1) / pageSizeOf(window))
    math.max(0, math.min(window.page, lastPage))

  def render(area: Rect, buffer: Buffer, state: DataTableState): Unit =
    if !area.isEmpty then
      clampPage(state)
      val view        = visibleRows(state)
      // the gutter is carved off the left of the whole table, header included, so every column keeps one x position
      val symbolWidth = math.min(CharWidth.of(highlightSymbol), area.width)
      val grid        = area.copy(x = area.x + symbolWidth, width = area.width - symbolWidth)
      // an empty `widths` means equal columns; a DataTable always names its columns, so the header settles the count
      val constraints = TableColumns.resolve(widths, Iterator(columns.size))
      val segments    = Layout(Direction.Horizontal, constraints, columnSpacing, flex).split(grid)
      renderHeader(buffer, segments, state)
      val footerRows  = if footer.isDefined && area.height > 1 then 1 else 0
      val bodyHeight  = area.height - 1 - footerRows
      if footerRows == 1 then
        footer.foreach(cells => renderRow(buffer, segments, cells, area.bottom - 1, _ => footerStyle))
      if bodyHeight > 0 && view.nonEmpty then
        val selected = state.selected.map(index => math.max(0, math.min(index, view.size - 1)))
        state.selected = selected
        state.offset = ScrollWindow.offsetFor(state.offset, selected, view.size, bodyHeight)
        val padding  = " ".repeat(symbolWidth)
        // a column index past the last column would highlight nothing and hide the fact that it was set wrong
        val cursor   = state.selectedColumn.map(index => math.max(0, math.min(index, segments.size - 1)))
        state.selectedColumn = cursor
        view.slice(state.offset, state.offset + bodyHeight).zipWithIndex.foreach { (cells, row) =>
          val index      = state.offset + row
          val isSelected = selected.contains(index)
          val rowStyle   = if isSelected then style.patch(highlightStyle) else style
          val y          = area.y + 1 + row
          if symbolWidth > 0 then
            val prefix = if isSelected then highlightSymbol else padding
            buffer.setString(area.x, y, CharWidth.substringByWidth(prefix, symbolWidth), rowStyle)
          renderRow(buffer, segments, cells, y, cellStyle(rowStyle, isSelected, cursor, _))
        }

  private def renderHeader(buffer: Buffer, segments: Seq[Rect], state: DataTableState): Unit =
    columns.zipWithIndex.foreach { (title, index) =>
      segments.lift(index).filterNot(_.isEmpty).foreach { segment =>
        val indicator = state.sort match
          case Some(ColumnSort(`index`, SortDirection.Ascending))  => " ▲"
          case Some(ColumnSort(`index`, SortDirection.Descending)) => " ▼"
          case _                                                   => ""
        val line      = Line(Seq(Span(title + indicator, headerStyle)))
        val _         = LineRenderer.render(buffer, segment.x, segment.y, line, segment.width)
      }
    }

  /** The style one body cell is drawn in: the row's style, then the column cursor over it, then the cell cursor over
    * both. Layering in that order is what lets the intersection of the selected row and the selected column look like
    * neither of them.
    */
  private def cellStyle(rowStyle: Style, isSelectedRow: Boolean, cursor: Option[Int], column: Int): Style =
    if !cursor.contains(column) then rowStyle
    else
      val withColumn = columnHighlightStyle.fold(rowStyle)(rowStyle.patch)
      if isSelectedRow then cellHighlightStyle.fold(withColumn)(withColumn.patch) else withColumn

  /** Draws one row's cells, asking `styleAt` for the style of each column so that a per-cell cursor can differ from the
    * row it sits on. Rows with no cursor pass a function that ignores the column.
    */
  private def renderRow(
      buffer: Buffer,
      segments: Seq[Rect],
      cells: Seq[String],
      y: Int,
      styleAt: Int => Style,
  ): Unit =
    segments.zip(cells).zipWithIndex.foreach { case ((segment, cell), column) =>
      if !segment.isEmpty then
        val _ = LineRenderer.render(buffer, segment.x, y, Line.styled(cell, styleAt(column)), segment.width)
    }

  /** Numeric-aware ordering, chosen once for the whole column rather than per comparison.
    *
    * Deciding per pair is not a valid ordering and is not merely untidy: in a column mixing `"9"`, `"10"` and
    * `"2020-01-01"`, `"9" < "10"` numerically while `"10" < "2020-01-01"` and `"2020-01-01" < "9"` textually — a cycle,
    * which makes `sortWith` throw `IllegalArgumentException: Comparison method violates its general contract!` out of
    * the render loop, and silently mis-order the rows when it does not. `NaN` is excluded from the numeric case for the
    * same reason: it compares false against everything, breaking transitivity just as badly.
    */
  private def ordering(cells: Seq[String]): Ordering[String] =
    val numeric = cells.forall(cell => cell.toDoubleOption.exists(value => !value.isNaN))
    if numeric then Ordering.by[String, Double](_.toDoubleOption.getOrElse(0.0))
    else (left, right) => left.compareToIgnoreCase(right)
