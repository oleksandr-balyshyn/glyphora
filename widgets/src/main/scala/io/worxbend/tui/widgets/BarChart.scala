package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Direction, Rect, Style, Widget}

/** Bars with labels, one per `(label, value)`, scaled against `max` (defaulting to the data's maximum) and topped with
  * a partial block glyph for sub-cell precision.
  *
  * `direction` picks the layout. `Vertical` — the default, and what this widget has always done — draws upright bars
  * `barWidth` columns wide with their labels on the row underneath, so each name has only as many columns as its bar.
  * `Horizontal` draws the bars rightwards, `barHeight` rows tall, and reserves a gutter down the left edge for the
  * labels: that is the layout to reach for when the category names are long, because a name in the gutter has a whole
  * strip of columns to itself instead of being truncated to a bar's width.
  *
  * @param direction
  *   by the widget parameter-order convention this is layout and would belong immediately after `data`; it sits after
  *   the styles because `BarChart` is a published 0.12.0 signature and inserting a parameter before `barWidth` would
  *   silently repoint every positional call site. `barHeight`, which only the horizontal layout reads, keeps it
  *   company.
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
    direction: Direction = Direction.Vertical,
    barHeight: Int = 1,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit = direction match
    case Direction.Vertical   => renderVertical(area, buffer)
    case Direction.Horizontal => renderHorizontal(area, buffer)

  /** Upright bars across the area, labels on the last row. */
  private def renderVertical(area: Rect, buffer: Buffer): Unit =
    val showLabels  = ColumnChart.showLabels(area, data.map((label, _) => label))
    val chartHeight = ColumnChart.chartHeight(area, showLabels)
    if area.isEmpty || data.isEmpty || chartHeight <= 0 || barWidth <= 0 then ()
    else
      val ceiling = scaleCeiling
      val stride  = ColumnChart.stride(barWidth, barGap)
      data.zipWithIndex.foreach { case ((label, value), index) =>
        val barLeft = ColumnChart.barLeftAt(area, index, stride)
        if ColumnChart.fits(area, barLeft, barWidth) then
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
          if showLabels then ColumnChart.drawCentredLabel(buffer, area, barLeft, barWidth, label, labelStyle)
      }

  /** Bars growing rightwards down the area, labels right-aligned in a gutter on the left. */
  private def renderHorizontal(area: Rect, buffer: Buffer): Unit =
    val labels     = data.map((label, _) => label)
    val showLabels = RowChart.showLabels(area, labels)
    val gutter     = RowChart.labelGutter(area, labels, showLabels)
    val plotLeft   = area.x + gutter
    val plotWidth  = area.width - gutter
    if area.isEmpty || data.isEmpty || plotWidth <= 0 || barHeight <= 0 then ()
    else
      val ceiling = scaleCeiling
      val stride  = RowChart.stride(barHeight, barGap)
      data.zipWithIndex.foreach { case ((label, value), index) =>
        val barTop = RowChart.barTopAt(area, index, stride)
        if RowChart.fits(area, barTop, barHeight) then
          BlockLadder.fillRow(
            buffer,
            y = barTop,
            rows = barHeight,
            left = plotLeft,
            right = plotLeft + plotWidth - 1,
            value = value,
            ceiling = ceiling,
            style = barStyle,
          )
          if gutter > 0 then RowChart.drawGutterLabel(buffer, area, barTop, gutter, label, labelStyle)
      }

  /** The top of the scale, at least one so an all-zero series cannot divide by zero. */
  private def scaleCeiling: Long =
    math.max(1L, max.getOrElse(data.map((_, value) => value).max))
