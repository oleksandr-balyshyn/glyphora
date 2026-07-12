package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Rect, Style, Widget}

/** A compact bar-per-column chart using the eight block-element glyphs, scaled over the full area height.
  *
  * Each data point maps to one column, oldest first; excess points are clipped on the right. `max` overrides
  * the scale ceiling (defaults to the data's maximum).
  */
final case class Sparkline(
    data: Seq[Long],
    max: Option[Long] = None,
    style: Style = Style.Default,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty && data.nonEmpty then
      val ceiling = math.max(1L, max.getOrElse(data.max))
      data.take(area.width).zipWithIndex.foreach { (value, column) =>
        val clamped = math.max(0L, math.min(value, ceiling))
        // total eighth-blocks of fill for this column, over the full area height
        var eighths = math.round(clamped.toDouble / ceiling * area.height * 8).toInt
        val x = area.x + column
        var y = area.bottom - 1
        while y >= area.y && eighths > 0 do
          val levelIndex = math.min(eighths, 8)
          buffer.set(x, y, Cell(Sparkline.Levels(levelIndex - 1), style))
          eighths -= levelIndex
          y -= 1
      }

object Sparkline:
  private val Levels: Vector[String] = Vector("▁", "▂", "▃", "▄", "▅", "▆", "▇", "█")
