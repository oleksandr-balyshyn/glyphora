package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Line, Rect, Style, Widget}

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
      val cursor = RowCursor(buffer, area.y, area.x, area.right)
      titles.zipWithIndex.foreach { (title, index) =>
        val titleStyle = if index == selected then style.patch(highlightStyle) else style
        cursor.skip(LineRenderer.render(buffer, cursor.at, area.y, title, cursor.remaining, titleStyle))
        val isLast     = index == titles.size - 1
        if !isLast then cursor.write(divider, style)
      }
