package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, Style, Widget}

/** A filled pie: angular sectors proportional to each value, plus a legend when width allows.
  *
  * Cells are roughly half as tall as they are wide, so the disc corrects the aspect ratio to look circular.
  */
final case class PieChart(
    data: Seq[(String, Double)],
    styles: Seq[Style] = SeriesPalette.Default,
    showLegend: Boolean = true,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    val total = data.map((_, value) => value).filter(_ > 0).sum
    if !area.isEmpty && total > 0 then
      val legendWidth = if showLegend then LegendFit.width(data.map((label, _) => label), LegendPadding) else 0
      // a legend wider than the area would otherwise push the disc — and the legend's own left edge — outside it
      val discWidth   = math.max(0, area.width - legendWidth)
      // width is halved once for the radius and again for the cell aspect: a cell is roughly half as tall as it is
      // wide, so a column of cells covers twice the visual distance of a row of them
      val radius      = math.min(discWidth / 2.0 / CellAspect, area.height / 2.0)
      val centerX     = area.x + discWidth / 2.0
      val centerY     = area.y + area.height / 2.0
      // the running total after each sector, so a point's angle can be looked up by the first edge it falls before
      val cumulative  = data.scanLeft(0.0) { case (runningTotal, (_, value)) => runningTotal + math.max(0, value) }.tail
      var y           = area.y
      while y < area.bottom do
        var x = area.x
        while x < area.x + discWidth do
          val dx = (x - centerX) / CellAspect // undo the aspect correction the radius applied
          val dy = y - centerY
          if math.sqrt(dx * dx + dy * dy) <= radius then
            val angle  = (math.atan2(dy, dx) + math.Pi) / (2 * math.Pi) // 0..1 around the disc
            val sector = cumulative.indexWhere(edge => angle * total <= edge)
            val index  = if sector < 0 then data.size - 1 else sector
            buffer.set(x, y, Cell("█", SeriesPalette.cycle(styles, index)))
          x += 1
        y += 1
      if showLegend then renderLegend(area, buffer, discWidth, total)

  /** How many times wider than tall a terminal cell is, as a divisor: horizontal distances are halved so the disc reads
    * as a circle rather than an ellipse.
    */
  private val CellAspect = 2.0

  /** Columns a legend entry needs beyond its label: the `■ ` swatch and the widest ` 100%` suffix. */
  private val LegendPadding = 7

  private def renderLegend(area: Rect, buffer: Buffer, discWidth: Int, total: Double): Unit =
    data.take(area.height).zipWithIndex.foreach { case ((label, value), index) =>
      val percent = math.round(value / total * 100)
      val entry   = s"■ $label $percent%"
      val x       = area.x + discWidth + 1
      buffer.setString(
        x,
        area.y + index,
        CharWidth.substringByWidth(entry, area.right - x),
        SeriesPalette.cycle(styles, index),
      )
    }
