package io.worxbend.tui.widgets

/** The module's unit of "how far along": a `Double` in `[0, 1]` standing for progress, sweep, intensity or a position
  * on a ramp.
  *
  * A named home for one rule that every widget taking such a value repeats. Callers pass raw ratios — a division whose
  * denominator can be zero, a value read straight from a config file — so the rule has to say what an out-of-range or
  * `NaN` value means, once, rather than once per widget with nothing keeping the copies in step.
  *
  * Public because it *is* the documented contract of every ratio-taking widget in the module ([[Gauge]], [[LineGauge]],
  * [[IndeterminateBar]], the ramps): "clamped to `[0, 1]`, `NaN` reads as no progress". A caller building its own ratio
  * should be able to apply the same rule the widget will, and to read what that rule is.
  */
object Fraction:

  /** `value` confined to `[0, 1]`.
    *
    * `NaN` reads as `0.0` — no progress, no sweep, the ramp's first stop — and never as a full one. That direction is
    * chosen rather than inherited: a bar that has lost its denominator should look stalled, not finished, because a
    * full bar is a claim about work that has happened.
    */
  def clamped(value: Double): Double =
    if value.isNaN then 0.0 else math.max(0.0, math.min(1.0, value))

  /** `current / total` as a fraction, with a zero or negative total reading as no progress rather than `NaN`.
    *
    * The counting form of the same rule, for the common case of "3 of 10 done". Not clamped: a `current` outside
    * `[0, total]` comes back outside `[0, 1]` and the widget's own [[clamped]] deals with it, so a caller that wants
    * the raw ratio for its own arithmetic still gets it.
    */
  def ratio(current: Int, total: Int): Double =
    if total <= 0 then 0.0 else current.toDouble / total
