package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, Style, Widget}

/** A compact page indicator: dots for small totals, `page/total` otherwise. Pages are 1-based for display. */
final case class Paginator(
    current: Int,
    total: Int,
    style: Style = Style.Default,
    highlightStyle: Style = Style.Default.bold,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty && total > 0 then
      val clamped = math.max(0, math.min(current, total - 1))
      if total <= 10 && total * 2 - 1 <= area.width then
        var x = area.x
        (0 until total).foreach { page =>
          val (symbol, pageStyle) = if page == clamped then ("●", highlightStyle) else ("○", style)
          buffer.set(x, area.y, Cell(symbol, pageStyle))
          x += 2
        }
      else
        val text = s"${clamped + 1}/$total"
        buffer.setString(area.x, area.y, CharWidth.substringByWidth(text, area.width), style)
