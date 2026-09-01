package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Constraint, Direction, Flex, Layout, Line, Rect, Style, Widget}

/** Rows of cells laid out in columns sized by the core constraint solver.
  *
  * Each row is one terminal row (no cell wrapping); rows past the area's bottom edge are clipped, matching the
  * library-wide silent-clipping philosophy.
  *
  * @param widths
  *   one [[Constraint]] per column. An empty sequence means "equal columns": the table counts the cells in the header
  *   and in the rows it is about to draw, and gives each column an equal share of the area. That fallback exists
  *   because an empty sequence used to render a blank rectangle, and the DSL's `table(rows)` — whose widths are
  *   varargs, so writing none of them is a legal call — landed straight in it.
  * @param footer
  *   an optional summary row — a totals line, a "last updated" note — pinned to the *bottom* of the area rather than
  *   following the last data row. Pinning it is the point: a table whose rows do not fill the area would otherwise
  *   leave the totals floating in the middle of the pane. It is laid out on the same solved columns as the body, which
  *   is what a separate `Table` stacked underneath could never guarantee — the moment the widths changed, the two
  *   drifted apart. It costs one row of body height. On an area one row tall that already has a header there is no
  *   bottom row left to pin it to, so the header wins and the footer is dropped.
  * @param footerStyle
  *   the style the footer row is drawn in, bold by default, matching the header.
  * @param flex
  *   where the columns sit when they do not fill the area. Constraints like `Length(8)` can leave the table narrower
  *   than the space it was given; before this parameter the leftover always trailed off the right-hand side, because
  *   the widget passed no flex to the layout and took the [[Flex.Start]] default. Now a table of fixed-width columns
  *   can be centred or right-aligned in its area, the same way a `row` can. Has no effect when a `Fill` or `Min` column
  *   is already absorbing the leftover, because then there is none.
  */
final case class Table(
    rows: Seq[Seq[Line]],
    widths: Seq[Constraint],
    header: Option[Seq[Line]] = None,
    footer: Option[Seq[Line]] = None,
    columnSpacing: Int = 1,
    flex: Flex = Flex.Start,
    style: Style = Style.Default,
    headerStyle: Style = Style.Default.bold,
    footerStyle: Style = Style.Default.bold,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val headerRows  = if header.isDefined then 1 else 0
      // the footer is pinned to the bottom, so it is only drawn when there is a row down there that the header is not
      // already using: on a one-row area with a header, the header wins and the footer is dropped
      val footerRows  = if footer.isDefined && area.height > headerRows then 1 else 0
      // bounded by the area, not the data: drawing 50 visible rows must not walk a 10 000-row Seq
      val body        = rows.iterator.take(math.max(0, area.height - headerRows - footerRows)).toSeq
      // the fallback only walks the rows that are about to be drawn, which is why `body` is taken first
      val cellCounts  = (header.iterator ++ body.iterator ++ footer.iterator).map(_.size)
      val constraints = TableColumns.resolve(widths, cellCounts)
      val columns     = Layout(Direction.Horizontal, constraints, columnSpacing, flex).split(area)
      var y           = area.y
      header.foreach { cells =>
        if y < area.bottom then
          renderRow(buffer, columns, cells, y, headerStyle)
          y += 1
      }
      body.foreach { cells =>
        renderRow(buffer, columns, cells, y, style)
        y += 1
      }
      if footerRows == 1 then footer.foreach(cells => renderRow(buffer, columns, cells, area.bottom - 1, footerStyle))

  private def renderRow(buffer: Buffer, columns: Seq[Rect], cells: Seq[Line], y: Int, rowStyle: Style): Unit =
    columns.zip(cells).foreach { (column, cell) =>
      if !column.isEmpty then
        val _ = LineRenderer.render(buffer, column.x, y, cell, column.width, rowStyle)
    }

object Table:

  /** A table of plain unstyled text.
    *
    * The cells of a table are [[Line]]s so that any one of them can carry several styles, but the common case is a grid
    * of `String`s and every such caller was writing the same `rows.map(_.map(Line.raw))` incantation. This does that
    * once. A `Seq[Seq[String | Line]]` union would have removed the need for a second factory altogether, but a union
    * two collections deep is unpleasant to read and to infer, so the two shapes get two names instead.
    */
  def ofStrings(
      rows: Seq[Seq[String]],
      widths: Seq[Constraint],
      header: Option[Seq[String]] = None,
      footer: Option[Seq[String]] = None,
      columnSpacing: Int = 1,
      flex: Flex = Flex.Start,
      style: Style = Style.Default,
      headerStyle: Style = Style.Default.bold,
      footerStyle: Style = Style.Default.bold,
  ): Table =
    Table(
      rows.map(_.map(Line.raw)),
      widths,
      header.map(_.map(Line.raw)),
      footer.map(_.map(Line.raw)),
      columnSpacing,
      flex,
      style,
      headerStyle,
      footerStyle,
    )
