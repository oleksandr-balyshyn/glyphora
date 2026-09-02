package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Line, Rect, Style}

/** Paints world-coordinate points into terminal cells for one [[Canvas]] render, accumulating sub-pixel hits and
  * flushing them as glyphs.
  *
  * The dot accumulation and the flush are [[SubCellSurface]]'s; what is this widget's own is the world-to-dot mapping
  * and the last-writer-wins style per cell. Last-writer-wins is right here and wrong for [[DotGrid]] for the reason
  * spelled out there: a canvas plots independent points, so which of two points sharing a cell wins carries no meaning.
  * At [[CanvasResolution.HalfBlock]] "sharing a cell" is narrower than it sounds — the upper and lower halves are
  * coloured separately, so two points stacked in one cell keep both of their colours and only two points in the *same*
  * half compete.
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

  private val surface                = SubCellSurface(area, resolution, marker)
  private val styles                 = Array.fill(surface.slotCount)(Style.Default)
  private val (dotsAcross, dotsDown) = SubCell.dotsPerCell(resolution)
  private val (xMin, xMax)           = xBounds
  private val (yMin, yMax)           = yBounds

  /** Whether the world rectangle has extent on both axes. A degenerate bound (`xMin == xMax`) has no mapping onto dots
    * at all, so every paint is dropped rather than divided by zero.
    */
  private val hasExtent: Boolean = xMax > xMin && yMax > yMin

  /** Labels recorded by [[print]], as `(cell x, cell y, line)`, written by [[flush]] once every dot is down.
    *
    * A growable buffer rather than an immutable list because a canvas may carry many labels and this is built once per
    * render on the render thread; it never escapes the painter, which itself lives for one render.
    */
  private val labels = scala.collection.mutable.ArrayBuffer.empty[(Int, Int, Line)]

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

  /** How many dots one world unit is worth on each axis, as `(acrossX, downY)`.
    *
    * This is what a shape needs when it has to choose a *sample count* — how many points to evaluate along a curve it
    * cannot rasterize exactly, such as a circle. Choosing that number from the world extent instead produces one sample
    * per world unit, which is meaningless: a circle of radius `0.4` on a normalized canvas is not smaller than one of
    * radius `400` on a canvas scaled a thousand times wider, it is exactly the same circle.
    *
    * `0.0` on an axis that has no room or no extent, which callers should read as "sample as coarsely as you like,
    * nothing will be drawn anyway".
    */
  def dotsPerWorldUnit: (Double, Double) =
    // per axis, so the guards are per axis too: hasExtent would wrongly zero both when only one is degenerate
    val (columns, rows) = dotSize
    val across          = if xMax > xMin && columns > 1 then (columns - 1) / (xMax - xMin) else 0.0
    val down            = if yMax > yMin && rows > 1 then (rows - 1) / (yMax - yMin) else 0.0
    (across, down)

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
    val insideWorld = x.isFinite && y.isFinite && x >= xMin && x <= xMax && y >= yMin && y <= yMax
    if insideWorld && hasExtent && surface.dotWidth > 0 && surface.dotHeight > 0 then
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

  /** Draws the segment `(x1, y1)-(x2, y2)` *and* fills the area between it and the horizontal line `baselineY`.
    *
    * This is what turns a line plot into an area plot, and it is a scanline rather than a stack of segments: for each
    * dot column the segment crosses, every dot from the segment down (or up) to the baseline is lit, once. Filling by
    * emitting many thin rectangles instead would either leave stripes, when the caller's step is coarser than a dot, or
    * repaint the same dots many times over when it is finer — and a caller has no way to know which, since how many
    * dots a world unit is worth is the painter's business.
    *
    * The baseline is a world y like any other, and it does not have to be inside the bounds: it is pulled onto the
    * nearest edge, so a baseline below the visible range fills all the way to the bottom of the canvas rather than
    * drawing nothing.
    *
    * The *segment* is clipped only sideways, and this is the difference from [[paintSegment]]. What is drawn here is
    * the region between the segment and the baseline, and a region can be visible when the segment bounding it is not:
    * an area plot whose values run off the top of the y range still covers the whole canvas underneath them. Clipping
    * the segment against the top and bottom edges first threw the entire fill away in that case — a chart scaled to
    * `0..10` with a value of `100` drew a blank plot instead of a full one. So each dot column takes the segment's own
    * y where it crosses that column and pulls it onto the nearest bound, exactly as the baseline is pulled: a value
    * above the range fills to the top of the canvas, one below it fills nothing above the baseline, and the part of the
    * segment that *is* in range keeps its true slope.
    *
    * Nothing is drawn if any coordinate, the baseline included, is not a finite number.
    */
  def paintFilledSegment(x1: Double, y1: Double, x2: Double, y2: Double, baselineY: Double, style: Style): Unit =
    if x1.isFinite && y1.isFinite && x2.isFinite && y2.isFinite && baselineY.isFinite then
      clipToXBounds(x1, y1, x2, y2).foreach { (fromX, fromY, toX, toY) =>
        val ends = dotColumn(fromX).zip(dotColumn(toX))
        ends.zip(nearestRow(baselineY)).foreach { case ((c0, c1), baselineRow) =>
          val firstColumn = math.min(c0, c1)
          val lastColumn  = math.max(c0, c1)
          (firstColumn to lastColumn).foreach { column =>
            val (leftY, rightY) = worldYsInColumn(fromX, fromY, toX, toY, column)
            nearestRow(leftY).zip(nearestRow(rightY)).foreach { (leftRow, rightRow) =>
              val top    = math.min(math.min(leftRow, rightRow), baselineRow)
              val bottom = math.max(math.max(leftRow, rightRow), baselineRow)
              (top to bottom).foreach(row => paintDot(column, row, style))
            }
          }
        }
      }

  /** The world y the segment has at each edge of one dot column, as `(at the left edge, at the right edge)`.
    *
    * A steep line covers several rows inside a single column. Taking only the y at the column's centre would leave the
    * fill's upper edge as a dotted staircase with holes in it, so the span is measured from one edge of the column to
    * the other — the same reason [[traceDots]] steps along the longer axis. Both edges are held inside the segment's
    * own x range, so the column at either end of the segment does not extend it past its endpoint.
    *
    * A vertical segment, and a grid only one dot wide, have no slope to evaluate: both endpoints share the one column,
    * so the answer is simply the segment's two ends.
    */
  private def worldYsInColumn(ax: Double, ay: Double, bx: Double, by: Double, column: Int): (Double, Double) =
    val columns = surface.dotWidth
    if columns <= 1 || ax == bx then (ay, by)
    else
      val worldPerColumn = (xMax - xMin) / (columns - 1)
      val lowX           = math.min(ax, bx)
      val highX          = math.max(ax, bx)
      val leftX          = clampInto(xMin + (column - 0.5) * worldPerColumn, lowX, highX)
      val rightX         = clampInto(xMin + (column + 0.5) * worldPerColumn, lowX, highX)
      val slope          = (by - ay) / (bx - ax)
      (ay + slope * (leftX - ax), ay + slope * (rightX - ax))

  /** The dot column a world x falls in, ignoring y entirely. `None` when the canvas has no dots or the x is outside the
    * horizontal bounds — the same rejection [[getPoint]] makes, which is why it is asked rather than repeated.
    */
  private def dotColumn(x: Double): Option[Int] = getPoint(x, yMin).map((column, _) => column)

  /** The dot row for a world y, with the y pulled onto the nearest bound rather than rejected for being outside.
    *
    * A fill's baseline is a direction as much as a position — "down to zero" still means "down" when zero is off the
    * bottom of the view — so clamping is right here where rejection is right in [[getPoint]].
    */
  private def nearestRow(y: Double): Option[Int] =
    getPoint(xMin, clampInto(y, yMin, yMax)).map((_, row) => row)

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
    if !hasExtent then None
    else
      val dx    = x2 - x1
      val dy    = y2 - y1
      val edges = Seq((-dx, x1 - xMin), (dx, xMax - x1), (-dy, y1 - yMin), (dy, yMax - y1))
      visibleSpan(edges).map { (t0, t1) =>
        (
          clampInto(x1 + dx * t0, xMin, xMax),
          clampInto(y1 + dy * t0, yMin, yMax),
          clampInto(x1 + dx * t1, xMin, xMax),
          clampInto(y1 + dy * t1, yMin, yMax),
        )
      }

  /** The part of the segment inside the *horizontal* bounds only, as `(x1, y1, x2, y2)`, or `None` when none of it is.
    *
    * The same Liang-Barsky narrowing as [[clipToBounds]] with the top and bottom edges left out, so the surviving `y`s
    * are the segment's real values and may sit outside the world's y range. That is what [[paintFilledSegment]] needs:
    * it clamps those `y`s per column rather than dropping the segment, because a fill under an off-the-top value is
    * still visible.
    */
  private def clipToXBounds(x1: Double, y1: Double, x2: Double, y2: Double): Option[(Double, Double, Double, Double)] =
    if !hasExtent then None
    else
      val dx = x2 - x1
      val dy = y2 - y1
      visibleSpan(Seq((-dx, x1 - xMin), (dx, xMax - x1))).map { (t0, t1) =>
        (clampInto(x1 + dx * t0, xMin, xMax), y1 + dy * t0, clampInto(x1 + dx * t1, xMin, xMax), y1 + dy * t1)
      }

  /** Narrows `0..1` — the whole segment — against each `(p, q)` edge in turn, or `None` if nothing survives. See
    * [[clipToBounds]] for what `p` and `q` mean; this is the fold itself, shared so that clipping against two edges and
    * against four cannot drift apart.
    */
  private def visibleSpan(edges: Seq[(Double, Double)]): Option[(Double, Double)] =
    edges.foldLeft(Option((0.0, 1.0))) { (surviving, edge) =>
      surviving.flatMap { (t0, t1) =>
        val (p, q) = edge
        if p == 0.0 then Option.when(q >= 0.0)((t0, t1))
        else
          val crossing = q / p
          if p < 0.0 then Option.when(crossing <= t1)((math.max(t0, crossing), t1))
          else Option.when(crossing >= t0)((t0, math.min(t1, crossing)))
      }
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

  /** Records `line` to be written with its first cell at the world point `(x, y)`.
    *
    * Text is placed at whole-*cell* granularity whatever the resolution: there is no half of a cell for a character to
    * sit in, so the label lands in the cell containing the point. A point outside the bounds, or a non-finite one, is
    * dropped exactly as [[paint]] drops it.
    *
    * Nothing is drawn until [[flush]], and labels are written *after* every dot. That ordering is the point: a shape
    * drawn later than a label cannot punch holes through the text, so a plot's annotations do not have to be added last
    * to survive.
    */
  def print(x: Double, y: Double, line: Line): Unit =
    getPoint(x, y).foreach { (column, row) =>
      labels += ((area.x + column / dotsAcross, area.y + row / dotsDown, line))
    }

  private[widgets] def flush(buffer: Buffer): Unit =
    surface.flush(buffer, styles.apply)
    // the budget is the canvas area's right edge, not the buffer's: Buffer.setString would happily let a label
    // run past the plot into whatever is drawn beside it. LineRenderer/RowCursor own the wide-cluster clipping.
    labels.foreach { (x, y, line) =>
      val _ = LineRenderer.render(buffer, x, y, line, area.right - x)
    }
