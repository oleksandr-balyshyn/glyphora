package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, Style, Widget}

/** A vertical set of mutually exclusive options, one marked `(•)`. Stateless — selection lives with the app. */
final case class RadioGroup(
    options: Seq[String],
    selected: Int,
    style: Style = Style.Default,
    selectedStyle: Style = Style.Default.bold,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      options.take(area.height).zipWithIndex.foreach { (label, index) =>
        val isSelected = index == selected
        val marker     = if isSelected then "(•) " else "( ) "
        val rowStyle   = if isSelected then selectedStyle else style
        buffer.setString(area.x, area.y + index, CharWidth.substringByWidth(marker + label, area.width), rowStyle)
      }
