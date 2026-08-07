package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Modifiers, Rect, Style, Widget}

/** A filled progress bar with a centered label; the fill spans the whole area height.
  *
  * `ratio` is clamped to `[0, 1]` and `NaN` reads as no progress. The default label is the percentage.
  *
  * `fillRamp` colors the fill by how far along it is. It replaces `filledStyle`'s background — the fill is drawn as
  * blank cells, so the bar's color *is* its background — and leaves everything else alone.
  */
final case class Gauge(
    ratio: Double,
    label: Option[String] = None,
    style: Style = Style.Default,
    filledStyle: Style = Style.Default.reverse,
    fillRamp: Option[ColorRamp] = None,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val clamped     = if ratio.isNaN then 0.0 else math.max(0.0, math.min(1.0, ratio))
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

      val text      = label.getOrElse(s"${math.round(clamped * 100)}%")
      val fitted    = CharWidth.substringByWidth(text, area.width)
      val textWidth = CharWidth.of(fitted)
      val startX    = area.x + (area.width - textWidth) / 2
      val labelY    = area.y + area.height / 2
      var x         = startX
      CharWidth.graphemeClusters(fitted).foreach { cluster =>
        val width = CharWidth.of(cluster)
        if width > 0 then
          buffer.set(x, labelY, Cell(cluster, styleAt(x)))
          if width == 2 then buffer.set(x + 1, labelY, Cell.Empty)
          x += width
      }

object Gauge:
  /** Convenience for out-of-`[0,1]` progress values: `Gauge.of(3, 10)` is a 30% gauge. */
  def of(current: Int, total: Int): Gauge =
    Gauge(if total <= 0 then 0.0 else current.toDouble / total)
