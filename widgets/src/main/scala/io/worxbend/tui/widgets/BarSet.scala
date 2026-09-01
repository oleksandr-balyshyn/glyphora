package io.worxbend.tui.widgets

import io.worxbend.tui.core.Symbols

/** The glyphs a bar or column is drawn from: one for each eighth of a cell it can be filled to, plus what to draw in
  * the cells above the fill.
  *
  * A bar chart in a terminal has no pixels to work with, so a bar that is two and a half cells tall is drawn as two
  * full cells and one half-filled one. `eighths` is the ladder that makes that possible: `eighths(n - 1)` is the glyph
  * for a cell filled to `n` eighths, from one eighth up to a whole cell. The default set uses the eight Unicode block
  * elements `▁▂▃▄▅▆▇█`, which is why a glyphora bar reads as eight times more precise than its cell height suggests.
  *
  * Substituting the ladder is what makes the charts work outside that assumption. A terminal or a font with no block
  * elements needs [[BarSet.Ascii]]; a design that wants blunt whole-cell bars with no sub-cell precision wants
  * [[BarSet.Solid]] or [[BarSet.Halves]].
  *
  * `empty` is what fills a cell the bar does not reach. `None` — the default — leaves those cells exactly as they were,
  * which is what lets a chart be drawn over an existing background; `Some(glyph)` paints them, which is what gives a
  * chart a visible track behind each bar.
  *
  * Every glyph must be a single terminal column wide: a bar is measured in whole columns, so a two-column glyph such as
  * a CJK ideograph would spill into the bar next door.
  */
final case class BarSet(eighths: Vector[String], empty: Option[String] = None):
  require(eighths.sizeIs == 8, s"a bar set needs exactly eight glyphs, one per eighth of a cell; got ${eighths.size}")

object BarSet:

  /** The eight Unicode block elements, one eighth of a cell taller each — the default, and the most precise. */
  val Eighths: BarSet = BarSet(Symbols.Block.VerticalEighths)

  /** Three levels: empty, half a cell, a whole cell. Blunter than [[Eighths]] and much easier to read at a glance on a
    * chart whose exact values are written out somewhere else.
    */
  val Halves: BarSet = BarSet(Vector(" ", "▄", "▄", "▄", "▄", "▄", "▄", "█"), Some(" "))

  /** Whole cells only: anything at all in a cell fills it completely. */
  val Solid: BarSet = BarSet(Vector.fill(8)("█"), Some(" "))

  /** Plain ASCII, for a terminal or a font with no block-element glyphs at all. */
  val Ascii: BarSet = BarSet(Vector.fill(8)("#"), Some(" "))

  /** A set built from one glyph, with an optional glyph for the cells above the fill. */
  def uniform(glyph: String, empty: Option[String] = None): BarSet =
    BarSet(Vector.fill(8)(glyph), empty)
