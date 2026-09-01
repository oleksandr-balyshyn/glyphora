package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Style, Widget}

/** Vertical bars with optional labels underneath: each `(label, value)` gets a `barWidth`-column bar scaled against
  * `max` (defaulting to the data's maximum), topped with a partial block glyph for sub-cell precision.
  *
  * `barSet` swaps the glyphs the bars are drawn from — [[BarSet.Ascii]] for a terminal with no block elements,
  * [[BarSet.Solid]] or [[BarSet.Halves]] for blunter bars, or a set of your own. Its `empty` glyph, if it has one, also
  * fills the cells above each bar, which is how the chart gets a visible track behind every bar.
  */
final case class BarChart(
    data: Seq[(String, Long)],
    barWidth: Int = 3,
    barGap: Int = 1,
    max: Option[Long] = None,
    barStyle: Style = Style.Default,
    labelStyle: Style = Style.Default,
    barSet: BarSet = BarSet.Eighths,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    val showLabels  = ColumnChart.showLabels(area, data.map((label, _) => label))
    val chartHeight = ColumnChart.chartHeight(area, showLabels)
    if area.isEmpty || data.isEmpty || chartHeight <= 0 || barWidth <= 0 then ()
    else
      val ceiling = math.max(1L, max.getOrElse(data.map((_, value) => value).max))
      val stride  = ColumnChart.stride(barWidth, barGap)
      data.zipWithIndex.foreach { case ((label, value), index) =>
        val barLeft = ColumnChart.barLeftAt(area, index, stride)
        if ColumnChart.fits(area, barLeft, barWidth) then
          drawBar(buffer, area, barLeft, chartHeight, value, ceiling)
          if showLabels then drawLabel(buffer, area, barLeft, label)
      }

  private def drawBar(buffer: Buffer, area: Rect, barLeft: Int, chartHeight: Int, value: Long, ceiling: Long): Unit =
    BlockLadder.fillColumn(
      buffer,
      x = barLeft,
      columns = barWidth,
      bottom = area.y + chartHeight - 1,
      top = area.y,
      value = value,
      ceiling = ceiling,
      style = barStyle,
      set = barSet,
    )

  private def drawLabel(buffer: Buffer, area: Rect, barLeft: Int, label: String): Unit =
    ColumnChart.drawCentredLabel(buffer, area, barLeft, barWidth, label, labelStyle)
