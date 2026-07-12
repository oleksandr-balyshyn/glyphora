package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, Style, Widget}

/** A pulsing placeholder for content that has not loaded yet (skeleton screen): light shade with a brighter band
  * sweeping through, advanced by `phase` (one step per tick).
  */
final case class Skeleton(
    phase: Int,
    style: Style = Style.Default.dim,
    bandStyle: Style = Style.Default,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val bandWidth = math.max(2, area.width / 5)
      val cycle = area.width + bandWidth
      val bandStart = math.floorMod(phase, cycle) - bandWidth
      var y = area.y
      while y < area.bottom do
        var x = area.x
        while x < area.right do
          val inBand = x - area.x >= bandStart && x - area.x < bandStart + bandWidth
          val symbol = if inBand then "▒" else "░"
          buffer.set(x, y, Cell(symbol, if inBand then bandStyle else style))
          x += 1
        y += 1

/** An indeterminate progress bar: a segment bouncing side to side, advanced by `phase`. */
final case class IndeterminateBar(
    phase: Int,
    style: Style = Style.Default.dim,
    segmentStyle: Style = Style.Default,
    segmentSymbol: String = "━",
    trackSymbol: String = "─",
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val segment = math.max(2, area.width / 4)
      val travel = math.max(1, area.width - segment)
      val bounce = math.floorMod(phase, 2 * travel)
      val start = if bounce < travel then bounce else 2 * travel - bounce
      var x = area.x
      while x < area.right do
        val inSegment = x - area.x >= start && x - area.x < start + segment
        val symbol = if inSegment then segmentSymbol else trackSymbol
        buffer.set(x, area.y, Cell(symbol, if inSegment then segmentStyle else style))
        x += 1

/** Text scrolling horizontally through the area (news-ticker style), advanced by `phase`; the text wraps around with a
  * gap between repetitions.
  */
final case class Marquee(
    content: String,
    phase: Int,
    style: Style = Style.Default,
    gap: Int = 4,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty && content.nonEmpty then
      val clusters = CharWidth.graphemeClusters(content).toVector ++ Vector.fill(gap)(" ")
      val offset = math.floorMod(phase, clusters.size)
      var x = area.x
      var index = offset
      while x < area.right do
        val cluster = clusters(index % clusters.size)
        val width = math.max(1, CharWidth.of(cluster))
        if x + width <= area.right then
          buffer.set(x, area.y, Cell(cluster, style))
          if width == 2 then buffer.set(x + 1, area.y, Cell.Empty)
        x += width
        index += 1
