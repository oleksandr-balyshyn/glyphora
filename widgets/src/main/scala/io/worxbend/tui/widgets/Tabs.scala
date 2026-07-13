package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, Line, Style, Widget}

/** A single-row tab bar: titles separated by a divider, the selected title highlighted. */
final case class Tabs(
    titles: Seq[Line],
    selected: Int = 0,
    style: Style = Style.Default,
    highlightStyle: Style = Style.Default.reverse,
    divider: String = " │ ",
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      var x = area.x
      titles.zipWithIndex.foreach { (title, index) =>
        val remaining = area.right - x
        if remaining > 0 then
          val titleStyle = if index == selected then style.patch(highlightStyle) else style
          x += LineRenderer.render(buffer, x, area.y, title, remaining, titleStyle)
          val isLast     = index == titles.size - 1
          if !isLast && area.right - x > 0 then
            val fitted = CharWidth.substringByWidth(divider, area.right - x)
            buffer.setString(x, area.y, fitted, style)
            x += CharWidth.of(fitted)
      }
