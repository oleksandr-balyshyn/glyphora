package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Style, Widget}

/** A labeled on/off switch. Stateless — the flag lives with the application. */
final case class Toggle(
    label: String,
    on: Boolean,
    style: Style = Style.Default,
    onSymbol: String = "◉ ",
    offSymbol: String = "○ ",
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val symbol = if on then onSymbol else offSymbol
      buffer.setString(area.x, area.y, symbol + label, style)
