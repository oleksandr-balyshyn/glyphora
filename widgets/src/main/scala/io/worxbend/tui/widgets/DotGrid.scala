package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, Style}

/** Sub-cell bit layouts and glyphs, in one place.
  *
  * Extracted from `Painter` rather than copied because [[Painter]] and [[DotGrid]] draw the same dots under different
  * *styling* rules, and two divergent copies of the braille bit table is the one outcome worth designing against.
  */
private[widgets] object SubCell:

  /** How many dots one cell contributes, as `(across, down)` — `(1,1)`, `(1,2)`, `(2,4)`. */
  def dotsPerCell(resolution: CanvasResolution): (Int, Int) =
    resolution match
      case CanvasResolution.Cell      => (1, 1)
      case CanvasResolution.HalfBlock => (1, 2)
      case CanvasResolution.Braille   => (2, 4)

  /** The bit sub-position `(dx, dy)` contributes to a cell's mask. */
  def bitFor(resolution: CanvasResolution, dx: Int, dy: Int): Int =
    resolution match
      case CanvasResolution.Cell      => 1
      case CanvasResolution.HalfBlock => if dy == 0 then 1 else 2
      case CanvasResolution.Braille   => BrailleBits(dy * 2 + dx)

  /** The glyph for a dot mask; `marker` is used only at [[CanvasResolution.Cell]], where there is one dot per cell. */
  def glyphFor(resolution: CanvasResolution, mask: Int, marker: String): String =
    resolution match
      case CanvasResolution.Cell      => marker
      case CanvasResolution.HalfBlock =>
        mask match
          case 1 => "▀"
          case 2 => "▄"
          case _ => "█"
      case CanvasResolution.Braille   => (0x2800 + mask).toChar.toString

  /** How many dot *columns* one dot *row* is worth if a circle is to come out round.
    *
    * `2` at cell resolution and `1` otherwise, and that is arithmetic rather than a fudge: a terminal cell is about
    * twice as tall as it is wide, braille packs 2 dots across and 4 down, half-blocks 1 and 2 — in both of those the
    * two factors cancel and a dot is square. A whole cell is not.
    */
  def columnAspect(resolution: CanvasResolution): Int =
    resolution match
      case CanvasResolution.Cell                                 => 2
      case CanvasResolution.HalfBlock | CanvasResolution.Braille => 1

  /** Braille dot bit for sub-position `(dx, dy)`, row-major: dots 1–8 per the Unicode braille block layout.
    *
    * A flat `Array` rather than nested `Vector`s because this is indexed once per lit dot on the render thread; it is
    * private to this object and never written after initialisation, so the mutability does not escape.
    */
  private val BrailleBits: Array[Int] =
    Array(0x01, 0x08, 0x02, 0x10, 0x04, 0x20, 0x40, 0x80)

/** A sub-cell scratch surface over one [[Rect]], addressed in dot space rather than in world coordinates.
  *
  * Separate from [[Painter]] because a [[io.worxbend.tui.core.Cell]] holds one [[Style]] and the two surfaces need
  * opposite resolution rules. A canvas plots independent points, so `Painter`'s last-writer-wins is harmless. An
  * animation with a graded tail puts dots of *different* brightness in one cell, and the cell must take the brightest —
  * otherwise a dim tail dot recolours the bright head dot it happens to share a cell with, which is exactly backwards.
  *
  * Allocated per render rather than retained, so its state is two primitive arrays and no boxed `Array[Style]`.
  */
private[widgets] final class DotGrid(area: Rect, resolution: CanvasResolution, marker: String):

  private val (dotsAcross, dotsDown) = SubCell.dotsPerCell(resolution)

  /** The grid's extent in dots. Zero on an empty `area`, which makes every [[light]] a no-op without a second guard. */
  val dotWidth: Int  = math.max(0, area.width) * dotsAcross
  val dotHeight: Int = math.max(0, area.height) * dotsDown

  val columnAspect: Int = SubCell.columnAspect(resolution)

  /** The dot a centred figure is pinned to.
    *
    * Rounded down rather than to the nearest half dot so the centre is an integer dot at every size: a figure that
    * moved half a dot when its area grew by one row would shift every golden frame under a resize.
    */
  val centreColumn: Int = (dotWidth - 1) / 2
  val centreRow: Int    = (dotHeight - 1) / 2

  private val masks       = new Array[Int](area.area)
  private val intensities = new Array[Double](area.area)

  /** A two-column marker would smear a grid the way it cannot smear a scatter plot, so it is refused here. */
  private val glyphMarker = if CharWidth.of(marker) == 1 then marker else DotGrid.FallbackMarker

  /** Lights dot `(col, row)` at `intensity` in `[0, 1]`. Coordinates off the grid are dropped rather than wrapped or
    * clamped, so a caller doing its own centring arithmetic cannot smear the edge. Masks accumulate; the cell keeps the
    * highest intensity it was given.
    */
  def light(col: Int, row: Int, intensity: Double): Unit =
    if col >= 0 && col < dotWidth && row >= 0 && row < dotHeight then
      val index = (row / dotsDown) * area.width + (col / dotsAcross)
      masks(index) |= SubCell.bitFor(resolution, col % dotsAcross, row % dotsDown)
      if intensity > intensities(index) then intensities(index) = intensity

  /** Writes every cell holding at least one dot, coloured `styleFor(cellIntensity)`. Cells with no dots are left
    * untouched, so the figure composes over whatever is beneath it under `layers` — the same rule [[Canvas]] follows.
    */
  def flush(buffer: Buffer, styleFor: Double => Style): Unit =
    var index = 0
    while index < masks.length do
      val mask = masks(index)
      if mask != 0 then
        val x = area.x + index % area.width
        val y = area.y + index / area.width
        buffer.set(x, y, Cell(SubCell.glyphFor(resolution, mask, glyphMarker), styleFor(intensities(index))))
      index += 1

private[widgets] object DotGrid:
  /** Stands in for a marker that is not exactly one column wide. */
  val FallbackMarker: String = "•"
