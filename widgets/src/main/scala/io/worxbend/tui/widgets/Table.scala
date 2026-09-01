package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Constraint, Direction, Layout, Line, Rect, Style, Widget}

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
  */
final case class Table(
    rows: Seq[Seq[Line]],
    widths: Seq[Constraint],
    header: Option[Seq[Line]] = None,
    columnSpacing: Int = 1,
    style: Style = Style.Default,
    headerStyle: Style = Style.Default.bold,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      // bounded by the area, not the data: drawing 50 visible rows must not walk a 10 000-row Seq
      val headerRows  = if header.isDefined then 1 else 0
      val body        = rows.iterator.take(math.max(0, area.height - headerRows)).toSeq
      // the fallback only walks the rows that are about to be drawn, which is why `body` is taken first
      val constraints = TableColumns.resolve(widths, (header.iterator ++ body.iterator).map(_.size))
      val columns     = Layout(Direction.Horizontal, constraints, columnSpacing).split(area)
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
      columnSpacing: Int = 1,
      style: Style = Style.Default,
      headerStyle: Style = Style.Default.bold,
  ): Table =
    Table(
      rows.map(_.map(Line.raw)),
      widths,
      header.map(_.map(Line.raw)),
      columnSpacing,
      style,
      headerStyle,
    )
