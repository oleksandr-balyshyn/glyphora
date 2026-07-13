package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, Style, Widget}

/** A one-row progress meter: a label followed by a filled/unfilled line. */
final case class LineGauge(
    ratio: Double,
    label: Option[String] = None,
    style: Style = Style.Default,
    filledStyle: Style = Style.Default,
    filledSymbol: String = "━",
    unfilledSymbol: String = "─",
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val clamped   = math.max(0.0, math.min(1.0, ratio))
      val text      = label.getOrElse(s"${math.round(clamped * 100)}%") + " "
      val fitted    = CharWidth.substringByWidth(text, area.width)
      buffer.setString(area.x, area.y, fitted, style)
      val lineStart = area.x + CharWidth.of(fitted)
      val lineWidth = area.right - lineStart
      if lineWidth > 0 then
        val filled = math.round(clamped * lineWidth).toInt
        var x      = lineStart
        while x < area.right do
          val symbol      = if x - lineStart < filled then filledSymbol else unfilledSymbol
          val symbolStyle = if x - lineStart < filled then filledStyle else style
          buffer.set(x, area.y, Cell(symbol, symbolStyle))
          x += 1
