package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, Style, Widget}

/** A one-row progress meter: a caption followed by a filled/unfilled line.
  *
  * The glyphs come from a [[ProgressStyle]], so a bar can step whole cells or move smoothly with sub-cell partials
  * without this widget knowing which. `ratio` is clamped to `[0, 1]` and `NaN` reads as no progress.
  *
  * `fillRamp` colors the fill by how far along it is. It overrides `filledStyle`'s foreground and nothing else, so the
  * modifiers set there still apply.
  */
final case class LineGauge(
    ratio: Double,
    label: ProgressLabel = ProgressLabel.Percentage,
    style: Style = Style.Default,
    filledStyle: Style = Style.Default,
    progressStyle: ProgressStyle = ProgressStyle.Line,
    fillRamp: Option[ColorRamp] = None,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val clamped   = LineGauge.clamp(ratio)
      val caption   = label.render(clamped)
      // a hidden or empty caption must not leave its separator column behind as a stray blank
      val text      = if caption.isEmpty then "" else caption + " "
      val fitted    = CharWidth.substringByWidth(text, area.width)
      buffer.setString(area.x, area.y, fitted, style)
      val lineStart = area.x + CharWidth.of(fitted)
      val lineWidth = area.right - lineStart
      if lineWidth > 0 then
        val glyphs = progressStyle.glyphs(clamped, lineWidth)
        val filled = progressStyle.filledCells(clamped, lineWidth)
        val fill   = fillRamp.fold(filledStyle)(ramp => filledStyle.withFg(ramp.at(clamped)))
        glyphs.zipWithIndex.foreach: (glyph, index) =>
          // the boundary cell counts as filled for styling: it is part of the bar, not part of the track
          val isFilled = index <= filled && (index < filled || progressStyle.isSubCell)
          buffer.set(lineStart + index, area.y, Cell(glyph, if isFilled then fill else style))

object LineGauge:

  /** Convenience for out-of-`[0,1]` progress values: `LineGauge.of(3, 10)` is a 30% meter. */
  def of(current: Int, total: Int, label: ProgressLabel = ProgressLabel.Percentage): LineGauge =
    LineGauge(ratioOf(current, total), label)

  /** `current / total` as a fraction, with a zero or negative total reading as no progress rather than `NaN`. */
  private[widgets] def ratioOf(current: Int, total: Int): Double =
    if total <= 0 then 0.0 else current.toDouble / total

  private[widgets] def clamp(ratio: Double): Double =
    if ratio.isNaN then 0.0 else math.max(0.0, math.min(1.0, ratio))
