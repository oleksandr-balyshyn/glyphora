package io.worxbend.tui.widgets

/** The ordered dot positions making up one closed loop, addressed by how far round the loop they are.
  *
  * Rasterised once and indexed, rather than sampled parametrically from `sin`/`cos`, because the arc has to move
  * exactly one dot per step. Rounding a parametric sample lands two neighbouring steps on the same dot wherever the
  * curve runs shallow, and a repeated dot is a visible stutter; it also leaves gaps wherever the curve runs steep. A
  * rasterised walk cannot do either, and it costs integer arithmetic instead of two transcendentals per dot per frame.
  *
  * The guarantees, which the whole family is built on:
  *   - `dotAt(0)` is the top of the figure, so every path starts at twelve o'clock and `sweep`/`period` mean the same
  *     thing whichever loop is travelled;
  *   - consecutive indices are *distinct* and 8-adjacent, and `dotAt(length - 1)` is 8-adjacent to `dotAt(0)` — the arc
  *     therefore advances every step and never jumps, at any radius or aspect ratio;
  *   - the point set is symmetric about both axes and spans exactly `±halfColumns` by `±halfRows`, which is what makes
  *     [[OrbitSpinner.sizeFor]] exact rather than a bound.
  *
  * Positions are returned *packed* into one `Int` — column in the high half, row (sign-extended) in the low half —
  * because this is walked per dot per frame on the render thread and a `(Int, Int)` per dot is garbage the frame does
  * not need. Use [[RingWalk.columnOf]] and [[RingWalk.rowOf]] to unpack. Coordinates are offsets from the figure's
  * centre dot, with rows growing downward like a terminal's.
  */
private[widgets] final class RingWalk private (
    path: OrbitPath,
    halfColumns: Int,
    halfRows: Int,
    quadrant: Array[Int],
    quadrantSize: Int,
):

  /** How many dots one lap is. Always at least one, so a degenerate figure is a single pip rather than an empty loop.
    */
  val length: Int =
    path match
      case OrbitPath.Circle => if quadrantSize <= 1 then 1 else 4 * quadrantSize - 4
      case OrbitPath.Square => math.max(1, 4 * (halfColumns + halfRows))

  /** The packed dot `index` steps clockwise from twelve o'clock. Out-of-range indices wrap, because a lap is a cycle
    * and a caller computing `head - k` should not have to floor-mod twice.
    */
  def dotAt(index: Int): Int =
    val step = math.floorMod(index, length)
    path match
      case OrbitPath.Circle => circleDot(step)
      case OrbitPath.Square => squareDot(step)

  /** One quadrant of the rasterised ellipse, mirrored into the other three.
    *
    * The stored quadrant runs from the top `(0, halfRows)` to the right `(halfColumns, 0)` in *upward* y; the four
    * segments below read it forwards, backwards, forwards, backwards, each dropping the endpoint the previous segment
    * already emitted. Mirroring rather than rasterising four times is what keeps the ring exactly symmetric — two
    * independent rasterisations of the same arc can disagree by a dot, and a lopsided ring is visible at radius 4.
    */
  private def circleDot(step: Int): Int =
    val size = quadrantSize
    if size <= 1 then RingWalk.pack(0, 0)
    else if step < size then RingWalk.mirror(quadrant(step), 1, -1)
    else if step < 2 * size - 1 then RingWalk.mirror(quadrant(2 * size - 2 - step), 1, 1)
    else if step < 3 * size - 2 then RingWalk.mirror(quadrant(step - 2 * size + 2), -1, 1)
    else RingWalk.mirror(quadrant(4 * size - 4 - step), -1, -1)

  /** The box perimeter as five straight runs — right along the top from the midpoint, down, left, up, and back along
    * the top — walked without a table because every run is one axis and one sign.
    *
    * A degenerate extent needs no special case: with `halfColumns == 0` the three horizontal runs are empty and the
    * walk becomes the vertical line travelled down and back up, which is still a closed loop with no step in place.
    */
  private def squareDot(step: Int): Int =
    val a = halfColumns
    val b = halfRows
    if step == 0 then RingWalk.pack(0, -b)
    else if step <= a then RingWalk.pack(step, -b)
    else if step <= a + 2 * b then RingWalk.pack(a, -b + (step - a))
    else if step <= 3 * a + 2 * b then RingWalk.pack(a - (step - a - 2 * b), b)
    else if step <= 3 * a + 4 * b then RingWalk.pack(-a, b - (step - 3 * a - 2 * b))
    else RingWalk.pack(-a + (step - 3 * a - 4 * b), -b)

private[widgets] object RingWalk:

  /** Half-extents are clamped to this. A figure this size fits no terminal, and the bound is what lets a dot pack into
    * one `Int` without an overflow check on the render thread.
    */
  val MaxHalfExtent: Int = 8192

  /** The loop of `path` with the given half-extents, in dots either side of the centre. */
  def apply(path: OrbitPath, halfColumns: Int, halfRows: Int): RingWalk =
    val a = clamp(halfColumns)
    val b = clamp(halfRows)
    path match
      case OrbitPath.Circle =>
        val points = new Array[Int](a + b + 1)
        new RingWalk(path, a, b, points, quadrantOf(a, b, points))
      case OrbitPath.Square =>
        new RingWalk(path, a, b, EmptyQuadrant, 0)

  def columnOf(dot: Int): Int = dot >> 16

  /** Sign-extends the low half — a row above the centre is negative. */
  def rowOf(dot: Int): Int = (dot << 16) >> 16

  private def pack(column: Int, row: Int): Int = (column << 16) | (row & 0xffff)

  private def mirror(dot: Int, columnSign: Int, rowSign: Int): Int =
    pack(columnSign * columnOf(dot), rowSign * rowOf(dot))

  private def clamp(extent: Int): Int = math.max(0, math.min(MaxHalfExtent, extent))

  private val EmptyQuadrant: Array[Int] = Array.empty

  /** Rasterises the quadrant from `(0, b)` to `(a, 0)` into `points`, returning how many it wrote.
    *
    * The midpoint *ellipse* rather than the midpoint circle, because the two axes genuinely differ: at
    * [[CanvasResolution.Cell]] one dot is a whole cell, so a round figure is twice as wide in dots as it is tall, and
    * `columnAspect` has already scaled `a` by the time this is called. Region 1 walks the shallow part (x advances
    * every step), region 2 the steep part (y retreats every step); each emitted point therefore differs from the last
    * in exactly one coordinate by one, which is where the no-gaps, no-repeats guarantee comes from.
    *
    * Decision variables are held scaled by four so they stay integral, and in `Long` so a large extent cannot overflow
    * the `4 * rx² * b` term. The array is sized `a + b + 1`, which is exactly the worst case: every point after the
    * first advances x or retreats y, and neither travels further than its extent.
    */
  private def quadrantOf(a: Int, b: Int, points: Array[Int]): Int =
    var size = 0
    if a <= 0 && b <= 0 then
      points(0) = pack(0, 0)
      size = 1
    else if a <= 0 then
      var y = b
      while y >= 0 do
        points(size) = pack(0, y)
        size += 1
        y -= 1
    else if b <= 0 then
      var x = 0
      while x <= a do
        points(size) = pack(x, 0)
        size += 1
        x += 1
    else
      val rx2      = a.toLong * a.toLong
      val ry2      = b.toLong * b.toLong
      var x        = 0
      var y        = b
      var px       = 0L
      var py       = 2L * rx2 * y.toLong
      points(0) = pack(0, b)
      size = 1
      var decision = 4L * ry2 - 4L * rx2 * b.toLong + rx2
      while px < py && y > 0 && size < points.length do
        x += 1
        px += 2L * ry2
        if decision < 0 then decision += 4L * (ry2 + px)
        else
          y -= 1
          py -= 2L * rx2
          decision += 4L * (ry2 + px - py)
        points(size) = pack(math.min(x, a), y)
        size += 1
      decision = ry2 * (2L * x + 1L) * (2L * x + 1L) + 4L * rx2 * (y - 1L) * (y - 1L) - 4L * rx2 * ry2
      while y > 0 && size < points.length do
        y -= 1
        py -= 2L * rx2
        if decision > 0 then decision += 4L * (rx2 - py)
        else
          x += 1
          px += 2L * ry2
          decision += 4L * (rx2 - py + px)
        points(size) = pack(math.min(x, a), y)
        size += 1
      // An extreme aspect ratio can leave region 1 with y already at 0 and x short of the extent: finish the run flat,
      // so the quadrant always ends at (a, 0) and the mirrored ring always spans exactly ±a.
      var last     = columnOf(points(size - 1))
      while last < a && size < points.length do
        last += 1
        points(size) = pack(last, 0)
        size += 1
    size
