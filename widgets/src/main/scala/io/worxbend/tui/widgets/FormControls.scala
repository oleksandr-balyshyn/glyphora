package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, Style, Widget}

/** A vertical set of mutually exclusive options, one marked `(•)`. Stateless — selection lives with the app. */
final case class RadioGroup(
    options: Seq[String],
    selected: Int,
    style: Style = Style.Default,
    selectedStyle: Style = Style.Default.bold,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    options.take(area.height).zipWithIndex.foreach { (label, index) =>
      val isSelected = index == selected
      val marker = if isSelected then "(•) " else "( ) "
      val rowStyle = if isSelected then selectedStyle else style
      buffer.setString(area.x, area.y + index, marker + label, rowStyle)
    }

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
      var x = area.x + 1
      while x < area.right - 1 do
        buffer.set(x, area.y, Cell("─", style))
        x += 1
      val span = math.max(1, max - min)
      val clamped = math.max(min, math.min(value, max))
      val knob = area.x + 1 + math.round((clamped - min).toDouble / span * (trackWidth - 1)).toInt
      buffer.set(knob, area.y, Cell("●", knobStyle))

/** A compact page indicator: dots for small totals, `page/total` otherwise. Pages are 1-based for display. */
final case class Paginator(
    current: Int,
    total: Int,
    style: Style = Style.Default,
    activeStyle: Style = Style.Default.bold,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty && total > 0 then
      val clamped = math.max(0, math.min(current, total - 1))
      if total <= 10 && total * 2 - 1 <= area.width then
        var x = area.x
        (0 until total).foreach { page =>
          val (symbol, pageStyle) = if page == clamped then ("●", activeStyle) else ("○", style)
          buffer.set(x, area.y, Cell(symbol, pageStyle))
          x += 2
        }
      else
        val text = s"${clamped + 1}/$total"
        buffer.setString(area.x, area.y, CharWidth.substringByWidth(text, area.width), style)
