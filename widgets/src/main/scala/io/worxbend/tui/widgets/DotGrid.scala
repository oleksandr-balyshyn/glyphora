package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Style}

/** A sub-cell scratch surface over one [[Rect]], addressed in dot space rather than in world coordinates.
  *
  * Separate from [[Painter]] because a [[io.worxbend.tui.core.Cell]] holds one [[Style]] and the two surfaces need
  * opposite resolution rules. A canvas plots independent points, so `Painter`'s last-writer-wins is harmless. An
  * animation with a graded tail puts dots of *different* brightness in one cell, and the cell must take the brightest —
  * otherwise a dim tail dot recolours the bright head dot it happens to share a cell with, which is exactly backwards.
  *
  * The dot accumulation itself is [[SubCellSurface]]'s; what is this widget's own is the max-wins intensity per cell.
  *
  * Allocated per render rather than retained, so its state is two primitive arrays and no boxed `Array[Style]`.
  */
private[widgets] final class DotGrid(area: Rect, resolution: CanvasResolution, marker: String):

  private val surface = SubCellSurface(area, resolution, marker)

  /** The grid's extent in dots. Zero on an empty `area`, which makes every [[light]] a no-op without a second guard. */
  val dotWidth: Int  = surface.dotWidth
  val dotHeight: Int = surface.dotHeight

  val columnAspect: Int = SubCell.columnAspect(resolution)

  /** The dot a centred figure is pinned to.
    *
    * Rounded down rather than to the nearest half dot so the centre is an integer dot at every size: a figure that
    * moved half a dot when its area grew by one row would shift every golden frame under a resize.
    */
  val centreColumn: Int = (dotWidth - 1) / 2
  val centreRow: Int    = (dotHeight - 1) / 2

  /** One entry per colour *slot*, not per cell: at half-block resolution the upper and lower halves of a cell are
    * coloured independently, so a graded tail can now be brighter in one half than in the other.
    */
  private val intensities = new Array[Double](surface.slotCount)

  /** Lights dot `(col, row)` at `intensity` in `[0, 1]`. Coordinates off the grid are dropped rather than wrapped or
    * clamped, so a caller doing its own centring arithmetic cannot smear the edge. Masks accumulate; the cell keeps the
    * highest intensity it was given.
    */
  def light(col: Int, row: Int, intensity: Double): Unit =
    val index = surface.light(col, row)
    if index >= 0 && intensity > intensities(index) then intensities(index) = intensity

  /** Writes every cell holding at least one dot, coloured `styleFor(cellIntensity)`. Cells with no dots are left
    * untouched, so the figure composes over whatever is beneath it under `layers` — the same rule [[Canvas]] follows.
    */
  def flush(buffer: Buffer, styleFor: Double => Style): Unit =
    surface.flush(buffer, index => styleFor(intensities(index)))
