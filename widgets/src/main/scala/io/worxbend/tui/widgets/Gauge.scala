package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Modifiers, Rect, Style, Widget}

/** A filled progress bar with a centered label; the fill spans the whole area height.
  *
  * `ratio` is clamped to `[0, 1]` and `NaN` reads as no progress. The caption comes from a [[ProgressLabel]], the same
  * vocabulary [[LineGauge]] uses, and defaults to the percentage; `ProgressLabel.Hidden` leaves the bar uncaptioned.
  *
  * `fillRamp` colors the fill by how far along it is. It replaces `filledStyle`'s background — the fill is drawn as
  * blank cells, so the bar's color *is* its background — and leaves everything else alone.
  */
final case class Gauge(
    ratio: Double,
    label: ProgressLabel = ProgressLabel.Percentage,
    style: Style = Style.Default,
    filledStyle: Style = Style.Default.reverse,
    fillRamp: Option[ColorRamp] = None,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val clamped     = Fraction.clamped(ratio)
      val filledWidth = math.round(clamped * area.width).toInt
      val fill        =
        fillRamp.fold(filledStyle)(ramp => filledStyle.without(Modifiers.Reverse).withBg(ramp.at(clamped)))

      def styleAt(x: Int): Style = if x - area.x < filledWidth then fill else style

      var y = area.y
      while y < area.bottom do
        var x = area.x
        while x < area.right do
          buffer.set(x, y, Cell(" ", styleAt(x)))
          x += 1
        y += 1

      val text = label.render(clamped)
      // `ProgressLabel.Hidden` renders nothing at all; centring an empty string would still walk the cluster loop
      if text.nonEmpty then
        val fitted = CharWidth.substringByWidth(text, area.width)
        val labelY = area.y + area.height / 2
        var x      = Alignment.Center.originAt(area.x, area.width, CharWidth.of(fitted))
        CharWidth.graphemeClusters(fitted).foreach { cluster =>
          x = ClusterRow.put(buffer, x, labelY, cluster, styleAt(x), area.right)
        }

object Gauge:
  /** Convenience for out-of-`[0,1]` progress values: `Gauge.of(3, 10)` is a 30% gauge. */
  def of(current: Int, total: Int): Gauge =
    Gauge(Fraction.ratio(current, total))
