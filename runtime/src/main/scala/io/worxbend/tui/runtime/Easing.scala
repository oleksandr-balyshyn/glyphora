package io.worxbend.tui.runtime

/** Progress curves for effects and tweens: map linear time `t ∈ [0, 1]` to eased progress. */
enum Easing:
  case Linear, QuadIn, QuadOut, QuadInOut, SineIn, SineOut, SineInOut

  /** Eased progress for `t`, clamped into `[0, 1]`. */
  def apply(t: Double): Double =
    val clamped = math.max(0.0, math.min(1.0, t))
    this match
      case Easing.Linear  => clamped
      case Easing.QuadIn  => clamped * clamped
      case Easing.QuadOut => 1 - (1 - clamped) * (1 - clamped)
      case Easing.QuadInOut =>
        if clamped < 0.5 then 2 * clamped * clamped
        else 1 - 2 * (1 - clamped) * (1 - clamped)
      case Easing.SineIn    => 1 - math.cos(clamped * math.Pi / 2)
      case Easing.SineOut   => math.sin(clamped * math.Pi / 2)
      case Easing.SineInOut => (1 - math.cos(clamped * math.Pi)) / 2

/** A value animated from `from` to `to` over `duration` with an easing curve — for animating gauge ratios,
  * offsets, and the like from `onTick` state.
  */
final case class Tween(
    from: Double,
    to: Double,
    duration: scala.concurrent.duration.FiniteDuration,
    easing: Easing = Easing.QuadOut,
):
  def at(elapsed: scala.concurrent.duration.FiniteDuration): Double =
    val t = if duration.toNanos == 0 then 1.0 else elapsed.toNanos.toDouble / duration.toNanos
    from + (to - from) * easing(t)

  def isDone(elapsed: scala.concurrent.duration.FiniteDuration): Boolean =
    elapsed >= duration
