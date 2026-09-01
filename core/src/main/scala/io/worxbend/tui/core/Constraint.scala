package io.worxbend.tui.core

/** How much of a split axis one segment claims (see [[Layout.split]]).
  *
  * `Length`/`Percentage`/`Ratio` are fixed demands; `Min` is a floor that also competes for leftover space; `Max` is a
  * cap that only takes leftover space; `Fill` divides leftover space by weight.
  */
enum Constraint:
  case Length(cells: Int)
  case Percentage(pct: Int)
  case Ratio(numerator: Int, denominator: Int)
  case Min(cells: Int)
  case Max(cells: Int)
  case Fill(weight: Int = 1)

  /** What this one constraint makes of an axis `available` cells long, with no other constraint competing for it.
    *
    * This is the cheap single-segment answer — a popup's width, a sidebar's height, a measurement pass — and it is
    * deliberately not the solver. [[LayoutSolver.solve]] classifies every segment's demand, shares the leftover space
    * between the segments that can grow, and hands back the cells lost to integer division; with a single segment the
    * two agree, but `sizeIn` states the rule directly instead of asking the reader to trace three passes.
    *
    * `Length`, `Percentage`, `Ratio`, `Max` and `Fill` never answer more than `available`. `Min` is a floor, so it is
    * the one case that can: `Min(10).sizeIn(5)` is 10, and the caller decides whether to scroll, clip, or grow the
    * container. A negative `available` is read as zero.
    *
    * Two deliberate differences from ratatui, the Rust toolkit these constraints follow. Its `Fill(n)` reuses the
    * weight as a length; here `Fill.weight` is only a share of leftover space and means nothing on its own, so a lone
    * `Fill` takes the whole axis whatever its weight. And the arithmetic stays in whole cells rather than floating
    * point, matching `LayoutSolver`, so `sizeIn` and a one-element `solve` cannot disagree by a rounded cell.
    */
  def sizeIn(available: Int): Int =
    val axis = math.max(0, available)
    this match
      case Length(cells)   => math.min(axis, math.max(0, cells))
      case Percentage(pct) => math.min(axis, axis * math.max(0, pct) / 100)
      case Ratio(num, den) => if den <= 0 then 0 else math.min(axis, axis * math.max(0, num) / den)
      case Min(cells)      => math.max(axis, math.max(0, cells))
      case Max(cells)      => math.min(axis, math.max(0, cells))
      case Fill(_)         => axis

object Constraint:
  def fill: Constraint = Fill(1)

  /** One [[Length]] per cell count, in the order given: `Constraint.lengths(3, 10, 3)`. */
  def lengths(cells: Int*): Seq[Constraint] = cells.map(Length.apply)

  /** One [[Percentage]] per percentage, in the order given: `Constraint.percentages(25, 50, 25)`. */
  def percentages(pcts: Int*): Seq[Constraint] = pcts.map(Percentage.apply)

  /** One [[Ratio]] per `(numerator, denominator)` pair: `Constraint.ratios(1 -> 3, 2 -> 3)`.
    *
    * Pairs rather than a flat list of numbers, because a flat list lets a caller pass an odd number of arguments and
    * only find out at run time.
    */
  def ratios(fractions: (Int, Int)*): Seq[Constraint] = fractions.map((num, den) => Ratio(num, den))

  /** One [[Min]] floor per cell count: `Constraint.mins(10, 10)`. */
  def mins(cells: Int*): Seq[Constraint] = cells.map(Min.apply)

  /** One [[Max]] cap per cell count: `Constraint.maxes(20, 20)`. */
  def maxes(cells: Int*): Seq[Constraint] = cells.map(Max.apply)

  /** One [[Fill]] per weight, so `Constraint.fills(1, 2, 1)` splits the leftover space 1:2:1. */
  def fills(weights: Int*): Seq[Constraint] = weights.map(Fill.apply)
