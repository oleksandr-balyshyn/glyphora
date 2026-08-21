package io.worxbend.tui.core

/** Splits a rectangle into segments along one axis according to a list of [[Constraint]]s.
  *
  * `Layout` owns the positioning half of the problem: it deducts `spacing`, asks `LayoutSolver` how many cells each
  * constraint gets, then places those sizes on the axis according to [[flex]], clamping anything that would run past
  * the far edge. How the sizes themselves are decided is documented on `LayoutSolver`.
  *
  * When the fixed demands exceed the available space, trailing segments are truncated (possibly to zero width) rather
  * than failing — consistent with the library-wide silent-clipping philosophy.
  */
final case class Layout(
    direction: Direction,
    constraints: Seq[Constraint],
    spacing: Int = 0,
    flex: Flex = Flex.Start,
):

  def split(area: Rect): Seq[Rect] =
    if constraints.isEmpty then Seq.empty
    else
      val total     = axisExtent(area)
      val available = math.max(0, total - spacing * (constraints.size - 1))
      val sizes     = LayoutSolver.solve(constraints, available)
      layOutSegments(area, sizes, total)

  /** How many cells `area` offers along [[direction]] — its width when horizontal, its height when vertical. The three
    * axis helpers are the only place in this file that knows which `Rect` fields the direction selects.
    */
  private def axisExtent(area: Rect): Int =
    direction match
      case Direction.Horizontal => area.width
      case Direction.Vertical   => area.height

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
      val start       = math.min(offset, axisEnd)
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
    val baseGaps     = spacing * math.max(0, segmentCount - 1)
    val free         = math.max(0, total - sizes.sum - baseGaps)
    val betweens     = IndexedSeq.fill(math.max(0, segmentCount - 1))(spacing)
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

  private def addSpacing(gaps: IndexedSeq[Int]): IndexedSeq[Int] = gaps.map(_ + spacing)

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
