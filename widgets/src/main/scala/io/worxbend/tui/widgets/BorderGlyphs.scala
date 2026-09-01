package io.worxbend.tui.widgets

/** The eight glyphs a [[Block]] draws its frame from: four corners, the two vertical sides, and the two horizontal
  * sides.
  *
  * The two sides of each axis are kept apart — `verticalLeft` next to `verticalRight`, `horizontalTop` next to
  * `horizontalBottom` — because several useful frames are *not* symmetric. A half-cell border drawn with block
  * elements, for instance, has to hug the interior from whichever side it is on: its left edge is `▐` (the right half
  * of the cell) and its right edge is `▌` (the left half). A single `vertical` glyph cannot say that, so before this
  * record split the two apart those frames could not be expressed at all.
  *
  * Every glyph is one terminal column wide in all the built-in sets, so choosing a set never changes a block's
  * [[Block.inner]] geometry — only its look. A custom set is free to break that rule, but a two-column glyph in a
  * one-column border cell renders torn, so keep to single-column glyphs.
  *
  * This is a plain immutable value with no rendering state, so one instance can be shared by any number of blocks on
  * any thread.
  */
final case class BorderGlyphs(
    horizontalTop: String,
    horizontalBottom: String,
    verticalLeft: String,
    verticalRight: String,
    topLeft: String,
    topRight: String,
    bottomLeft: String,
    bottomRight: String,
)

object BorderGlyphs:

  /** A set whose two horizontal sides share one glyph and whose two vertical sides share another — the shape of every
    * classic box-drawing frame, and the short spelling for a custom one.
    *
    * `BorderGlyphs.symmetric("─", "│", "┌", "┐", "└", "┘")` is the plain box.
    */
  def symmetric(
      horizontal: String,
      vertical: String,
      topLeft: String,
      topRight: String,
      bottomLeft: String,
      bottomRight: String,
  ): BorderGlyphs =
    BorderGlyphs(horizontal, horizontal, vertical, vertical, topLeft, topRight, bottomLeft, bottomRight)

  /** A set that draws every one of its eight positions with the same glyph — how the solid and blank frames are
    * spelled.
    */
  def uniform(glyph: String): BorderGlyphs =
    BorderGlyphs(glyph, glyph, glyph, glyph, glyph, glyph, glyph, glyph)

  /** The glyphs [[BorderType]] names. */
  def of(borderType: BorderType): BorderGlyphs =
    borderType match
      case BorderType.Plain   => symmetric("─", "│", "┌", "┐", "└", "┘")
      case BorderType.Rounded => symmetric("─", "│", "╭", "╮", "╰", "╯")
      case BorderType.Double  => symmetric("═", "║", "╔", "╗", "╚", "╝")
      case BorderType.Thick   => symmetric("━", "┃", "┏", "┓", "┗", "┛")
      case BorderType.Ascii   => symmetric("-", "|", "+", "+", "+", "+")

      // The dashed sets keep the solid corners of the weight they belong to: a dashed corner glyph does not exist, and
      // the eye reads a broken run of dashes as a line only when the ends are pinned down.
      case BorderType.LightDoubleDashed    => symmetric("╌", "╎", "┌", "┐", "└", "┘")
      case BorderType.LightTripleDashed    => symmetric("┄", "┆", "┌", "┐", "└", "┘")
      case BorderType.LightQuadrupleDashed => symmetric("┈", "┊", "┌", "┐", "└", "┘")
      case BorderType.HeavyDoubleDashed    => symmetric("╍", "╏", "┏", "┓", "┗", "┛")
      case BorderType.HeavyTripleDashed    => symmetric("┅", "┇", "┏", "┓", "┗", "┛")
      case BorderType.HeavyQuadrupleDashed => symmetric("┉", "┋", "┏", "┓", "┗", "┛")

      // Quadrant sets: each cell is treated as a 2x2 grid of half-cell "pixels", so the frame can sit half a cell
      // outside the block's area (Outside) or half a cell inside it (Inside).
      case BorderType.QuadrantOutside =>
        BorderGlyphs(
          horizontalTop = "▀",
          horizontalBottom = "▄",
          verticalLeft = "▌",
          verticalRight = "▐",
          topLeft = "▛",
          topRight = "▜",
          bottomLeft = "▙",
          bottomRight = "▟",
        )
      case BorderType.QuadrantInside  =>
        BorderGlyphs(
          horizontalTop = "▄",
          horizontalBottom = "▀",
          verticalLeft = "▐",
          verticalRight = "▌",
          topLeft = "▗",
          topRight = "▖",
          bottomLeft = "▝",
          bottomRight = "▘",
        )

      // The McGugan sets, named after Will McGugan's "McGugan box" trick: a border drawn from one-eighth block
      // elements sits so close to the cell edge that the frame reads as a hairline rather than as a row of glyphs.
      case BorderType.OneEighthWide =>
        BorderGlyphs(
          horizontalTop = "▁",
          horizontalBottom = "▔",
          verticalLeft = "▏",
          verticalRight = "▕",
          topLeft = "▁",
          topRight = "▁",
          bottomLeft = "▔",
          bottomRight = "▔",
        )
      case BorderType.OneEighthTall =>
        BorderGlyphs(
          horizontalTop = "▔",
          horizontalBottom = "▁",
          verticalLeft = "▕",
          verticalRight = "▏",
          topLeft = "▕",
          topRight = "▏",
          bottomLeft = "▕",
          bottomRight = "▏",
        )

      // A terminal cell is about twice as tall as it is wide. The proportional sets exploit that: a half-height block
      // on the horizontal sides and a full block on the vertical ones make the frame look the same thickness all round.
      case BorderType.ProportionalWide =>
        BorderGlyphs(
          horizontalTop = "▄",
          horizontalBottom = "▀",
          verticalLeft = "█",
          verticalRight = "█",
          topLeft = "▄",
          topRight = "▄",
          bottomLeft = "▀",
          bottomRight = "▀",
        )
      case BorderType.ProportionalTall =>
        BorderGlyphs(
          horizontalTop = "▀",
          horizontalBottom = "▄",
          verticalLeft = "█",
          verticalRight = "█",
          topLeft = "█",
          topRight = "█",
          bottomLeft = "█",
          bottomRight = "█",
        )

      case BorderType.Full  => uniform("█")
      case BorderType.Blank => uniform(" ")
