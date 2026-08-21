package io.worxbend.tui.widgets

/** The module's unit of "how far along": a `Double` in `[0, 1]` standing for progress, sweep, intensity or a position
  * on a ramp.
  *
  * A named home for one rule that every widget taking such a value repeats. Callers pass raw ratios — a division whose
  * denominator can be zero, a value read straight from a config file — so the rule has to say what an out-of-range or
  * `NaN` value means, once, rather than once per widget with nothing keeping the copies in step.
  */
private[widgets] object Fraction:

  /** `value` confined to `[0, 1]`.
    *
    * `NaN` reads as `0.0` — no progress, no sweep, the ramp's first stop — and never as a full one. That direction is
    * chosen rather than inherited: a bar that has lost its denominator should look stalled, not finished, because a
    * full bar is a claim about work that has happened.
    */
  def clamped(value: Double): Double =
    if value.isNaN then 0.0 else math.max(0.0, math.min(1.0, value))
