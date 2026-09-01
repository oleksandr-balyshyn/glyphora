package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Style}

/** Paints world-coordinate points into terminal cells for one [[Canvas]] render, accumulating sub-pixel hits and
  * flushing them as glyphs.
  *
  * The dot accumulation and the flush are [[SubCellSurface]]'s; what is this widget's own is the world-to-dot mapping
  * and the last-writer-wins style per cell. Last-writer-wins is right here and wrong for [[DotGrid]] for the reason
  * spelled out there: a canvas plots independent points, so which of two points sharing a cell wins carries no meaning.
  *
  * Two coordinate systems meet here, and it is worth naming both. *World* coordinates are the numbers a [[Shape]]
  * speaks in — whatever `xBounds`/`yBounds` the canvas was given, with y pointing up the way a graph's y axis does.
  * *Dot* coordinates are integer positions on the sub-cell grid the surface actually lights: column 0 is the left edge,
  * row 0 is the **top** edge, and how many dots fit in a cell depends on the [[CanvasResolution]]. A shape that wants
  * to scan-convert — walk a shape dot by dot rather than guess a sample count — works in dot space, via [[getPoint]],
  * [[paintDot]], [[bounds]] and [[dotSize]].
  *
  * One painter is created per [[Canvas]] render and is used on the render thread only; it is never shared or retained.
  */
final class Painter private[widgets] (
    area: Rect,
    xBounds: (Double, Double),
    yBounds: (Double, Double),
    resolution: CanvasResolution,
    marker: String,
):

  private val surface = SubCellSurface(area, resolution, marker)
  private val styles  = Array.fill(surface.cellCount)(Style.Default)

  /** The world rectangle this painter maps, as `((xMin, xMax), (yMin, yMax))`.
    *
    * A shape that clips or scan-converts has to know what it is being drawn inside. Without it the only thing a shape
    * can do is sample its own outline in world units and hope the density matches the surface, which is how a line
    * drawn under bounds of `0.0` to `1.0` used to come out as a handful of scattered dots.
    */
  def bounds: ((Double, Double), (Double, Double)) = (xBounds, yBounds)

  /** The dot grid's extent as `(columns, rows)` — the exclusive upper bound of [[paintDot]]'s arguments. Both are `0`
    * on an empty area, which makes every paint a no-op.
    */
  def dotSize: (Int, Int) = (surface.dotWidth, surface.dotHeight)

  /** The dot `(column, row)` a world point falls in, or `None` when there is no such dot.
    *
    * `None` is returned when the point lies outside [[bounds]], when either coordinate is non-finite, when the bounds
    * are degenerate (`xMin == xMax`), or when the canvas has no room at all. Row 0 is the *top* of the area: y points
    * up in world coordinates and rows grow down, and the flip happens here so that no caller repeats it.
    *
    * Rejecting NaN and Infinity is written out rather than left implicit. It used to work by accident — every
    * comparison against NaN is false, so `x >= xMin` already dropped it — and an accident is not a contract; stated
    * this way the guard survives the next rewrite of the arithmetic around it.
    */
  def getPoint(x: Double, y: Double): Option[(Int, Int)] =
    val (xMin, xMax) = xBounds
    val (yMin, yMax) = yBounds
    val insideWorld  = x.isFinite && y.isFinite && x >= xMin && x <= xMax && y >= yMin && y <= yMax
    if insideWorld && xMax > xMin && yMax > yMin && surface.dotWidth > 0 && surface.dotHeight > 0 then
      val column = ((x - xMin) / (xMax - xMin) * (surface.dotWidth - 1)).round.toInt
      val row    = ((yMax - y) / (yMax - yMin) * (surface.dotHeight - 1)).round.toInt
      Some((column, row))
    else None

  /** Marks the sub-pixel containing the world-coordinate point; points outside the bounds, and non-finite ones, are
    * dropped. The y axis points up (world), while rows grow down (terminal) — the mapping flips it.
    */
  def paint(x: Double, y: Double, style: Style): Unit =
    getPoint(x, y).foreach((column, row) => paintDot(column, row, style))

  /** Marks dot `(column, row)` on the sub-cell grid directly, for a shape that has already done its own arithmetic.
    *
    * A dot outside the grid is dropped, never clamped and never wrapped: clamping would pile a shape's overflow up
    * against the edge and make an off-screen figure look like an on-screen one.
    */
  def paintDot(column: Int, row: Int, style: Style): Unit =
    val index = surface.light(column, row)
    if index >= 0 then styles(index) = style

  /** Draws the straight segment between two world points, lighting every dot along the way.
    *
    * Two things happen here that a caller cannot do for itself, which is why the whole segment — not merely its two
    * endpoints — is the painter's business.
    *
    * First the segment is *clipped* to the world bounds before anything is drawn. Dropping points one at a time, the
    * way [[paint]] must, throws away a line's continuity as soon as part of it leaves the window: a line running in
    * from far off-screen used to contribute only the handful of its samples that happened to land inside, instead of a
    * solid run up to the edge.
    *
    * Then the clipped segment is stepped once per *dot* rather than once per world unit. A caller choosing its own
    * sample count has to guess how many dots a world unit is worth, and the guess is wrong in both directions: under
    * bounds of `0.0` to `1.0` a full-width line came out as five scattered dots, and under bounds of `0.0` to `1e9` the
    * same arithmetic asked for billions of samples. Stepping in dot space, the cost is bounded by the size of the grid
    * and the line is solid at every scale.
    *
    * A segment with any non-finite endpoint is not drawn at all: its direction is undefined, so painting the finite end
    * would put the line somewhere it does not go.
    */
  def paintSegment(x1: Double, y1: Double, x2: Double, y2: Double, style: Style): Unit =
    if x1.isFinite && y1.isFinite && x2.isFinite && y2.isFinite then
      clipToBounds(x1, y1, x2, y2).foreach { (fromX, fromY, toX, toY) =>
        getPoint(fromX, fromY).zip(getPoint(toX, toY)).foreach { case ((c0, r0), (c1, r1)) =>
          traceDots(c0, r0, c1, r1, style)
        }
      }

  /** The part of the segment inside the world bounds, as `(x1, y1, x2, y2)`, or `None` when none of it is.
    *
    * This is the Liang-Barsky algorithm: rather than test points, it treats the segment as `start + t * delta` for `t`
    * running 0 to 1 and narrows that range against each of the four bound edges in turn. `p` is how fast the segment
    * approaches the edge and `q` how far it starts from it; `p == 0` means the segment runs parallel to that edge, so
    * it is either wholly on the inside (`q >= 0`) or wholly outside. If the range ever inverts, no part of the segment
    * is visible.
    *
    * The surviving endpoints are clamped back onto the bounds: the arithmetic is exact in principle, but
    * `start + t * delta` in floating point can land a hair past the edge it was solved for.
    */
  private def clipToBounds(x1: Double, y1: Double, x2: Double, y2: Double): Option[(Double, Double, Double, Double)] =
    val (xMin, xMax) = xBounds
    val (yMin, yMax) = yBounds
    if xMax <= xMin || yMax <= yMin then None
    else
      val dx    = x2 - x1
      val dy    = y2 - y1
      val edges = Seq((-dx, x1 - xMin), (dx, xMax - x1), (-dy, y1 - yMin), (dy, yMax - y1))
      val span  = edges.foldLeft(Option((0.0, 1.0))) { (surviving, edge) =>
        surviving.flatMap { (t0, t1) =>
          val (p, q) = edge
          if p == 0.0 then Option.when(q >= 0.0)((t0, t1))
          else
            val crossing = q / p
            if p < 0.0 then Option.when(crossing <= t1)((math.max(t0, crossing), t1))
            else Option.when(crossing >= t0)((t0, math.min(t1, crossing)))
        }
      }
      span.map { (t0, t1) =>
        (
          clampInto(x1 + dx * t0, xMin, xMax),
          clampInto(y1 + dy * t0, yMin, yMax),
          clampInto(x1 + dx * t1, xMin, xMax),
          clampInto(y1 + dy * t1, yMin, yMax),
        )
      }

  private def clampInto(value: Double, low: Double, high: Double): Double = math.min(high, math.max(low, value))

  /** Lights every dot on the grid line from `(c0, r0)` to `(c1, r1)`, endpoints included.
    *
    * Bresenham's algorithm, in integer arithmetic: `error` tracks how far the line has drifted from the dot row (or
    * column) currently being drawn, and each step moves along the longer axis, moving along the shorter one only when
    * the drift has built up to half a dot. Local `var`s are how this reads in every language it is written in, and the
    * loop runs once per lit dot on the render thread, so the mutable state is deliberate and does not escape.
    */
  private def traceDots(c0: Int, r0: Int, c1: Int, r1: Int, style: Style): Unit =
    val stepColumn   = if c0 < c1 then 1 else -1
    val stepRow      = if r0 < r1 then 1 else -1
    val columnSpan   = math.abs(c1 - c0)
    val negativeSpan = -math.abs(r1 - r0)
    var column       = c0
    var row          = r0
    var error        = columnSpan + negativeSpan
    var running      = true
    while running do
      paintDot(column, row, style)
      if column == c1 && row == r1 then running = false
      else
        val doubled = 2 * error
        if doubled >= negativeSpan then
          error += negativeSpan
          column += stepColumn
        if doubled <= columnSpan then
          error += columnSpan
          row += stepRow

  private[widgets] def flush(buffer: Buffer): Unit =
    surface.flush(buffer, styles.apply)
