package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Color, Rect, Style, Widget}

/** Distinct styles cycled across chart series/sectors when the caller does not supply any. */
private[widgets] object SeriesPalette:
  val Default: Vector[Style] = Vector(
    Style.Default.withFg(Color.Cyan),
    Style.Default.withFg(Color.Green),
    Style.Default.withFg(Color.Yellow),
    Style.Default.withFg(Color.Magenta),
    Style.Default.withFg(Color.Red),
    Style.Default.withFg(Color.Blue),
  )

  def at(index: Int): Style = Default(index % Default.size)

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
    val total = data.map(_._2).filter(_ > 0).sum
    if !area.isEmpty && total > 0 then
      val legendWidth = if showLegend then data.map(_._1.length).maxOption.getOrElse(0) + 7 else 0
      val discWidth   = area.width - legendWidth
      val radius      = math.min(discWidth / 2.0 / 2.0, area.height / 2.0) // width halved for cell aspect
      val centerX     = area.x + discWidth / 2.0
      val centerY     = area.y + area.height / 2.0
      val cumulative  = data.scanLeft(0.0)((acc, entry) => acc + math.max(0, entry._2)).tail
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
            buffer.set(x, y, Cell("█", styles(index % styles.size)))
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
        styles(index % styles.size),
      )
    }

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
    val maxTotal    = data.map(_._2.map(math.max(0L, _)).sum).maxOption.getOrElse(0L)
    if area.isEmpty || data.isEmpty || chartHeight <= 0 || maxTotal <= 0 then ()
    else
      data.zipWithIndex.foreach { case ((label, values), barIndex) =>
        val barLeft = area.x + barIndex * (barWidth + barGap)
        if barLeft + barWidth <= area.right then
          var bottom = area.y + chartHeight
          values.zipWithIndex.foreach { (value, series) =>
            val cells = math.round(math.max(0L, value).toDouble / maxTotal * chartHeight).toInt
            val top   = bottom - cells
            var y     = top
            while y < bottom do
              var x = barLeft
              while x < barLeft + barWidth do
                buffer.set(x, y, Cell("█", styles(series % styles.size)))
                x += 1
              y += 1
            bottom = top
          }
          if showLabels then
            val fitted = CharWidth.substringByWidth(label, barWidth)
            buffer.setString(barLeft + (barWidth - CharWidth.of(fitted)) / 2, area.bottom - 1, fitted, labelStyle)
      }

/** A value grid rendered as shade intensity: each cell maps its value (against the grid's max) onto a shade ramp — rows
  * are y (top first), columns are x.
  */
final case class Heatmap(
    values: Seq[Seq[Double]],
    style: Style = Style.Default,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    val ceiling = values.flatten.filter(_ > 0).maxOption.getOrElse(0.0)
    if !area.isEmpty && ceiling > 0 then
      values.take(area.height).zipWithIndex.foreach { (row, y) =>
        row.take(area.width).zipWithIndex.foreach { (value, x) =>
          val normalized = math.max(0.0, math.min(1.0, value / ceiling))
          val level      = math.round(normalized * (Heatmap.Ramp.size - 1)).toInt
          buffer.set(area.x + x, area.y + y, Cell(Heatmap.Ramp(level), style))
        }
      }

object Heatmap:
  private val Ramp: Vector[String] = Vector(" ", "░", "▒", "▓", "█")
