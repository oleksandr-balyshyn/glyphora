package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Rect, Style, Widget}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/** A pulsing placeholder for content that has not loaded yet (skeleton screen): a base shade with a brighter band
  * sweeping through, positioned from how long the animation has been running.
  *
  * `period` is one full sweep, in wall-clock time, so the sweep takes the same time whatever the app's tick rate and
  * whatever the widget's width. `bandWidth` defaults to a fifth of the area, which keeps the highlight proportional to
  * whatever the skeleton is standing in for; give it an explicit width when several skeletons of different sizes need
  * to pulse in step.
  */
final case class Skeleton(
    elapsed: FiniteDuration,
    style: Style = Style.Default.dim,
    bandStyle: Style = Style.Default,
    baseSymbol: String = "░",
    bandSymbol: String = "▒",
    bandWidth: Option[Int] = None,
    period: FiniteDuration = 1200.millis,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val band      = math.max(1, bandWidth.getOrElse(math.max(2, area.width / 5)))
      val cycle     = area.width + band
      val bandStart = Animation.step(elapsed, period, cycle) - band
      var y         = area.y
      while y < area.bottom do
        var x = area.x
        while x < area.right do
          val inBand = x - area.x >= bandStart && x - area.x < bandStart + band
          buffer.set(x, y, Cell(if inBand then bandSymbol else baseSymbol, if inBand then bandStyle else style))
          x += 1
        y += 1
