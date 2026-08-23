package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Measured, Rect, Style, Widget}

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
) extends Widget
    with Measured:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val cursor = RowCursor(buffer, area.y, area.x, area.right)
      cursor.write(preset.frameAt(elapsed), style)
      if label.nonEmpty then
        cursor.skip(1) // one blank column between the moving glyph and its label
        cursor.write(label, labelStyle.getOrElse(style))

  /** The width this spinner wants: the preset's frame width plus `" $label"` when there is one. A spinner is always one
    * row and its width never depends on how many rows it is given, so `height` is ignored.
    */
  override def widthAt(height: Int): Option[Int] =
    val _ = height
    Some(preset.width + (if label.isEmpty then 0 else 1 + CharWidth.of(label)))
