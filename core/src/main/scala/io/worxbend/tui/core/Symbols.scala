package io.worxbend.tui.core

/** The named glyphs the library draws with.
  *
  * These characters were previously written as bare literals wherever they were needed, which meant the same four
  * shading blocks appeared in three unrelated widgets and the eighth-block ladder existed twice — once growing upward
  * and once growing rightward — with no name connecting them. A reader had to recognise `▒` on sight to know what a
  * line of code drew, and an application that wanted to build a ramp matching a built-in widget had to copy the
  * characters out of the source.
  *
  * One vocabulary fixes both. Widgets name the glyph they mean, applications and themes can reach the same names, and a
  * variant ramp is built from the same pieces rather than retyped.
  *
  * Pure `String` constants in `tui-core`, depending on nothing, so every tier can reach them. They are `String` rather
  * than `Char` because that is what a [[Cell]] holds — a cell's content is a grapheme cluster, which can be more than
  * one `Char`.
  */
object Symbols:

  /** The shading blocks: a cell filled by roughly a quarter, a half, three quarters, or completely.
    *
    * This is the one glyph family that reads as a scale without any colour at all, which is why it turns up wherever an
    * intensity has to survive a monochrome terminal — a heat map cell, a progress track, the resting state of a loading
    * placeholder.
    */
  object Shade:

    /** Nothing drawn: the low end of [[Ramp]]. */
    val Empty: String = " "

    /** `░`, about a quarter filled. */
    val Light: String = "░"

    /** `▒`, about half filled. */
    val Medium: String = "▒"

    /** `▓`, about three quarters filled. */
    val Dark: String = "▓"

    /** `█`, the whole cell. The same character as [[Block.Full]], named here too because it is the top of this ramp.
      */
    val Full: String = "█"

    /** The five shades from empty to full, in increasing order — the ramp a heat map or an intensity scale steps
      * through. Index `n` of `Ramp` is darker than index `n - 1`, so a normalised value maps onto it by multiplying by
      * `Ramp.size - 1` and rounding.
      */
    val Ramp: Vector[String] = Vector(Empty, Light, Medium, Dark, Full)

  /** The block elements: partial fills of a cell, used wherever a bar has to land between two whole columns or rows.
    *
    * The two eighth ladders below are the same eight fractions drawn in different directions, and they are separate
    * values because the direction is not interchangeable: a vertical bar grows from the bottom of the cell upward, a
    * horizontal one from the left edge rightward, and using one where the other belongs draws a bar that fills the
    * wrong way.
    */
  object Block:

    /** `█`, a completely filled cell. */
    val Full: String = "█"

    /** `▀`, the top half of a cell. */
    val UpperHalf: String = "▀"

    /** `▄`, the bottom half of a cell. */
    val LowerHalf: String = "▄"

    /** `▌`, the left half of a cell. */
    val LeftHalf: String = "▌"

    /** `▐`, the right half of a cell. */
    val RightHalf: String = "▐"

    /** The seven partial fills growing '''upward''' from the bottom of the cell, one eighth taller each: `▁` through
      * `▇`. [[VerticalEighths]] is this ladder with [[Full]] on the end.
      */
    val VerticalPartials: Vector[String] = Vector("▁", "▂", "▃", "▄", "▅", "▆", "▇")

    /** The eight upward-growing fills, `▁` (one eighth) through `█` (the whole cell). `VerticalEighths(n - 1)` is the
      * glyph for `n` eighths, which is why a column is walked from its bottom row toward its top.
      */
    val VerticalEighths: Vector[String] = VerticalPartials :+ Full

    /** The seven partial fills growing '''rightward''' from the left edge of the cell, one eighth wider each: `▏`
      * through `▉`. [[HorizontalEighths]] is this ladder with [[Full]] on the end.
      */
    val HorizontalPartials: Vector[String] = Vector("▏", "▎", "▍", "▌", "▋", "▊", "▉")

    /** The eight rightward-growing fills, `▏` (one eighth) through `█` (the whole cell). */
    val HorizontalEighths: Vector[String] = HorizontalPartials :+ Full
