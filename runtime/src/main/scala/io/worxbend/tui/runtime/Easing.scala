package io.worxbend.tui.runtime

/** Progress curves for effects and tweens: map linear time `t ∈ [0, 1]` to eased progress.
  *
  * The families follow the conventional Penner set; `Back`/`Elastic`/`Bounce` overshoot or oscillate, so they can leave
  * `[0, 1]` mid-flight even though they start at 0 and end at 1.
  */
enum Easing:
  case Linear
  case QuadIn, QuadOut, QuadInOut
  case CubicIn, CubicOut, CubicInOut
  case QuartIn, QuartOut, QuartInOut
  case QuintIn, QuintOut, QuintInOut
  case SineIn, SineOut, SineInOut
  case ExpoIn, ExpoOut, ExpoInOut
  case CircIn, CircOut, CircInOut
  case BackIn, BackOut, BackInOut
  case ElasticIn, ElasticOut, ElasticInOut
  case BounceIn, BounceOut, BounceInOut

  /** Eased progress for `t`, with `t` first clamped into `[0, 1]`. */
  def apply(t: Double): Double =
    val x = math.max(0.0, math.min(1.0, t))
    this match
      case Linear       => x
      case QuadIn       => x * x
      case QuadOut      => 1 - (1 - x) * (1 - x)
      case QuadInOut    => Easing.inOut(x, p => p * p)
      case CubicIn      => x * x * x
      case CubicOut     => 1 - math.pow(1 - x, 3)
      case CubicInOut   => Easing.inOut(x, p => p * p * p)
      case QuartIn      => math.pow(x, 4)
      case QuartOut     => 1 - math.pow(1 - x, 4)
      case QuartInOut   => Easing.inOut(x, p => math.pow(p, 4))
      case QuintIn      => math.pow(x, 5)
      case QuintOut     => 1 - math.pow(1 - x, 5)
      case QuintInOut   => Easing.inOut(x, p => math.pow(p, 5))
      case SineIn       => 1 - math.cos(x * math.Pi / 2)
      case SineOut      => math.sin(x * math.Pi / 2)
      case SineInOut    => (1 - math.cos(x * math.Pi)) / 2
      case ExpoIn       => if x == 0 then 0 else math.pow(2, 10 * (x - 1))
      case ExpoOut      => if x == 1 then 1 else 1 - math.pow(2, -10 * x)
      case ExpoInOut    => Easing.inOut(x, p => if p == 0 then 0 else math.pow(2, 10 * (p - 1)))
      case CircIn       => 1 - math.sqrt(1 - x * x)
      case CircOut      => math.sqrt(1 - (x - 1) * (x - 1))
      case CircInOut    => Easing.inOut(x, p => 1 - math.sqrt(1 - p * p))
      case BackIn       => Easing.backIn(x)
      case BackOut      => 1 - Easing.backIn(1 - x)
      case BackInOut    => Easing.inOut(x, Easing.backIn)
      case ElasticIn    => 1 - Easing.elasticOut(1 - x)
      case ElasticOut   => Easing.elasticOut(x)
      case ElasticInOut => Easing.inOut(x, p => 1 - Easing.elasticOut(1 - p))
      case BounceIn     => 1 - Easing.bounceOut(1 - x)
      case BounceOut    => Easing.bounceOut(x)
      case BounceInOut  => Easing.inOut(x, p => 1 - Easing.bounceOut(1 - p))

object Easing:
  /** Mirrors an ease-in function `f` into a symmetric ease-in-out. */
  private def inOut(x: Double, f: Double => Double): Double =
    if x < 0.5 then f(2 * x) / 2 else 1 - f(2 * (1 - x)) / 2

  private val BackC1                    = 1.70158
  private def backIn(x: Double): Double = (BackC1 + 1) * x * x * x - BackC1 * x * x

  private def elasticOut(x: Double): Double =
    if x == 0 then 0.0
    else if x == 1 then 1.0
    else math.pow(2, -10 * x) * math.sin((x * 10 - 0.75) * (2 * math.Pi / 3)) + 1

  private def bounceOut(x: Double): Double =
    val n1 = 7.5625
    val d1 = 2.75
    if x < 1 / d1 then n1 * x * x
    else if x < 2 / d1 then { val y = x - 1.5 / d1; n1 * y * y + 0.75 }
    else if x < 2.5 / d1 then { val y = x - 2.25 / d1; n1 * y * y + 0.9375 }
    else { val y = x - 2.625 / d1; n1 * y * y + 0.984375 }

/** A damped-spring integrator (à la Charm's Harmonica) for physical, non-linear motion — scrolling, progress fills,
  * layout transitions.
  *
  * Unlike an [[Easing]], a spring has no fixed duration: call [[step]] each tick with the current position and velocity
  * and it eases toward `target`, overshooting or settling per `frequency` (stiffness) and `damping` (`< 1` bouncy, `1`
  * critically damped, `> 1` sluggish). Integrated semi-implicitly, stable for the usual TUI tick rates.
  */
final case class Spring(frequency: Double = 6.0, damping: Double = 0.7, deltaTime: Double = 1.0 / 60):

  /** The next `(position, velocity)` as the value eases one `deltaTime` step toward `target`. */
  def step(position: Double, velocity: Double, target: Double): (Double, Double) =
    val accel       = -frequency * frequency * (position - target) - 2 * damping * frequency * velocity
    val newVelocity = velocity + accel * deltaTime
    val newPosition = position + newVelocity * deltaTime
    (newPosition, newVelocity)

  /** Whether the value has effectively settled on `target` (within `epsilon` of it and nearly at rest). */
  def settled(position: Double, velocity: Double, target: Double, epsilon: Double = 1e-3): Boolean =
    math.abs(position - target) < epsilon && math.abs(velocity) < epsilon

/** A value animated from `from` to `to` over `duration` with an easing curve — for animating gauge ratios, offsets, and
  * the like from `onTick` state.
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
