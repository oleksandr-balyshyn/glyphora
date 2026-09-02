package io.worxbend.tui.core

/** Whole-cell arithmetic for the layout, done in a width that cannot wrap around.
  *
  * A terminal axis is at most a few hundred cells, but a *constraint* is a plain `Int` the application supplies, and
  * nothing stops it being `Int.MaxValue`. `available * percentage` is then an `Int` multiplication that overflows and
  * comes back negative long before the divide by 100 can bring it back into range — which is how
  * `Constraint.Percentage(25000000).sizeIn(100)` used to answer `-17949672` instead of `100`.
  *
  * The fix is to do the multiplication in `Long`, where the largest product two `Int`s can make still fits exactly, and
  * only then bring the result back into `Int` by clamping. That is invisible from outside: every answer that was
  * already correct is unchanged, and the answers that used to wrap around now land on the nearest representable cell
  * count instead.
  *
  * Internal to the layout implementation — [[Constraint]] and [[LayoutSolver]] share it so the single-segment answer
  * and the solver cannot disagree — rather than part of what `tui-core` publishes.
  */
private[core] object CellCount:

  /** `value` brought back into `Int`, saturating at `Int.MinValue`/`Int.MaxValue` rather than wrapping around. */
  def clamp(value: Long): Int =
    math.max(Int.MinValue.toLong, math.min(Int.MaxValue.toLong, value)).toInt

  /** The whole cells `numerator / denominator` of an `axis`-cell axis comes to, rounded down.
    *
    * A non-positive `denominator` is not a fraction anyone can honor, so it claims nothing rather than dividing by
    * zero; a negative `numerator` or `axis` is read as zero, the same way every other constraint input is. The result
    * can still exceed `axis` — `Percentage(200)` genuinely asks for twice the axis — and it is each caller's own job to
    * decide whether that is capped or passed on.
    */
  def fractionOf(axis: Int, numerator: Int, denominator: Int): Int =
    if denominator <= 0 then 0
    else clamp(math.max(0, axis).toLong * math.max(0, numerator) / denominator)
