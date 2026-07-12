package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Direction, Rect, Style, Widget}

/** A separator line with an optional inline label: `── label ─────`. Vertical rules ignore the label. */
final case class Rule(
    label: Option[String] = None,
    orientation: Direction = Direction.Horizontal,
    style: Style = Style.Default,
    labelStyle: Style = Style.Default,
    lineSymbol: String = "─",
    verticalSymbol: String = "│",
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      orientation match
        case Direction.Horizontal => renderHorizontal(area, buffer)
        case Direction.Vertical   => renderVertical(area, buffer)

  private def renderHorizontal(area: Rect, buffer: Buffer): Unit =
    val y = area.y
    var x = area.x
    while x < area.right do
      buffer.set(x, y, Cell(lineSymbol, style))
      x += 1
    label.foreach { text =>
      val fitted = CharWidth.substringByWidth(s" $text ", math.max(0, area.width - 4))
      buffer.setString(area.x + 2, y, fitted, labelStyle)
    }

  private def renderVertical(area: Rect, buffer: Buffer): Unit =
    val x = area.x
    var y = area.y
    while y < area.bottom do
      buffer.set(x, y, Cell(verticalSymbol, style))
      y += 1
