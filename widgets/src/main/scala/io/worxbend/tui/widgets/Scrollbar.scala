package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Direction, Rect, StatefulWidget, Style}

/** Caller-owned scrollbar state: total content length (in rows/columns) and the current scroll position. */
final class ScrollbarState(var contentLength: Int, var position: Int = 0)

/** A scrollbar strip: a vertical bar on the area's right edge or a horizontal bar on its bottom edge.
  *
  * The thumb's size is proportional to how much of the content the track (viewport) covers; when the content
  * fits entirely, only the track is drawn.
  */
final case class Scrollbar(
    orientation: Direction = Direction.Vertical,
    trackSymbol: String = "│",
    thumbSymbol: String = "█",
    style: Style = Style.Default,
    thumbStyle: Style = Style.Default,
) extends StatefulWidget[ScrollbarState]:

  def render(area: Rect, buffer: Buffer, state: ScrollbarState): Unit =
    if !area.isEmpty then
      val trackLength = orientation match
        case Direction.Vertical   => area.height
        case Direction.Horizontal => area.width
      val thumb = thumbRange(trackLength, state)
      var along = 0
      while along < trackLength do
        val inThumb = thumb.exists(range => along >= range._1 && along < range._1 + range._2)
        val symbol = if inThumb then thumbSymbol else trackSymbol
        val cellStyle = if inThumb then thumbStyle else style
        orientation match
          case Direction.Vertical   => buffer.set(area.right - 1, area.y + along, Cell(symbol, cellStyle))
          case Direction.Horizontal => buffer.set(area.x + along, area.bottom - 1, Cell(symbol, cellStyle))
        along += 1

  /** `(start, size)` of the thumb along the track, or `None` when the content fits the viewport. */
  private def thumbRange(trackLength: Int, state: ScrollbarState): Option[(Int, Int)] =
    if state.contentLength <= trackLength || trackLength == 0 then None
    else
      val size = math.max(1, trackLength * trackLength / state.contentLength)
      val maxPosition = state.contentLength - trackLength
      val clampedPosition = math.max(0, math.min(state.position, maxPosition))
      val start = math.round(clampedPosition.toDouble / maxPosition * (trackLength - size)).toInt
      Some((start, size))
