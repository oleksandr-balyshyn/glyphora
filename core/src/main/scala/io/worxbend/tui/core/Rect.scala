package io.worxbend.tui.core

/** A rectangular region of the terminal in absolute coordinates.
  *
  * `x`/`y` locate the top-left corner; the region spans `width` columns and `height` rows. The right and bottom edges
  * (`x + width`, `y + height`) are exclusive.
  */
final case class Rect(x: Int, y: Int, width: Int, height: Int):

  def area: Int = if isEmpty then 0 else width * height

  /** Whether this rectangle covers no cells. A negative extent counts as empty: it can only come from arithmetic
    * upstream, and the standard `if !area.isEmpty` widget guard must reject it rather than render into a region that
    * does not exist.
    */
  def isEmpty: Boolean = width <= 0 || height <= 0

  /** The top-left corner as a [[Position]]. */
  def position: Position = Position(x, y)

  /** The extent as a [[Size]], dropping where the rectangle sits. */
  def size: Size = Size(width, height)

  /** Exclusive right edge. */
  def right: Int = x + width

  /** Exclusive bottom edge. */
  def bottom: Int = y + height

  /** The overlapping region of the two rectangles; a zero-sized `Rect` when they do not overlap. */
  def intersection(other: Rect): Rect =
    val left          = math.max(x, other.x)
    val top           = math.max(y, other.y)
    val overlapRight  = math.min(right, other.right)
    val overlapBottom = math.min(bottom, other.bottom)
    if overlapRight <= left || overlapBottom <= top then Rect(left, top, 0, 0)
    else Rect(left, top, overlapRight - left, overlapBottom - top)

  /** Whether the cell at `(x, y)` lies inside this rectangle. The coordinate pair overload is the primitive: rendering
    * bounds-checks per cell, and building a [[Position]] only to compare its two fields allocates on the hottest path
    * in the toolkit.
    */
  def contains(x: Int, y: Int): Boolean =
    x >= this.x && x < right && y >= this.y && y < bottom

  /** Whether `pos` lies inside this rectangle. */
  def contains(pos: Position): Boolean = contains(pos.x, pos.y)

  /** Applies `f` to every cell coordinate in this rectangle, row-major: left to right within a row, then top to bottom.
    * Does nothing when the rectangle covers no cells, so a caller needs no `isEmpty` guard.
    *
    * This, rather than [[positions]], is the primitive a renderer should reach for. Filling or scanning a region walks
    * tens of thousands of coordinates per frame, and handing back one [[Position]] object per cell allocates on the
    * hottest path in the toolkit — the same reasoning already written on the two-argument [[contains]]. Because it is
    * an `inline def` taking an `inline` function, the compiler splices the body of `f` straight into the loop, so
    * calling it costs the same as writing the nested `while` by hand.
    */
  inline def foreachPosition(inline f: (Int, Int) => Unit): Unit =
    var row = y
    while row < bottom do
      var col = x
      while col < right do
        f(col, row)
        col += 1
      row += 1

  /** Every cell coordinate in this rectangle as [[Position]] values, row-major; empty when the rectangle covers no
    * cells.
    *
    * This allocates one `Position` per cell, so it is meant for tests, assertions and cold paths. A renderer walking a
    * region every frame should use [[foreachPosition]] instead.
    */
  def positions: Iterator[Position] =
    if isEmpty then Iterator.empty
    else Iterator.range(y, bottom).flatMap(row => Iterator.range(x, right).map(col => Position(col, row)))

  /** This rectangle shrunk by `margin` cells on every side; zero-sized when the margin exhausts it. */
  def inset(margin: Int): Rect = inset(margin, margin)

  /** This rectangle shrunk by `horizontal` cells on the left/right and `vertical` on the top/bottom (ratatui's per-axis
    * `Margin`).
    *
    * If either axis is exhausted the whole result collapses to a zero-sized rect — not just the exhausted axis — and it
    * sits at this rectangle's centre rather than at its origin. Both halves of that are deliberate. A rect with one
    * surviving axis covers no cells anyway (`isEmpty` is true as soon as one extent hits zero), so keeping the other
    * extent would only offer callers a number they cannot render into; and centring means an overlay positioned against
    * the leftover still lands where the caller was aiming instead of jumping to the top-left corner.
    *
    * Named `inset` rather than `inner` because `Block.inner(area)` in `tui-widgets` is a different computation — it
    * subtracts a block's borders and padding from an area given to it — and the two used to be one keystroke apart.
    */
  def inset(horizontal: Int, vertical: Int): Rect =
    val shrunkWidth  = math.max(0, width - 2 * horizontal)
    val shrunkHeight = math.max(0, height - 2 * vertical)
    if shrunkWidth == 0 || shrunkHeight == 0 then Rect(x + width / 2, y + height / 2, 0, 0)
    else Rect(x + horizontal, y + vertical, shrunkWidth, shrunkHeight)

  /** Moves this rectangle by `dx`/`dy` without resizing it. */
  def offset(dx: Int, dy: Int): Rect = copy(x = x + dx, y = y + dy)

  /** The one-row rectangles this rectangle is made of, top to bottom: `Rect(x, y + i, width, 1)` for each of its
    * `height` rows. Empty when this rectangle covers no cells, so a caller can iterate with no guard.
    *
    * An `Iterator` rather than a `Seq` on purpose: a renderer usually walks the few visible rows of a tall area and
    * stops, and an iterator lets it `take` them without building a collection every frame. Prefer this over the
    * equivalent `Layout.vertical(Seq.fill(height)(Constraint.Length(1))*).split(this)`, which runs the whole constraint
    * solver to compute what is plain addition.
    */
  def rows: Iterator[Rect] =
    if isEmpty then Iterator.empty
    else Iterator.range(y, bottom).map(rowY => Rect(x, rowY, width, 1))

  /** The one-column rectangles this rectangle is made of, left to right: `Rect(x + i, y, 1, height)` for each of its
    * `width` columns. Empty when this rectangle covers no cells.
    *
    * A column here is one terminal *cell* wide, not one grapheme wide: a double-width cluster such as `世` occupies two
    * of these. Anything that needs display width still goes through [[CharWidth]].
    */
  def columns: Iterator[Rect] =
    if isEmpty then Iterator.empty
    else Iterator.range(x, right).map(colX => Rect(colX, y, 1, height))

  /** A `w`×`h` rectangle centered inside this one, clamped so it never exceeds these bounds. */
  def centered(w: Int, h: Int): Rect =
    val cw = math.min(w, width)
    val ch = math.min(h, height)
    Rect(x + (width - cw) / 2, y + (height - ch) / 2, cw, ch)

  /** This rectangle shrunk to at most `container`'s extent and then *moved* so that it lies fully inside it.
    *
    * This is not [[intersection]], which crops. A 40×10 dialog whose right edge runs three columns past the screen
    * comes back from `clamp` still 40×10, slid three columns to the left; `intersection` would instead return a 37×10
    * rectangle with the last three columns of its content cut off. Sliding is what caller-positioned overlays want: a
    * tooltip anchored at the mouse, or a dropdown opened under a field near the bottom row.
    *
    * When this rectangle is larger than `container` on an axis, that axis is shrunk to the container's and pinned to
    * the container's origin — there is nowhere else for it to go. An empty `container` yields a zero-sized rectangle at
    * the container's origin, because it holds no cell to move into.
    */
  def clamp(container: Rect): Rect =
    val clampedWidth  = math.max(0, math.min(width, container.width))
    val clampedHeight = math.max(0, math.min(height, container.height))
    // the outer `max` is what pins an oversized or empty container's result to its origin instead of left of it
    val clampedX      = math.max(container.x, math.min(x, container.right - clampedWidth))
    val clampedY      = math.max(container.y, math.min(y, container.bottom - clampedHeight))
    Rect(clampedX, clampedY, clampedWidth, clampedHeight)

  /** True when the two rectangles share at least one cell.
    *
    * Defined as "the overlap is not empty" rather than as the four half-plane inequalities, so it can never disagree
    * with [[intersection]]. A zero-sized rect shares no cell with anything — including with itself — and layout
    * routinely produces them: `Layout.split` clamps a segment that runs past the far edge down to zero width.
    */
  def intersects(other: Rect): Boolean = !intersection(other).isEmpty

  /** The smallest rectangle covering both (their bounding box). */
  def union(other: Rect): Rect =
    if isEmpty then other
    else if other.isEmpty then this
    else
      val left        = math.min(x, other.x)
      val top         = math.min(y, other.y)
      val unionRight  = math.max(right, other.right)
      val unionBottom = math.max(bottom, other.bottom)
      Rect(left, top, unionRight - left, unionBottom - top)

object Rect:
  val Zero: Rect = Rect(0, 0, 0, 0)

  def apply(size: Size): Rect = Rect(0, 0, size.width, size.height)
