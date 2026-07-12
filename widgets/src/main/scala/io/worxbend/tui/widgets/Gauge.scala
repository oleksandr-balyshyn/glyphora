package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, Style, Widget}

/** A filled progress bar with a centered label; the fill spans the whole area height.
  *
  * `ratio` is clamped to `[0, 1]`. The default label is the percentage.
  */
final case class Gauge(
    ratio: Double,
    label: Option[String] = None,
    style: Style = Style.Default,
    filledStyle: Style = Style.Default.reverse,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val clamped = math.max(0.0, math.min(1.0, ratio))
      val filledWidth = math.round(clamped * area.width).toInt

      def styleAt(x: Int): Style = if x - area.x < filledWidth then filledStyle else style

      var y = area.y
      while y < area.bottom do
        var x = area.x
        while x < area.right do
          buffer.set(x, y, Cell(" ", styleAt(x)))
          x += 1
        y += 1

      val text = label.getOrElse(s"${math.round(clamped * 100)}%")
      val fitted = CharWidth.substringByWidth(text, area.width)
      val textWidth = CharWidth.of(fitted)
      val startX = area.x + (area.width - textWidth) / 2
      val labelY = area.y + area.height / 2
      var x = startX
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
