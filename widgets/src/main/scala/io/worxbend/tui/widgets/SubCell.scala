package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, Style}

/** Sub-cell bit layouts and glyphs, in one place.
  *
  * Extracted from [[Painter]] rather than copied because [[Painter]] and [[DotGrid]] draw the same dots under different
  * *styling* rules, and two divergent copies of the braille bit table is the one outcome worth designing against.
  */
private[widgets] object SubCell:

  /** How many dots one cell contributes, as `(across, down)`. */
  def dotsPerCell(resolution: CanvasResolution): (Int, Int) =
    resolution match
      case CanvasResolution.Cell      => (1, 1)
      case CanvasResolution.HalfBlock => (1, 2)
      case CanvasResolution.Quadrant  => (2, 2)
      case CanvasResolution.Sextant   => (2, 3)
      case CanvasResolution.Braille   => (2, 4)
      case CanvasResolution.Octant    => (2, 4)

  /** The bit sub-position `(dx, dy)` contributes to a cell's mask.
    *
    * Every resolution except braille numbers its dots row-major from the top left — bit `dx + across * dy` — which is
    * the order the block-drawing glyph tables below are written in. Braille is the exception because the Unicode
    * braille block numbers its dots down the left column first and then down the right, a layout inherited from six-dot
    * braille cells rather than chosen for graphics, so it needs the lookup in [[BrailleBits]].
    */
  def bitFor(resolution: CanvasResolution, dx: Int, dy: Int): Int =
    resolution match
      case CanvasResolution.Braille => BrailleBits(dy * 2 + dx)
      case other                    =>
        val (across, _) = dotsPerCell(other)
        1 << (dx + across * dy)

  /** The glyph for a dot mask; `marker` is used only at [[CanvasResolution.Cell]], where there is one dot per cell.
    *
    * Each block-drawing table is indexed by the mask itself, so index 0 is the blank a surface never asks for (a cell
    * with no dots is left untouched, so whatever is beneath it shows through) and the last index is the full block.
    */
  def glyphFor(resolution: CanvasResolution, mask: Int, marker: String): String =
    resolution match
      case CanvasResolution.Cell      => marker
      case CanvasResolution.HalfBlock =>
        mask match
          case 1 => UpperHalfBlock
          case 2 => LowerHalfBlock
          case _ => FullBlock
      case CanvasResolution.Quadrant  => Quadrants(mask)
      case CanvasResolution.Sextant   => Sextants(mask)
      case CanvasResolution.Braille   => (BrailleBase + mask).toChar.toString
      case CanvasResolution.Octant    => Octants(mask)

  /** How many independently *coloured* sub-pixels one cell holds: 2 at half-block resolution, 1 everywhere else.
    *
    * This is the whole reason to pick half blocks over a finer resolution. A terminal cell carries one foreground and
    * one background colour, and `▀` paints its top half in the foreground and its bottom half in the background — so a
    * half-block cell is genuinely two coloured pixels, where a braille cell with eight dots still has only one colour
    * for all eight of them. Two points stacked in one cell used to collapse to whichever was drawn second.
    */
  def slotsPerCell(resolution: CanvasResolution): Int =
    resolution match
      case CanvasResolution.HalfBlock => 2
      case _                          => 1

  /** The cell a lit half-block pair renders as, given the style each half was drawn in.
    *
    * Three shapes come out of it. One half lit is that half's own block (`▀` or `▄`) in that half's style, leaving the
    * background alone so whatever is beneath still shows through. Both lit in the same colour is a solid `█`. Both lit
    * in *different* colours is `▀` with the lower half's foreground moved into the background, which is the trick that
    * fits two colours into one cell.
    *
    * A lower half with no foreground colour of its own has nothing to move into the background, so it falls back to the
    * solid block rather than inventing a colour for it.
    */
  def halfBlockCell(mask: Int, upper: Style, lower: Style): Cell =
    mask match
      case 1 => Cell(UpperHalfBlock, upper)
      case 2 => Cell(LowerHalfBlock, lower)
      case _ =>
        lower.fg match
          case Some(colour) if !upper.fg.contains(colour) => Cell(UpperHalfBlock, upper.withBg(colour))
          case _                                          => Cell(FullBlock, upper)

  /** Stands in for a marker that is not exactly one column wide. */
  val FallbackMarker: String = Marker.Dot

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
    * This is arithmetic rather than a fudge. A terminal cell is about twice as tall as it is wide, so how square a
    * *dot* is depends on how the packing divides that: a dot is `across / down` times as wide as the cell is tall,
    * halved. Braille packs 2 across and 4 down, half blocks 1 and 2, octants 2 and 4 — in each of those the two factors
    * cancel and a dot comes out square, needing no correction. A 2×2 quadrant divides both sides equally, so a quadrant
    * dot keeps the cell's own 1:2 shape and needs the same correction a whole cell does. A 2×3 sextant lands a third of
    * the way between, and since the correction has to be a whole number of dot columns, `1` is the honest answer for
    * it.
    */
  def columnAspect(resolution: CanvasResolution): Int =
    resolution match
      case CanvasResolution.Cell | CanvasResolution.Quadrant => 2
      case _                                                 => 1

  /** Braille dot bit for sub-position `(dx, dy)`, row-major: dots 1–8 per the Unicode braille block layout.
    *
    * A flat `Array` rather than nested `Vector`s because this is indexed once per lit dot on the render thread; it is
    * private to this object and never written after initialisation, so the mutability does not escape.
    */
  private val BrailleBits: Array[Int] =
    Array(0x01, 0x08, 0x02, 0x10, 0x04, 0x20, 0x40, 0x80)

  /** The first code point of the Unicode braille block. Every braille glyph is this plus its eight-bit dot mask, which
    * is the whole reason braille needs no glyph table where the block-drawing resolutions do.
    */
  private val BrailleBase: Int = 0x2800

  private val UpperHalfBlock: String = "▀"
  private val LowerHalfBlock: String = "▄"
  private val FullBlock: String      = "█"

  /** Splits a packed glyph table into one string per code point.
    *
    * The tables are written as string literals because that is the only form in which a reader can check them against
    * the Unicode charts by eye. They cannot be *indexed* as strings, though: the sextant and octant glyphs live outside
    * the Basic Multilingual Plane, so in Java's UTF-16 each of them occupies two `Char`s and character index `n` is not
    * glyph `n`. Walking code points once, at class-initialisation time, gives an array where it is.
    */
  private def unpack(packed: String): Array[String] =
    packed.codePoints.toArray.map(codePoint => new String(Character.toChars(codePoint)))

  /** Quadrant blocks, indexed by a 2×2 row-major mask: bit 1 top-left, 2 top-right, 4 bottom-left, 8 bottom-right. */
  private val Quadrants: Array[String] = unpack(" ▘▝▀▖▌▞▛▗▚▐▜▄▙▟█")

  /** Sextant blocks (`U+1FB00`–`U+1FB3B`, Unicode 13's Symbols for Legacy Computing), by a 2×3 row-major mask.
    *
    * Two of the sixty-four patterns are not in that range: the left half (`0b010101`) and the right half (`0b101010`)
    * already existed as `▌` and `▐`, so Unicode did not encode them a second time. Writing the table out in full rather
    * than computing an offset around those two holes is what makes it checkable against the charts.
    */
  private val Sextants: Array[String] = unpack(
    " 🬀🬁🬂🬃🬄🬅🬆🬇🬈🬉🬊🬋🬌🬍🬎🬏🬐🬑🬒🬓▌🬔🬕🬖🬗🬘🬙🬚🬛🬜🬝" +
      "🬞🬟🬠🬡🬢🬣🬤🬥🬦🬧▐🬨🬩🬪🬫🬬🬭🬮🬯🬰🬱🬲🬳🬴🬵🬶🬷🬸🬹🬺🬻█"
  )

  /** Octant blocks (`U+1CD00`–`U+1CDE5`, Unicode 16's Symbols for Legacy Computing Supplement), by a 2×4 row-major
    * mask. As with the sextants, every pattern that already had a code point — the quadrants, the half blocks, the
    * eighths bars — keeps it, so the table is written out rather than derived from a base plus an offset.
    */
  private val Octants: Array[String] = unpack(
    " 𜺨𜺫🮂𜴀▘𜴁𜴂𜴃𜴄▝𜴅𜴆𜴇𜴈▀𜴉𜴊𜴋𜴌🯦𜴍𜴎𜴏𜴐𜴑𜴒𜴓𜴔𜴕𜴖𜴗" +
      "𜴘𜴙𜴚𜴛𜴜𜴝𜴞𜴟🯧𜴠𜴡𜴢𜴣𜴤𜴥𜴦𜴧𜴨𜴩𜴪𜴫𜴬𜴭𜴮𜴯𜴰𜴱𜴲𜴳𜴴𜴵🮅" +
      "𜺣𜴶𜴷𜴸𜴹𜴺𜴻𜴼𜴽𜴾𜴿𜵀𜵁𜵂𜵃𜵄▖𜵅𜵆𜵇𜵈▌𜵉𜵊𜵋𜵌▞𜵍𜵎𜵏𜵐▛" +
      "𜵑𜵒𜵓𜵔𜵕𜵖𜵗𜵘𜵙𜵚𜵛𜵜𜵝𜵞𜵟𜵠𜵡𜵢𜵣𜵤𜵥𜵦𜵧𜵨𜵩𜵪𜵫𜵬𜵭𜵮𜵯𜵰" +
      "𜺠𜵱𜵲𜵳𜵴𜵵𜵶𜵷𜵸𜵹𜵺𜵻𜵼𜵽𜵾𜵿𜶀𜶁𜶂𜶃𜶄𜶅𜶆𜶇𜶈𜶉𜶊𜶋𜶌𜶍𜶎𜶏" +
      "▗𜶐𜶑𜶒𜶓▚𜶔𜶕𜶖𜶗▐𜶘𜶙𜶚𜶛▜𜶜𜶝𜶞𜶟𜶠𜶡𜶢𜶣𜶤𜶥𜶦𜶧𜶨𜶩𜶪𜶫" +
      "▂𜶬𜶭𜶮𜶯𜶰𜶱𜶲𜶳𜶴𜶵𜶶𜶷𜶸𜶹𜶺𜶻𜶼𜶽𜶾𜶿𜷀𜷁𜷂𜷃𜷄𜷅𜷆𜷇𜷈𜷉𜷊" +
      "𜷋𜷌𜷍𜷎𜷏𜷐𜷑𜷒𜷓𜷔𜷕𜷖𜷗𜷘𜷙𜷚▄𜷛𜷜𜷝𜷞▙𜷟𜷠𜷡𜷢▟𜷣▆𜷤𜷥█"
  )

/** A scratch grid of dot masks over one [[Rect]], addressed in dots and flushed as one glyph per cell.
  *
  * Every sub-cell surface in the module does the same three things: accumulate a bit per lit dot into the mask of the
  * cell that dot falls in, keep something *per cell* to decide the cell's colour, and finally walk the masks writing
  * one glyph each. Only the middle one differs between surfaces — [[Painter]] takes the last writer's style,
  * [[DotGrid]] the brightest dot's — and one differing decision is a parameter, not a second copy of the surface.
  *
  * That decision arrives as the `styleAt` function [[flush]] takes, so the accumulator here never needs to know what
  * "brightest" means. Callers keep their own array, sized [[slotCount]] and indexed by the value [[light]] hands back —
  * one slot per cell at every resolution but half-block, where the upper and lower halves are coloured separately and
  * get a slot each.
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

  /** Colour slots one cell holds — see [[SubCell.slotsPerCell]]. Exposed so a caller keeping something *per cell*
    * (rather than per slot) can turn a slot index from [[light]] back into the cell it belongs to.
    */
  val slotsPerCell: Int = SubCell.slotsPerCell(resolution)

  /** How many colour slots a caller's per-slot array needs.
    *
    * The same as [[cellCount]] at every resolution but half-block, where it is twice that: a `▀` cell colours its top
    * half with the foreground and its bottom half with the background, so the two halves are coloured separately and
    * each needs a slot of its own.
    */
  val slotCount: Int = cellCount * slotsPerCell

  private val masks      = new Array[Int](cellCount)
  private val safeMarker = SubCell.safeMarker(marker)

  /** Lights dot `(col, row)` and returns the index of the *colour slot* it landed in, or `-1` when it is off the grid.
    *
    * Off-grid dots are dropped rather than wrapped or clamped, so a caller doing its own centring arithmetic cannot
    * smear the edge. The returned index is what the caller stores its style or intensity under; `-1` means "nothing was
    * lit", so the caller records nothing. At every resolution but half-block a cell has exactly one slot and the index
    * is the cell's own; at half-block the upper and lower halves get one slot each.
    */
  def light(col: Int, row: Int): Int =
    if col >= 0 && col < dotWidth && row >= 0 && row < dotHeight then
      val cell = (row / dotsDown) * area.width + (col / dotsAcross)
      masks(cell) |= SubCell.bitFor(resolution, col % dotsAcross, row % dotsDown)
      if slotsPerCell == 1 then cell else cell * slotsPerCell + row % dotsDown
    else -1

  /** Writes every cell holding at least one dot, styled `styleAt(slotIndex)` for the slots [[light]] handed back.
    *
    * Cells with no dots are left untouched, so the figure composes over whatever is beneath it under `layers` — the
    * rule [[Canvas]], [[DotGrid]] and [[LinearDots]] all follow.
    */
  def flush(buffer: Buffer, styleAt: Int => Style): Unit =
    var index = 0
    while index < masks.length do
      val mask = masks(index)
      if mask != 0 then
        val x    = area.x + index % area.width
        val y    = area.y + index / area.width
        val cell =
          if slotsPerCell == 2 then SubCell.halfBlockCell(mask, styleAt(index * 2), styleAt(index * 2 + 1))
          else Cell(SubCell.glyphFor(resolution, mask, safeMarker), styleAt(index))
        buffer.set(x, y, cell)
      index += 1
