package io.worxbend.tui.core

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

  /** Eased progress for `t`, with `t` first clamped into `[0, 1]`; a `NaN` progress reads as the start of the curve. */
  def apply(t: Double): Double =
    // `math.max`/`math.min` propagate NaN rather than clamping it, and a NaN progress would spread into every cell
    // coordinate the effect derives from it
    val x = if t.isNaN then 0.0 else math.max(0.0, math.min(1.0, t))
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

  /** The four-segment Penner bounce (the `easeOutBounce` of easings.net): four upward parabolas of the same steepness,
    * each one starting later, narrower and higher than the last, so the value drops and rebounds three times before
    * settling on 1.
    *
    * `amplitude` is the shared steepness of those parabolas and `segments` splits `[0, 1]` into the four spans. The
    * per-segment offsets (the centre `1.5 / segments`, `2.25 / segments`, `2.625 / segments` and the floors `0.75`,
    * `0.9375`, `0.984375`) are the published constants of that curve: they are what makes each parabola touch the
    * previous one exactly where it lands. Changing one in isolation puts a visible kink in the bounce.
    */
  private def bounceOut(x: Double): Double =
    val amplitude = 7.5625
    val segments  = 2.75
    if x < 1 / segments then amplitude * x * x
    else if x < 2 / segments then { val y = x - 1.5 / segments; amplitude * y * y + 0.75 }
    else if x < 2.5 / segments then { val y = x - 2.25 / segments; amplitude * y * y + 0.9375 }
    else { val y = x - 2.625 / segments; amplitude * y * y + 0.984375 }
