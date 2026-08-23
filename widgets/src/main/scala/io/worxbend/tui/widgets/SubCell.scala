package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, Style}

/** Sub-cell bit layouts and glyphs, in one place.
  *
  * Extracted from [[Painter]] rather than copied because [[Painter]] and [[DotGrid]] draw the same dots under different
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

  /** Stands in for a marker that is not exactly one column wide. */
  val FallbackMarker: String = "•"

  /** `marker` if it is exactly one column wide, [[FallbackMarker]] otherwise.
    *
    * A two-column marker would smear a sub-cell surface the way it cannot smear a scatter plot — the second column
    * belongs to the neighbouring cell, which the surface is also drawing into — so it is refused rather than clipped.
    * One rule for every sub-cell surface, which is why [[SubCellSurface]] applies it on the way in and no caller has to
    * remember to: changing the fallback glyph cannot leave one surface behind on the old one.
    */
  def safeMarker(marker: String): String = if CharWidth.of(marker) == 1 then marker else FallbackMarker

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

/** A scratch grid of dot masks over one [[Rect]], addressed in dots and flushed as one glyph per cell.
  *
  * Every sub-cell surface in the module does the same three things: accumulate a bit per lit dot into the mask of the
  * cell that dot falls in, keep something *per cell* to decide the cell's colour, and finally walk the masks writing
  * one glyph each. Only the middle one differs between surfaces — [[Painter]] takes the last writer's style,
  * [[DotGrid]] the brightest dot's — and one differing decision is a parameter, not a second copy of the surface.
  *
  * That decision arrives as the `styleAt` function [[flush]] takes, so the accumulator here never needs to know what
  * "brightest" means. Callers keep their own per-cell array, sized [[cellCount]] and indexed by the value [[light]]
  * hands back.
  *
  * `marker` is passed through [[SubCell.safeMarker]] on the way in, so a two-column marker cannot smear into the
  * neighbouring cell on *any* surface.
  *
  * Allocated per render rather than retained, and used on the render thread only.
  */
private[widgets] final class SubCellSurface(area: Rect, resolution: CanvasResolution, marker: String):

  private val (dotsAcross, dotsDown) = SubCell.dotsPerCell(resolution)

  /** The grid's extent in dots. Zero on an empty `area`, which makes every [[light]] a no-op without a second guard. */
  val dotWidth: Int  = math.max(0, area.width) * dotsAcross
  val dotHeight: Int = math.max(0, area.height) * dotsDown

  /** How many cells the surface covers — the size a caller's per-cell array needs to be. */
  val cellCount: Int = area.area

  private val masks      = new Array[Int](cellCount)
  private val safeMarker = SubCell.safeMarker(marker)

  /** Lights dot `(col, row)` and returns the index of the cell it landed in, or `-1` when it is off the grid.
    *
    * Off-grid dots are dropped rather than wrapped or clamped, so a caller doing its own centring arithmetic cannot
    * smear the edge. The returned index is what the caller stores its per-cell style or intensity under; `-1` means
    * "nothing was lit", so the caller records nothing.
    */
  def light(col: Int, row: Int): Int =
    if col >= 0 && col < dotWidth && row >= 0 && row < dotHeight then
      val index = (row / dotsDown) * area.width + (col / dotsAcross)
      masks(index) |= SubCell.bitFor(resolution, col % dotsAcross, row % dotsDown)
      index
    else -1

  /** Writes every cell holding at least one dot, styled `styleAt(cellIndex)`.
    *
    * Cells with no dots are left untouched, so the figure composes over whatever is beneath it under `layers` — the
    * rule [[Canvas]], [[DotGrid]] and [[LinearDots]] all follow.
    */
  def flush(buffer: Buffer, styleAt: Int => Style): Unit =
    var index = 0
    while index < masks.length do
      val mask = masks(index)
      if mask != 0 then
        val x = area.x + index % area.width
        val y = area.y + index / area.width
        buffer.set(x, y, Cell(SubCell.glyphFor(resolution, mask, safeMarker), styleAt(index)))
      index += 1
