package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Style, Widget}

/** An animation frame indicator: the app advances `frame` on each tick and re-renders.
  *
  * Stateless by design — animation state is a tick counter the app already owns (`TuiApp.onTick`).
  */
final case class Spinner(
    frame: Int,
    label: String = "",
    frames: Seq[String] = Spinner.BrailleFrames,
    style: Style = Style.Default,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty && frames.nonEmpty then
      val glyph = frames(math.floorMod(frame, frames.size))
      val content = if label.isEmpty then glyph else s"$glyph $label"
      buffer.setString(area.x, area.y, content, style)

object Spinner:
  val BrailleFrames: Seq[String] = Seq("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
  val LineFrames: Seq[String] = Seq("|", "/", "-", "\\")
