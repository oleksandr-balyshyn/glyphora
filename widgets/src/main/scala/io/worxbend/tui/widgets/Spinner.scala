package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, Style, Widget}

import scala.concurrent.duration.FiniteDuration

/** An animation frame indicator, drawn from how long the animation has been running.
  *
  * Stateless by design: the frame is a pure function of `elapsed`, so nothing here retains state between renders and a
  * test can render any moment directly. Elapsed *time* rather than a tick count is what makes a preset portable — the
  * same spinner reads the same in an app ticking every 50ms and one ticking every 200ms.
  *
  * The glyph and the label are styled separately, because they carry different weight: the glyph is the moving part and
  * usually takes the accent color, while the label is ordinary text. `labelStyle` defaults to `style`.
  */
final case class Spinner(
    elapsed: FiniteDuration,
    label: String = "",
    preset: SpinnerPreset = SpinnerPreset.Dots,
    style: Style = Style.Default,
    labelStyle: Option[Style] = None,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val glyph = CharWidth.substringByWidth(preset.frameAt(elapsed), area.width)
      buffer.setString(area.x, area.y, glyph, style)
      if label.nonEmpty then
        val labelX     = area.x + CharWidth.of(glyph) + 1
        val labelWidth = area.right - labelX
        if labelWidth > 0 then
          buffer.setString(labelX, area.y, CharWidth.substringByWidth(label, labelWidth), labelStyle.getOrElse(style))

  /** The width this spinner wants: the preset's frame width plus `" $label"` when there is one. */
  def preferredWidth: Int = preset.width + (if label.isEmpty then 0 else 1 + CharWidth.of(label))
