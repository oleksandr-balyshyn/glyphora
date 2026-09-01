package io.worxbend.tui.core

/** Splits a rectangle into segments along one axis according to a list of [[Constraint]]s.
  *
  * `Layout` owns the positioning half of the problem: it deducts `spacing`, asks `LayoutSolver` how many cells each
  * constraint gets, then places those sizes on the axis according to [[flex]], clamping anything that would run past
  * the far edge. How the sizes themselves are decided is documented on `LayoutSolver`.
  *
  * When the fixed demands exceed the available space, trailing segments are truncated (possibly to zero width) rather
  * than failing — consistent with the library-wide silent-clipping philosophy. A negative `spacing` is treated as zero
  * for the same reason: a negative number in that field is almost always a mistake, and `LayoutSolver` already clamps
  * every constraint input it reads rather than propagating a negative.
  *
  * Overlapping segments *are* reachable, but only by asking for them by name. Set `between` to a [[Spacing.Overlap]]
  * and each segment is pulled back over the one before it so the two share that many columns or rows — which is how two
  * bordered blocks come to share one border line instead of drawing two adjacent ones. `between` takes precedence over
  * `spacing` when it is set, and `spacing` is what every layout that has not heard of `between` keeps using.
  */
final case class Layout(
    direction: Direction,
    constraints: Seq[Constraint],
    spacing: Int = 0,
    flex: Flex = Flex.Start,
    between: Option[Spacing] = None,
):

  /** [[spacing]] as the layout actually spends it, when no [[between]] was given. */
  private val gap: Int = math.max(0, spacing)

  /** How far the layout advances past the end of one segment before starting the next.
    *
    * Positive inserts a gap, negative shares cells. Every step that budgets or places a gap reads this one value, so a
    * layout cannot deduct space in one step and hand it back as an overlap in the next — which is exactly the bug the
    * negative-`spacing` clamp was written to prevent.
    */
  private val step: Int = between.map(_.signed).getOrElse(gap)

  def split(area: Rect): Seq[Rect] =
    if constraints.isEmpty then Seq.empty
    else
      val total     = axisExtent(area)
      // a negative step is an overlap, which hands the solver *more* room than the axis has, because the segments will
      // be laid down sharing cells rather than each taking their own
      val available = math.max(0, total - step * (constraints.size - 1))
      val sizes     = LayoutSolver.solve(constraints, available)
      layOutSegments(area, sizes, total)

  /** [[split]], plus the `segments.size + 1` *spacer* rectangles the layout left between and around those segments.
    *
    * `spacers(0)` is the room before the first segment, `spacers(i)` the room between segment `i - 1` and segment `i`,
    * and the last spacer the room after the final segment. Before this existed a caller who wanted to paint something
    * into a gap — a rule between two panes, the grab handle of a draggable splitter, a drop shadow — had to redo the
    * arithmetic `spacing` and [[flex]] had already done, and could get a different answer from the solver. Now the
    * solver hands the gaps back, so the two cannot disagree.
    *
    * The spacers are derived from the segments *after* they were placed and clamped, not from the intended offsets. If
    * the constraints demand more room than `area` has, the trailing segments are squeezed to nothing and the gaps
    * between them are reported as nothing too, rather than as room that is not on screen.
    *
    * A zero-extent spacer is normal and needs no special case at the call site: it is what adjacent segments with no
    * spacing produce, and every widget renders an empty rectangle as nothing. When `constraints` is empty both results
    * are empty — there are no segments, so there is nothing for a gap to sit between.
    *
    * Under a [[Spacing.Overlap]] every inner spacer is zero-extent, because overlapping segments leave no room between
    * them to report. The shared cells belong to both neighbours and are not a gap.
    *
    * @return
    *   the segments, then the spacers.
    */
  def splitWithSpacers(area: Rect): (Seq[Rect], Seq[Rect]) =
    val segments = split(area)
    if segments.isEmpty then (Seq.empty, Seq.empty) else (segments, spacersBetween(area, segments))

  /** The gaps around and between already-placed `segments`, in the order [[splitWithSpacers]] documents.
    *
    * Walks the axis: the first gap runs from the area's near edge to the first segment's start, each middle gap from
    * one segment's end to the next one's start, and the last from the final segment's end to the area's far edge. Each
    * bound is clamped into the area and each extent floored at zero, so a segment that was itself clamped cannot
    * produce a gap that starts outside `area` or has a negative width.
    */
  private def spacersBetween(area: Rect, segments: Seq[Rect]): Seq[Rect] =
    val (axisStart, axisEnd) = axisBounds(area)
    val starts               = segments.map(axisOffsetOf)
    val ends                 = segments.map(segment => axisOffsetOf(segment) + axisExtent(segment))
    (axisStart +: ends).zip(starts :+ axisEnd).map { (from, to) =>
      val begin = math.max(axisStart, math.min(from, axisEnd))
      segmentRect(area, begin, math.max(0, math.min(to, axisEnd) - begin))
    }

  /** [[split]] for a layout whose constraint count is known while writing the code: the segments come back as a tuple,
    * so `val (left, right) = layout.split2(area)` binds two names the compiler checked instead of two `apply` calls it
    * did not.
    *
    * Positional indexing into the `Seq` — `split(area)(2)` — is the shape every caller used before these existed, and
    * it fails at run time, on a terminal already switched into raw mode, where the stack trace is the last thing the
    * user sees. A short result is padded with the empty rectangle `Rect(0, 0, 0, 0)`, which every widget renders as
    * nothing, so a mismatch degrades to a missing pane rather than an exception.
    */
  def split2(area: Rect): (Rect, Rect) =
    val parts = padded(area, 2)
    (parts(0), parts(1))

  def split3(area: Rect): (Rect, Rect, Rect) =
    val parts = padded(area, 3)
    (parts(0), parts(1), parts(2))

  def split4(area: Rect): (Rect, Rect, Rect, Rect) =
    val parts = padded(area, 4)
    (parts(0), parts(1), parts(2), parts(3))

  def split5(area: Rect): (Rect, Rect, Rect, Rect, Rect) =
    val parts = padded(area, 5)
    (parts(0), parts(1), parts(2), parts(3), parts(4))

  /** [[split]]'s result grown to at least `count` rectangles with empty ones, so the tuple helpers can index safely. */
  private def padded(area: Rect, count: Int): IndexedSeq[Rect] =
    val parts = split(area).toIndexedSeq
    parts ++ IndexedSeq.fill(math.max(0, count - parts.size))(Rect(0, 0, 0, 0))

  /** How many cells `area` offers along [[direction]] — its width when horizontal, its height when vertical. The four
    * axis helpers are the only place in this file that knows which `Rect` fields the direction selects.
    */
  private def axisExtent(area: Rect): Int =
    direction match
      case Direction.Horizontal => area.width
      case Direction.Vertical   => area.height

  /** Where `area` starts along [[direction]] — its `x` when horizontal, its `y` when vertical. */
  private def axisOffsetOf(area: Rect): Int =
    direction match
      case Direction.Horizontal => area.x
      case Direction.Vertical   => area.y

  /** The first coordinate along [[direction]] and the one past the last. */
  private def axisBounds(area: Rect): (Int, Int) =
    direction match
      case Direction.Horizontal => (area.x, area.right)
      case Direction.Vertical   => (area.y, area.bottom)

  /** A segment covering `size` cells from `start` along [[direction]], spanning `area` fully on the other axis. */
  private def segmentRect(area: Rect, start: Int, size: Int): Rect =
    direction match
      case Direction.Horizontal => Rect(start, area.y, size, area.height)
      case Direction.Vertical   => Rect(area.x, start, area.width, size)

  /** Places the solved `sizes` onto the axis of `area`, applying the [[flex]] offsets and clamping any segment that
    * would run past the far edge.
    */
  private def layOutSegments(area: Rect, sizes: IndexedSeq[Int], total: Int): Seq[Rect] =
    val (axisStart, axisEnd) = axisBounds(area)
    val (leading, gaps)      = flexOffsets(sizes, total)
    var offset               = axisStart + leading
    sizes.indices.map { i =>
      val size        = sizes(i)
      // clamped at both ends: an overlap deeper than the previous segment must not walk the cursor back out of the area
      val start       = math.max(axisStart, math.min(offset, axisEnd))
      val clampedSize = math.max(0, math.min(size, axisEnd - start))
      val gapAfter    = if i < sizes.size - 1 then gaps(i) else 0
      offset = start + size + gapAfter
      segmentRect(area, start, clampedSize)
    }

  /** The leading offset and the inter-segment gaps (each already including the base `spacing`) that realize the current
    * [[flex]] mode, given the solved `sizes` and the axis `total`. `free` is the space the segments and base spacing
    * leave over — zero when a `Fill`/`Min` constraint already consumed everything, which makes every mode collapse to
    * `Start`.
    */
  private def flexOffsets(sizes: IndexedSeq[Int], total: Int): (Int, IndexedSeq[Int]) =
    val segmentCount = sizes.size
    val baseGaps     = step * math.max(0, segmentCount - 1)
    val free         = math.max(0, total - sizes.sum - baseGaps)
    val betweens     = IndexedSeq.fill(math.max(0, segmentCount - 1))(step)
    if free == 0 then (0, betweens)
    else
      flex match
        case Flex.Start        => (0, betweens)
        case Flex.End          => (free, betweens)
        case Flex.Center       => (free / 2, betweens)
        case Flex.SpaceBetween =>
          if segmentCount <= 1 then (0, betweens)
          else (0, addSpacing(evenSplit(free, segmentCount - 1)))
        case Flex.SpaceEvenly  =>
          val slots = evenSplit(free, segmentCount + 1)
          (slots(0), addSpacing((1 until segmentCount).map(i => slots(i)).toIndexedSeq))
        case Flex.SpaceAround  =>
          val halves = evenSplit(free, 2 * segmentCount)
          val gaps   = (1 until segmentCount).map(i => halves(2 * i - 1) + halves(2 * i)).toIndexedSeq
          (halves(0), addSpacing(gaps))

  /** Splits `total` cells into `parts` as evenly as possible, giving the remainder to the earliest slots. */
  private def evenSplit(total: Int, parts: Int): IndexedSeq[Int] =
    if parts <= 0 then IndexedSeq.empty
    else
      val base  = total / parts
      // all keys equal, so [[LayoutSolver.distributeRemainder]] falls back to index order: the earliest slots get the spare cells
      val extra = LayoutSolver.distributeRemainder(total % parts, IndexedSeq.fill(parts)(0.0))
      (0 until parts).map(i => base + extra(i)).toIndexedSeq

  private def addSpacing(gaps: IndexedSeq[Int]): IndexedSeq[Int] = gaps.map(_ + step)

  /** This layout with `spacing` replaced by an explicit [[Spacing]], so `Layout.horizontal(4, 4).spaced(Overlap(1))`
    * reads at the call site as what it does.
    */
  def spaced(spacing: Spacing): Layout = copy(between = Some(spacing))

object Layout:
  /** Constraint shorthand: a plain `Int` means `Length(cells)`, a `Double` means a fraction of the whole (`0.5` →
    * `Percentage(50)`, truncating — use `Constraint.Ratio` when exact thirds matter), and any `Constraint` passes
    * through. A union-typed overload rather than implicit `Conversion`s so call sites need no language import.
    */
  def apply(direction: Direction)(constraints: (Int | Double | Constraint)*): Layout =
    Layout(
      direction,
      constraints.map {
        case cells: Int             => Constraint.Length(cells)
        case fraction: Double       => Constraint.Percentage((fraction * 100).toInt)
        case constraint: Constraint => constraint
      },
    )

  def horizontal(constraints: (Int | Double | Constraint)*): Layout =
    apply(Direction.Horizontal)(constraints*)

  def vertical(constraints: (Int | Double | Constraint)*): Layout =
    apply(Direction.Vertical)(constraints*)
