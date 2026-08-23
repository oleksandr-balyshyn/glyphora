package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, Style, Widget}

import scala.concurrent.duration.FiniteDuration

/** Text scrolling horizontally through the area (news-ticker style); the text wraps around with a gap between
  * repetitions.
  *
  * `cellsPerSecond` is a reading speed rather than a step count, so the same marquee scrolls at the same rate whatever
  * the app's tick rate. Around 8 cells per second is comfortable to read.
  */
final case class Marquee(
    content: String,
    elapsed: FiniteDuration,
    style: Style = Style.Default,
    gap: Int = 4,
    cellsPerSecond: Double = 8.0,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty && content.nonEmpty then
      val clusters = CharWidth.graphemeClusters(content).toVector ++ Vector.fill(gap)(" ")
      val offset   = Animation.stepAtRate(elapsed, cellsPerSecond, clusters.size)
      var x        = area.x
      var index    = offset
      while x < area.right do
        x = ClusterRow.put(buffer, x, area.y, clusters(index % clusters.size), style, area.right)
        index += 1
