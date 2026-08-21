package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, Style, Widget}

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
    val showLabels  = area.height >= 2 && data.exists((label, _) => label.nonEmpty)
    val chartHeight = if showLabels then area.height - 1 else area.height
    val maxTotal    = data.map((_, values) => values.map(math.max(0L, _)).sum).maxOption.getOrElse(0L)
    if area.isEmpty || data.isEmpty || chartHeight <= 0 || maxTotal <= 0 then ()
    else
      val stride = math.max(1, barWidth + barGap)
      data.zipWithIndex.foreach { case ((label, values), barIndex) =>
        val barLeft = area.x + barIndex * stride
        if barLeft >= area.x && barLeft + barWidth <= area.right then
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
          if showLabels then
            val fitted = CharWidth.substringByWidth(label, barWidth)
            buffer.setString(barLeft + (barWidth - CharWidth.of(fitted)) / 2, area.bottom - 1, fitted, labelStyle)
      }
