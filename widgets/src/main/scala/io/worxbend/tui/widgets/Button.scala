package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, Style, Widget}

/** A pressable control rendered as `[ label ]`, centered in its area. Stateless — press handling lives with the caller
  * (the DSL element activates on Enter/Space while focused).
  */
final case class Button(
    label: String,
    style: Style = Style.Default,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val text = s"[ $label ]"
      val fitted = CharWidth.substringByWidth(text, area.width)
      val startX = area.x + (area.width - CharWidth.of(fitted)) / 2
      buffer.setString(startX, area.y + area.height / 2, fitted, style)
