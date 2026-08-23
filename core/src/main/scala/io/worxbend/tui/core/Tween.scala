package io.worxbend.tui.core

import scala.concurrent.duration.FiniteDuration

/** A value animated from `from` to `to` over `duration` with an easing curve — for animating gauge ratios, offsets, and
  * the like from `onTick` state.
  */
final case class Tween(
    from: Double,
    to: Double,
    duration: FiniteDuration,
    easing: Easing = Easing.QuadOut,
):
  def at(elapsed: FiniteDuration): Double =
    from + (to - from) * easing(Progress.normalized(elapsed, duration))

  def isDone(elapsed: FiniteDuration): Boolean =
    elapsed >= duration
