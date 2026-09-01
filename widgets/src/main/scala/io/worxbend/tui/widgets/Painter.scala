package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Style}

/** Paints world-coordinate points into terminal cells for one [[Canvas]] render, accumulating sub-pixel hits and
  * flushing them as glyphs.
  *
  * The dot accumulation and the flush are [[SubCellSurface]]'s; what is this widget's own is the world-to-dot mapping
  * and the last-writer-wins style per cell. Last-writer-wins is right here and wrong for [[DotGrid]] for the reason
  * spelled out there: a canvas plots independent points, so which of two points sharing a cell wins carries no meaning.
  *
  * Two coordinate systems meet here, and it is worth naming both. *World* coordinates are the numbers a [[Shape]]
  * speaks in — whatever `xBounds`/`yBounds` the canvas was given, with y pointing up the way a graph's y axis does.
  * *Dot* coordinates are integer positions on the sub-cell grid the surface actually lights: column 0 is the left edge,
  * row 0 is the **top** edge, and how many dots fit in a cell depends on the [[CanvasResolution]]. A shape that wants
  * to scan-convert — walk a shape dot by dot rather than guess a sample count — works in dot space, via [[getPoint]],
  * [[paintDot]], [[bounds]] and [[dotSize]].
  *
  * One painter is created per [[Canvas]] render and is used on the render thread only; it is never shared or retained.
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

  /** The world rectangle this painter maps, as `((xMin, xMax), (yMin, yMax))`.
    *
    * A shape that clips or scan-converts has to know what it is being drawn inside. Without it the only thing a shape
    * can do is sample its own outline in world units and hope the density matches the surface, which is how a line
    * drawn under bounds of `0.0` to `1.0` used to come out as a handful of scattered dots.
    */
  def bounds: ((Double, Double), (Double, Double)) = (xBounds, yBounds)

  /** The dot grid's extent as `(columns, rows)` — the exclusive upper bound of [[paintDot]]'s arguments. Both are `0`
    * on an empty area, which makes every paint a no-op.
    */
  def dotSize: (Int, Int) = (surface.dotWidth, surface.dotHeight)

  /** The dot `(column, row)` a world point falls in, or `None` when there is no such dot.
    *
    * `None` is returned when the point lies outside [[bounds]], when either coordinate is non-finite, when the bounds
    * are degenerate (`xMin == xMax`), or when the canvas has no room at all. Row 0 is the *top* of the area: y points
    * up in world coordinates and rows grow down, and the flip happens here so that no caller repeats it.
    *
    * Rejecting NaN and Infinity is written out rather than left implicit. It used to work by accident — every
    * comparison against NaN is false, so `x >= xMin` already dropped it — and an accident is not a contract; stated
    * this way the guard survives the next rewrite of the arithmetic around it.
    */
  def getPoint(x: Double, y: Double): Option[(Int, Int)] =
    val (xMin, xMax) = xBounds
    val (yMin, yMax) = yBounds
    val insideWorld  = x.isFinite && y.isFinite && x >= xMin && x <= xMax && y >= yMin && y <= yMax
    if insideWorld && xMax > xMin && yMax > yMin && surface.dotWidth > 0 && surface.dotHeight > 0 then
      val column = ((x - xMin) / (xMax - xMin) * (surface.dotWidth - 1)).round.toInt
      val row    = ((yMax - y) / (yMax - yMin) * (surface.dotHeight - 1)).round.toInt
      Some((column, row))
    else None

  /** Marks the sub-pixel containing the world-coordinate point; points outside the bounds, and non-finite ones, are
    * dropped. The y axis points up (world), while rows grow down (terminal) — the mapping flips it.
    */
  def paint(x: Double, y: Double, style: Style): Unit =
    getPoint(x, y).foreach((column, row) => paintDot(column, row, style))

  /** Marks dot `(column, row)` on the sub-cell grid directly, for a shape that has already done its own arithmetic.
    *
    * A dot outside the grid is dropped, never clamped and never wrapped: clamping would pile a shape's overflow up
    * against the edge and make an off-screen figure look like an on-screen one.
    */
  def paintDot(column: Int, row: Int, style: Style): Unit =
    val index = surface.light(column, row)
    if index >= 0 then styles(index) = style

  private[widgets] def flush(buffer: Buffer): Unit =
    surface.flush(buffer, styles.apply)
