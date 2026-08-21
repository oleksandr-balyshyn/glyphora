package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, Style, Widget}

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
      val ceiling = math.max(1L, max.getOrElse(data.map((_, value) => value).max))
      // a negative gap can walk `barLeft` left of the area; `Buffer.set` clips to the buffer, not to the Rect, so an
      // unclamped bar paints straight over whatever widget owns the columns to the left
      val stride  = math.max(1, barWidth + barGap)
      data.zipWithIndex.foreach { case ((label, value), index) =>
        val barLeft = area.x + index * stride
        if barLeft >= area.x && barLeft + barWidth <= area.right then
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
    )

  private def drawLabel(buffer: Buffer, area: Rect, barLeft: Int, label: String): Unit =
    val fitted = CharWidth.substringByWidth(label, barWidth)
    val offset = (barWidth - CharWidth.of(fitted)) / 2
    buffer.setString(barLeft + offset, area.bottom - 1, fitted, labelStyle)
