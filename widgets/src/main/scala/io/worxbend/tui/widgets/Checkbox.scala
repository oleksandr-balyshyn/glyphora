package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Style, Widget}

/** A labeled checkbox. Stateless — the checked flag lives with the application (typically in a `Signal`). */
final case class Checkbox(
    label: String,
    checked: Boolean,
    style: Style = Style.Default,
    checkedSymbol: String = "[x] ",
    uncheckedSymbol: String = "[ ] ",
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val symbol = if checked then checkedSymbol else uncheckedSymbol
      buffer.setString(area.x, area.y, symbol + label, style)
