package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Rect, Style, Widget}

/** Bars stacked from multiple series: each `(label, values)` column stacks one segment per series, scaled against the
  * tallest stack.
  */
final case class StackedBarChart(
    data: Seq[(String, Seq[Long])],
    barWidth: Int = 3,
    barGap: Int = 1,
    styles: Seq[Style] = SeriesPalette.Default,
    labelStyle: Style = Style.Default,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    val showLabels  = ColumnChart.showLabels(area, data.map((label, _) => label))
    val chartHeight = ColumnChart.chartHeight(area, showLabels)
    val maxTotal    = data.map((_, values) => values.map(math.max(0L, _)).sum).maxOption.getOrElse(0L)
    if area.isEmpty || data.isEmpty || chartHeight <= 0 || maxTotal <= 0 then ()
    else
      val stride = ColumnChart.stride(barWidth, barGap)
      data.zipWithIndex.foreach { case ((label, values), barIndex) =>
        val barLeft = ColumnChart.barLeftAt(area, barIndex, stride)
        if ColumnChart.fits(area, barLeft, barWidth) then
          drawStack(buffer, area, barLeft, chartHeight, values, maxTotal)
          if showLabels then ColumnChart.drawCentredLabel(buffer, area, barLeft, barWidth, label, labelStyle)
      }

  /** Paints one column's segments bottom-up, each series scaled against the tallest stack in the chart.
    *
    * The segments are stacked by carrying the previous segment's top down as the next one's bottom, rather than by
    * summing offsets, so the rounding of one segment cannot leave a one-row gap between it and the next.
    */
  private def drawStack(
      buffer: Buffer,
      area: Rect,
      barLeft: Int,
      chartHeight: Int,
      values: Seq[Long],
      maxTotal: Long,
  ): Unit =
    var bottom = area.y + chartHeight
    values.zipWithIndex.foreach { (value, series) =>
      val cells = math.round(math.max(0L, value).toDouble / maxTotal * chartHeight).toInt
      // each segment rounds up independently, so a tall stack can ask for more rows than the chart has
      val top   = math.max(area.y, bottom - cells)
      var y     = top
      while y < bottom do
        var x = barLeft
        while x < barLeft + barWidth do
          buffer.set(x, y, Cell("█", SeriesPalette.cycle(styles, series)))
          x += 1
        y += 1
      bottom = top
    }
