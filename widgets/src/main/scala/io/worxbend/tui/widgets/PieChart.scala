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
      val legendWidth = if showLegend then data.map((label, _) => CharWidth.of(label)).maxOption.getOrElse(0) + 7 else 0
      // a legend wider than the area would otherwise push the disc — and the legend's own left edge — outside it
      val discWidth   = math.max(0, area.width - legendWidth)
      val radius      = math.min(discWidth / 2.0 / 2.0, area.height / 2.0) // width halved for cell aspect
      val centerX     = area.x + discWidth / 2.0
      val centerY     = area.y + area.height / 2.0
      // the running total after each sector, so a point's angle can be looked up by the first edge it falls before
      val cumulative  = data.scanLeft(0.0) { case (runningTotal, (_, value)) => runningTotal + math.max(0, value) }.tail
      var y           = area.y
      while y < area.bottom do
        var x = area.x
        while x < area.x + discWidth do
          val dx = (x - centerX) / 2.0 // undo the aspect correction
          val dy = y - centerY
          if math.sqrt(dx * dx + dy * dy) <= radius then
            val angle  = (math.atan2(dy, dx) + math.Pi) / (2 * math.Pi) // 0..1 around the disc
            val sector = cumulative.indexWhere(edge => angle * total <= edge)
            val index  = if sector < 0 then data.size - 1 else sector
            buffer.set(x, y, Cell("█", SeriesPalette.cycle(styles, index)))
          x += 1
        y += 1
      if showLegend then renderLegend(area, buffer, discWidth, total)

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
