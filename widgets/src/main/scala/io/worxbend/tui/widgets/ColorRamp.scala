package io.worxbend.tui.widgets

import io.worxbend.tui.core.Color

/** A color gradient sampled by a fraction in `[0, 1]` — what a progress bar uses to color its fill by how far along it
  * is.
  *
  * A named type rather than a `(Color, Color)` pair for two reasons: a tuple does not say which end is which, and two
  * stops cannot express the most common progress ramp of all. Green through amber to red is three stops, and
  * interpolating straight from green to red passes through a muddy brown rather than through amber.
  */
final case class ColorRamp(stops: Vector[Color]):
  require(stops.nonEmpty, "a color ramp needs at least one stop")

  /** The color at `fraction` along the ramp; out-of-range and `NaN` fractions clamp to an end rather than throwing. */
  def at(fraction: Double): Color =
    val clamped = Fraction.clamped(fraction)
    if stops.sizeIs == 1 then stops.head
    else
      val scaled = clamped * (stops.size - 1)
      val lower  = math.min(stops.size - 2, math.floor(scaled).toInt)
      Color.mix(stops(lower), stops(lower + 1), scaled - lower)

  /** The same ramp read from the other end. */
  def reversed: ColorRamp = ColorRamp(stops.reverse)

object ColorRamp:

  /** A two-stop ramp. */
  def apply(from: Color, to: Color): ColorRamp = ColorRamp(Vector(from, to))

  /** A three-stop ramp, so the midpoint is a color you chose rather than whatever the two ends average to. */
  def apply(from: Color, through: Color, to: Color): ColorRamp = ColorRamp(Vector(from, through, to))

  /** Green through amber to red: "filling up towards trouble". The right default for disk, memory, and quota bars — and
    * the wrong one for a download, which is why no theme applies a ramp unless asked.
    */
  val Traffic: ColorRamp = ColorRamp(Color.Green, Color.Yellow, Color.Red)

  /** Red through amber to green: "recovering". For health, coverage, and completion. */
  val Recovery: ColorRamp = Traffic.reversed

  /** A cool-to-warm ramp for load and utilisation that carries no good/bad judgement. */
  val Heat: ColorRamp = ColorRamp(Color.Blue, Color.Magenta, Color.Red)
