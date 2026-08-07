package io.worxbend.tui.widgets

import scala.concurrent.duration.FiniteDuration

/** The two ways an animation turns elapsed time into a discrete position, shared so every looping widget wraps the same
  * way — including at a negative elapsed time, which a caller subtracting two timestamps can produce.
  */
private[widgets] object Animation:

  /** Which of `steps` positions a cycle of length `period` is at after `elapsed`.
    *
    * Returns `0` for a non-positive `steps` or `period`, rather than dividing by zero: both come from caller arithmetic
    * (`area.width - segment`, a configured duration) and a render must not take the app down.
    */
  def step(elapsed: FiniteDuration, period: FiniteDuration, steps: Int): Int =
    if steps <= 0 || period.toNanos <= 0L then 0
    else
      val perStep = math.max(1L, period.toNanos / steps)
      math.floorMod(elapsed.toNanos / perStep, steps.toLong).toInt

  /** Which of `steps` positions a run at `perSecond` positions per second is at after `elapsed`. */
  def stepAtRate(elapsed: FiniteDuration, perSecond: Double, steps: Int): Int =
    if steps <= 0 || !(perSecond > 0.0) then 0
    else
      val advanced = (elapsed.toNanos / 1e9 * perSecond).toLong
      math.floorMod(advanced, steps.toLong).toInt
