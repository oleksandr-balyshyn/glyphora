package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Constraint, Direction, Layout, Line, Rect, Style, Widget}

/** Rows of cells laid out in columns sized by the core constraint solver.
  *
  * Each row is one terminal row (no cell wrapping); rows past the area's bottom edge are clipped, matching the
  * library-wide silent-clipping philosophy.
  */
final case class Table(
    rows: Seq[Seq[Line]],
    widths: Seq[Constraint],
    header: Option[Seq[Line]] = None,
    columnSpacing: Int = 1,
    headerStyle: Style = Style.Default.bold,
    style: Style = Style.Default,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val columns = Layout(Direction.Horizontal, widths, columnSpacing).split(area)
      var y = area.y
      header.foreach { cells =>
        if y < area.bottom then
          renderRow(buffer, columns, cells, y, headerStyle)
          y += 1
      }
      rows.foreach { cells =>
        if y < area.bottom then
          renderRow(buffer, columns, cells, y, style)
          y += 1
      }

  private def renderRow(buffer: Buffer, columns: Seq[Rect], cells: Seq[Line], y: Int, rowStyle: Style): Unit =
    columns.zip(cells).foreach { (column, cell) =>
      if !column.isEmpty then
        val _ = LineRenderer.render(buffer, column.x, y, cell, column.width, rowStyle)
    }
