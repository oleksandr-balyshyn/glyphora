package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Style, Widget}

/** A single-line option cycler (`◀ value ▶`). Stateless — the selected index lives with the application;
  * left/right key handling belongs to the caller (or the DSL wrapper).
  */
final case class Select(
    options: Seq[String],
    selected: Int,
    style: Style = Style.Default,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty && options.nonEmpty then
      val index = math.max(0, math.min(selected, options.size - 1))
      buffer.setString(area.x, area.y, s"◀ ${options(index)} ▶", style)
