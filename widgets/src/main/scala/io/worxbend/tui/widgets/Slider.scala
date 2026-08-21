package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Rect, Style, Widget}

/** A one-row value slider: `├───●──────┤` proportional to `value` within `[min, max]`. */
final case class Slider(
    value: Int,
    min: Int = 0,
    max: Int = 100,
    style: Style = Style.Default,
    knobStyle: Style = Style.Default.bold,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if area.width >= 3 && !area.isEmpty then
      val trackWidth = area.width - 2
      buffer.set(area.x, area.y, Cell("├", style))
      buffer.set(area.right - 1, area.y, Cell("┤", style))
      var x          = area.x + 1
      while x < area.right - 1 do
        buffer.set(x, area.y, Cell("─", style))
        x += 1
      val span       = math.max(1, max - min)
      val clamped    = math.max(min, math.min(value, max))
      val knob       = area.x + 1 + math.round((clamped - min).toDouble / span * (trackWidth - 1)).toInt
      buffer.set(knob, area.y, Cell("●", knobStyle))
