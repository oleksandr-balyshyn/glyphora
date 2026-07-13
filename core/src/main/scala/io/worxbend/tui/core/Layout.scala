package io.worxbend.tui.core

/** Splits a rectangle into segments along one axis according to a list of [[Constraint]]s.
  *
  * Solving happens in two passes: fixed demands first (`Length`, `Percentage`, `Ratio`, and `Min`'s floor), then any
  * leftover space is shared among the flexible constraints (`Fill` by weight, `Min` and `Max` with weight 1, `Max`
  * additionally capped). Sizes use integer cells; distribution remainders go to the earliest segments with the largest
  * fractional share, so results are deterministic. When the fixed demands exceed the available space, trailing segments
  * are truncated (possibly to zero width) rather than failing — consistent with the library-wide silent-clipping
  * philosophy.
  */
final case class Layout(direction: Direction, constraints: Seq[Constraint], spacing: Int = 0):

  def split(area: Rect): Seq[Rect] =
    if constraints.isEmpty then Seq.empty
    else
      val total     = direction match
        case Direction.Horizontal => area.width
        case Direction.Vertical   => area.height
      val available = math.max(0, total - spacing * (constraints.size - 1))
      val sizes     = solve(available)
      positioned(area, sizes)

  private def solve(available: Int): IndexedSeq[Int] =
    val fixed    = constraints.map(fixedDemand(_, available)).toArray
    val leftover = available - fixed.sum
    if leftover <= 0 then fixed.toIndexedSeq
    else
      val extras = distributeLeftover(leftover)
      fixed.indices.map(i => fixed(i) + extras(i))

  private def fixedDemand(constraint: Constraint, available: Int): Int =
    constraint match
      case Constraint.Length(cells)   => math.max(0, cells)
      case Constraint.Percentage(pct) => available * math.max(0, pct) / 100
      case Constraint.Ratio(num, den) => if den == 0 then 0 else available * math.max(0, num) / den
      case Constraint.Min(cells)      => math.max(0, cells)
      case Constraint.Max(_)          => 0
      case Constraint.Fill(_)         => 0

  /** Shares `leftover` cells among flexible constraints by weight, honoring `Max` caps; capped residue is
    * re-distributed among the still-uncapped until nothing changes.
    */
  private def distributeLeftover(leftover: Int): IndexedSeq[Int] =
    val weights    = constraints.map {
      case Constraint.Fill(weight) => math.max(0, weight)
      case Constraint.Min(_)       => 1
      case Constraint.Max(_)       => 1
      case _                       => 0
    }.toIndexedSeq
    val caps       = constraints.map {
      case Constraint.Max(cells) => math.max(0, cells)
      case _                     => Int.MaxValue
    }.toIndexedSeq
    val granted    = Array.fill(constraints.size)(0)
    var remaining  = leftover
    var progressed = true
    while remaining > 0 && progressed do
      val open   = constraints.indices.filter(i => weights(i) > 0 && granted(i) < caps(i))
      val shares = weightedShares(remaining, open.map(weights), open.map(i => caps(i) - granted(i)))
      progressed = shares.sum > 0
      open.zip(shares).foreach { (index, share) => granted(index) += share }
      remaining -= shares.sum
    granted.toIndexedSeq

  /** Integer division of `amount` by `weights` with largest-remainder rounding (ties to the earlier index), each share
    * clamped to its `limits` entry.
    */
  private def weightedShares(amount: Int, weights: Seq[Int], limits: Seq[Int]): IndexedSeq[Int] =
    val totalWeight = weights.sum
    if totalWeight == 0 then IndexedSeq.fill(weights.size)(0)
    else
      val base        = weights.map(w => amount.toLong * w / totalWeight)
      val remainders  = weights.map(w => amount.toLong * w % totalWeight)
      var extras      = amount - base.sum
      val byRemainder = weights.indices.sortBy(i => (-remainders(i), i))
      val shares      = base.map(_.toInt).toArray
      byRemainder.foreach { index =>
        if extras > 0 then
          shares(index) += 1
          extras -= 1
      }
      weights.indices.map(i => math.min(shares(i), limits(i)))

  private def positioned(area: Rect, sizes: IndexedSeq[Int]): Seq[Rect] =
    val (axisStart, axisEnd) = direction match
      case Direction.Horizontal => (area.x, area.right)
      case Direction.Vertical   => (area.y, area.bottom)
    var offset               = axisStart
    sizes.map { size =>
      val start       = math.min(offset, axisEnd)
      val clampedSize = math.max(0, math.min(size, axisEnd - start))
      offset = start + size + spacing
      direction match
        case Direction.Horizontal => Rect(start, area.y, clampedSize, area.height)
        case Direction.Vertical   => Rect(area.x, start, area.width, clampedSize)
    }

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
