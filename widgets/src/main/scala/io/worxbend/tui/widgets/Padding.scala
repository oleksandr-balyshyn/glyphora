package io.worxbend.tui.widgets

/** Blank cells reserved on each side of a widget's content, in terminal cells.
  *
  * Per-side rather than one number because the two axes are not interchangeable in a terminal: a cell is roughly twice
  * as tall as it is wide, and a screen is 24 rows against 80 columns, so one row of vertical padding costs about four
  * times as much of the display as one column of horizontal padding does. Negative counts are treated as zero by every
  * consumer, the same way [[io.worxbend.tui.core.Layout]] treats a negative `spacing`.
  */
final case class Padding(left: Int, right: Int, top: Int, bottom: Int):

  /** The columns this padding removes from a content area. */
  def horizontalCells: Int = math.max(0, left) + math.max(0, right)

  /** The rows this padding removes from a content area. */
  def verticalCells: Int = math.max(0, top) + math.max(0, bottom)

object Padding:

  /** No padding at all — the default everywhere, so adding the parameter changed no existing rendering. */
  val zero: Padding = Padding(0, 0, 0, 0)

  /** `n` cells on all four sides. Looks lopsided in a terminal — see [[proportional]] for the usual intent. */
  def uniform(n: Int): Padding = Padding(n, n, n, n)

  /** `n` columns left and right, nothing top or bottom. */
  def horizontal(n: Int): Padding = Padding(n, n, 0, 0)

  /** `n` rows top and bottom, nothing left or right. */
  def vertical(n: Int): Padding = Padding(0, 0, n, n)

  /** `x` columns left and right, `y` rows top and bottom. */
  def symmetric(x: Int, y: Int): Padding = Padding(x, x, y, y)

  /** Padding that *looks* equal on both axes: `n` rows top and bottom, `2 * n` columns left and right.
    *
    * A terminal cell is about twice as tall as it is wide, so a gap of one column and a gap of one row are not the same
    * size on screen — the row is roughly double. Doubling the horizontal count cancels that out, which is why this, and
    * not [[uniform]], is what the DSL's `.padding(cells)` builder reaches for.
    *
    * Example: `Padding.proportional(1)` is one blank row above and below the content and two blank columns either side
    * — on a typical font that reads as an even one-character margin all the way round.
    */
  def proportional(n: Int): Padding = symmetric(2 * n, n)
