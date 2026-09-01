package io.worxbend.tui.widgets

/** Which sides of a [[Block]] draw a border, packed into an `Int` bitset (same pattern as core `Modifiers`). */
opaque type Borders = Int

object Borders:
  val None: Borders   = 0
  val Top: Borders    = 1 << 0
  val Right: Borders  = 1 << 1
  val Bottom: Borders = 1 << 2
  val Left: Borders   = 1 << 3

  /** The two side edges — `Left | Right`. Named for the direction the line is *drawn* in: a vertical border is the one
    * running down the left or right edge of the block.
    */
  val Vertical: Borders = Left | Right

  /** The two end edges — `Top | Bottom`. Named for the direction the line is *drawn* in: a horizontal border is the one
    * running across the top or the bottom of the block.
    */
  val Horizontal: Borders = Top | Bottom

  val All: Borders = Horizontal | Vertical

  extension (b: Borders)
    def |(other: Borders): Borders = (b: Int) | (other: Int)

    /** The sides present in both sets, so `Borders.All & Borders.Horizontal` is `Borders.Horizontal`. */
    def &(other: Borders): Borders = (b: Int) & (other: Int)

    /** Whether *any* side of `side` is set. With a single side — the overwhelmingly common call — that reads exactly as
      * it looks; with several ORed together it is an any-of test. Use [[hasAll]] when every side must be present.
      */
    def hasAny(side: Borders): Boolean = ((b: Int) & (side: Int)) != 0

    /** Whether *every* side of `sides` is set. `hasAll(Borders.None)` is true — no side is required. */
    def hasAll(sides: Borders): Boolean = ((b: Int) & (sides: Int)) == (sides: Int)

    def isEmpty: Boolean = (b: Int) == 0

    /** This set with every side in `sides` cleared. Before this existed, "all borders except the top" had to be spelled
      * out side by side as `Borders.Right | Borders.Bottom | Borders.Left`; it is now
      * `Borders.All.without(Borders.Top)`, which says what it means and cannot omit a side by accident.
      */
    def without(sides: Borders): Borders = (b: Int) & ~(sides: Int)

    /** The names of the set sides, in declaration order; empty when nothing is set. */
    def names: Seq[String] = Named.collect { case (side, name) if b.hasAny(side) => name }

    /** The set sides as `"Top|Left"`, or `"None"` when nothing is set — what a `toString` holding a `Borders` should
      * print instead of the raw `Int` the opaque type erases to.
      */
    def show: String = if b.isEmpty then "None" else names.mkString("|")

  /** Every single side paired with its name, in bit order. The one table both [[names]] and [[show]] read. The
    * composite aliases [[Vertical]], [[Horizontal]] and [[All]] are deliberately absent, so `Borders.All.show` prints
    * the four sides rather than a name that happens to cover them.
    */
  private val Named: Seq[(Borders, String)] = Seq(
    Top    -> "Top",
    Right  -> "Right",
    Bottom -> "Bottom",
    Left   -> "Left",
  )
