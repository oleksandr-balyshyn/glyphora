package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Style, Widget}

/** A compact bar-per-column chart using the eight block-element glyphs, scaled over the full area height.
  *
  * Each data point maps to one column, oldest first; excess points are clipped on the right. `max` overrides the scale
  * ceiling (defaults to the data's maximum).
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
        // one terminal column per data point, filled over the full area height
        BlockLadder.fillColumn(
          buffer,
          x = area.x + column,
          columns = 1,
          bottom = area.bottom - 1,
          top = area.y,
          value = value,
          ceiling = ceiling,
          style = style,
        )
      }
