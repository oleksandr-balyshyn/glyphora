package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Color, Rect, Style, Widget}

/** A clickable hyperlink (OSC 8): renders `label` underlined with the link attached — terminals without OSC 8
  * support just show the styled text.
  */
final case class Link(
    label: String,
    url: String,
    style: Style = Style.Default.withFg(Color.Blue).underline,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then buffer.setString(area.x, area.y, label, style.withLink(url))
