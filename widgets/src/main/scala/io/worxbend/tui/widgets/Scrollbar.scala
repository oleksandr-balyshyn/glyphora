package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Direction, Rect, Style, Widget}

/** A scrollbar strip: a vertical bar on the area's right edge or a horizontal bar on its bottom edge.
  *
  * The thumb's size is proportional to how much of the content the track (viewport) covers; when the content fits
  * entirely, only the track is drawn.
  *
  * Stateless on purpose. In this toolkit a `StatefulWidget` is a widget whose *render* adjusts caller-owned state — a
  * list scrolling itself to keep its selection visible, for instance. A scrollbar adjusts nothing: where the thumb goes
  * is a pure function of `contentLength`, `position` and the area it is given, so both numbers are passed in as
  * ordinary parameters and the caller keeps whatever state it already had.
  *
  * @param contentLength
  *   the full extent of the content being scrolled, in rows (vertical) or columns (horizontal)
  * @param position
  *   how far into the content the viewport starts, in the same units; clamped, so an out-of-range value pins the thumb
  *   to an end rather than drawing it off the track
  */
final case class Scrollbar(
    contentLength: Int,
    position: Int = 0,
    orientation: Direction = Direction.Vertical,
    style: Style = Style.Default,
    thumbStyle: Style = Style.Default,
    trackSymbol: String = "│",
    thumbSymbol: String = "█",
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val trackLength = orientation match
        case Direction.Vertical   => area.height
        case Direction.Horizontal => area.width
      val thumb       = thumbRange(trackLength)
      var along       = 0
      while along < trackLength do
        val inThumb   = thumb.exists((start, size) => along >= start && along < start + size)
        val symbol    = if inThumb then thumbSymbol else trackSymbol
        val cellStyle = if inThumb then thumbStyle else style
        orientation match
          case Direction.Vertical   => buffer.set(area.right - 1, area.y + along, Cell(symbol, cellStyle))
          case Direction.Horizontal => buffer.set(area.x + along, area.bottom - 1, Cell(symbol, cellStyle))
        along += 1

  /** `(start, size)` of the thumb along the track, or `None` when the content fits the viewport. */
  private def thumbRange(trackLength: Int): Option[(Int, Int)] =
    if contentLength <= trackLength || trackLength == 0 then None
    else
      val size            = math.max(1, trackLength * trackLength / contentLength)
      val maxPosition     = contentLength - trackLength
      val clampedPosition = math.max(0, math.min(position, maxPosition))
      val start           = math.round(clampedPosition.toDouble / maxPosition * (trackLength - size)).toInt
      Some((start, size))
