package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Style}

/** Paints world-coordinate points into terminal cells for one [[Canvas]] render, accumulating sub-pixel hits and
  * flushing them as glyphs.
  *
  * The dot accumulation and the flush are [[SubCellSurface]]'s; what is this widget's own is the world-to-dot mapping
  * and the last-writer-wins style per cell. Last-writer-wins is right here and wrong for [[DotGrid]] for the reason
  * spelled out there: a canvas plots independent points, so which of two points sharing a cell wins carries no meaning.
  */
final class Painter private[widgets] (
    area: Rect,
    xBounds: (Double, Double),
    yBounds: (Double, Double),
    resolution: CanvasResolution,
    marker: String,
):

  private val surface = SubCellSurface(area, resolution, marker)
  private val styles  = Array.fill(surface.cellCount)(Style.Default)

  /** Marks the sub-pixel containing the world-coordinate point; points outside the bounds are dropped. The y axis
    * points up (world), while rows grow down (terminal) — the mapping flips it.
    */
  def paint(x: Double, y: Double, style: Style): Unit =
    val (xMin, xMax) = xBounds
    val (yMin, yMax) = yBounds
    if x >= xMin && x <= xMax && y >= yMin && y <= yMax && xMax > xMin && yMax > yMin then
      val column = ((x - xMin) / (xMax - xMin) * (surface.dotWidth - 1)).round.toInt
      val row    = ((yMax - y) / (yMax - yMin) * (surface.dotHeight - 1)).round.toInt
      val index  = surface.light(column, row)
      if index >= 0 then styles(index) = style

  private[widgets] def flush(buffer: Buffer): Unit =
    surface.flush(buffer, styles.apply)
