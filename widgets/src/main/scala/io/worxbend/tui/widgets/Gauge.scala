package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Modifiers, Rect, Style, Widget}

/** A filled progress bar with a centered label; the fill spans the whole area height.
  *
  * `ratio` is clamped to `[0, 1]` and `NaN` reads as no progress. The caption comes from a [[ProgressLabel]], the same
  * vocabulary [[LineGauge]] uses, and defaults to the percentage; `ProgressLabel.Hidden` leaves the bar uncaptioned.
  *
  * `fillRamp` colors the fill by how far along it is. Which part of the style it colors depends on how the bar is
  * drawn, described next.
  *
  * `labelStyle` fixes the caption's colours in one go. Left unset, the caption over the fill is written in the fill's
  * own two colours swapped, which is what keeps it readable; see `captionStyleAt` below.
  *
  * There are two ways to draw the bar, and `preset` picks between them:
  *
  *   - '''No preset (the default).''' The bar is blank cells wearing `filledStyle`, whose own default is
  *     `Style.Default.reverse` — so the bar's color *is* its background, and `fillRamp` replaces that background. The
  *     boundary is rounded to the nearest whole cell, which gives a 20-column bar 21 distinguishable states.
  *   - '''With a preset.''' The bar is the glyphs of a [[ProgressPreset]], the same vocabulary [[LineGauge]] draws
  *     with, so `ProgressPreset.Blocks` fills whole cells with `█` and paints the cell the boundary falls inside with
  *     the nearest eighth block. That turns the same 20-column bar into 161 states, and `ProgressPreset.Ascii` gives a
  *     `#`-over-`-` bar for a terminal with no Block Elements font. Here the glyph carries the color, so `fillRamp`
  *     sets the foreground rather than the background, and `Reverse` is dropped from `filledStyle`: a reversed full
  *     block paints the cell's background color over the whole cell, which is an invisible bar.
  */
final case class Gauge(
    ratio: Double,
    label: ProgressLabel = ProgressLabel.Percentage,
    style: Style = Style.Default,
    filledStyle: Style = Style.Default.reverse,
    fillRamp: Option[ColorRamp] = None,
    preset: Option[ProgressPreset] = None,
    labelStyle: Option[Style] = None,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val clamped = Fraction.clamped(ratio)
      val fill    = fillStyleFor(clamped)
      val filled  = filledColumns(clamped, area.width)
      val glyphs  = preset.map(_.glyphs(clamped, area.width))

      def styleAt(x: Int): Style   = if x - area.x < filled then fill else style
      def symbolAt(x: Int): String = glyphs.fold(" ")(_(x - area.x))

      var y = area.y
      while y < area.bottom do
        var x = area.x
        while x < area.right do
          buffer.set(x, y, Cell(symbolAt(x), styleAt(x)))
          x += 1
        y += 1

      val text = label.render(clamped)
      // `ProgressLabel.Hidden` renders nothing at all; centring an empty string would still walk the cluster loop
      if text.nonEmpty then
        val fitted = CharWidth.substringByWidth(text, area.width)
        val labelY = area.y + area.height / 2
        var x      = Alignment.Center.originAt(area.x, area.width, CharWidth.of(fitted))
        CharWidth.graphemeClusters(fitted).foreach { cluster =>
          x = ClusterRow.put(buffer, x, labelY, cluster, captionStyleAt(x - area.x < filled, fill), area.right)
        }

  /** The style one cluster of the caption is written with.
    *
    * An explicit `labelStyle` wins everywhere. Without one, the caption over the *track* keeps the widget's `style`,
    * as it always has, while the caption over the *fill* wears the fill's own style with `Reverse` toggled — which is
    * a terminal's way of saying "swap this cell's foreground and background".
    *
    * That swap is what makes the caption legible. Before it, the caption inherited the fill style unchanged: with the
    * default reversed fill the glyphs were reversed text drawn on an already-reversed cell, which some terminals
    * resolve back to invisible, and with a `fillRamp` the glyphs kept the default foreground over whatever colour the
    * ramp had chosen — white on pale yellow at the end of a heat ramp. Swapping guarantees the caption is drawn in the
    * two colours the cell already has, the other way round, so it can never collide with its own background.
    */
  private def captionStyleAt(overFill: Boolean, fill: Style): Style =
    labelStyle.getOrElse(if overFill then swapped(fill) else style)

  /** `cell` with `Reverse` toggled — set if it was clear, cleared if it was set. */
  private def swapped(cell: Style): Style =
    if cell.modifiers.hasAny(Modifiers.Reverse) then cell.without(Modifiers.Reverse) else cell.reverse

  /** The style the filled part of the bar wears, with `fillRamp` applied to whichever half of it carries the color: the
    * background for the blank-cell bar, the foreground for a preset's glyphs.
    */
  private def fillStyleFor(clamped: Double): Style =
    preset match
      case None    =>
        fillRamp.fold(filledStyle)(ramp => filledStyle.without(Modifiers.Reverse).withBg(ramp.at(clamped)))
      case Some(_) =>
        val glyphStyle = filledStyle.without(Modifiers.Reverse)
        fillRamp.fold(glyphStyle)(ramp => glyphStyle.withFg(ramp.at(clamped)))

  /** How many columns from the left edge count as "done" for styling.
    *
    * Without a preset that is the boundary rounded to the nearest cell, which is what this widget has always drawn.
    * With one, the arithmetic is [[ProgressPreset]]'s so the block gauge and the line gauge cannot disagree about where
    * a ratio falls, and the boundary cell counts as part of the bar whenever the preset can draw a partial glyph in it
    * — the partial block is the bar, not the track.
    */
  private def filledColumns(clamped: Double, width: Int): Int =
    preset match
      case None         => math.max(0, math.min(width, math.round(clamped * width).toInt))
      case Some(preset) =>
        val whole = preset.filledCells(clamped, width)
        if preset.isSubCell then math.min(width, whole + 1) else whole

object Gauge:
  /** Convenience for out-of-`[0,1]` progress values: `Gauge.of(3, 10)` is a 30% gauge. */
  def of(current: Int, total: Int): Gauge =
    Gauge(Fraction.ratio(current, total))
