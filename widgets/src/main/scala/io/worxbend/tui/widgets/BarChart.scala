package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, Style, Widget}

/** Vertical bars with optional labels underneath: each `(label, value)` gets a `barWidth`-column bar scaled against
  * `max` (defaulting to the data's maximum), topped with a partial block glyph for sub-cell precision.
  */
final case class BarChart(
    data: Seq[(String, Long)],
    barWidth: Int = 3,
    barGap: Int = 1,
    max: Option[Long] = None,
    barStyle: Style = Style.Default,
    labelStyle: Style = Style.Default,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    val showLabels  = area.height >= 2 && data.exists((label, _) => label.nonEmpty)
    val chartHeight = if showLabels then area.height - 1 else area.height
    if area.isEmpty || data.isEmpty || chartHeight <= 0 || barWidth <= 0 then ()
    else
      val ceiling = math.max(1L, max.getOrElse(data.map(_._2).max))
      data.zipWithIndex.foreach { case ((label, value), index) =>
        val barLeft = area.x + index * (barWidth + barGap)
        if barLeft + barWidth <= area.right then
          drawBar(buffer, area, barLeft, chartHeight, value, ceiling)
          if showLabels then drawLabel(buffer, area, barLeft, label)
      }

  private def drawBar(buffer: Buffer, area: Rect, barLeft: Int, chartHeight: Int, value: Long, ceiling: Long): Unit =
    val clamped = math.max(0L, math.min(value, ceiling))
    var eighths = math.round(clamped.toDouble / ceiling * chartHeight * 8).toInt
    var y       = area.y + chartHeight - 1
    while y >= area.y && eighths > 0 do
      val levelIndex = math.min(eighths, 8)
      var x          = barLeft
      while x < barLeft + barWidth do
        buffer.set(x, y, Cell(BarChart.Levels(levelIndex - 1), barStyle))
        x += 1
      eighths -= levelIndex
      y -= 1

  private def drawLabel(buffer: Buffer, area: Rect, barLeft: Int, label: String): Unit =
    val fitted = CharWidth.substringByWidth(label, barWidth)
    val offset = (barWidth - CharWidth.of(fitted)) / 2
    buffer.setString(barLeft + offset, area.bottom - 1, fitted, labelStyle)

object BarChart:
  private val Levels: Vector[String] = Vector("▁", "▂", "▃", "▄", "▅", "▆", "▇", "█")
