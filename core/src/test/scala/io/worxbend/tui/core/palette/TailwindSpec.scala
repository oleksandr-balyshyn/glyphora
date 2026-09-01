package io.worxbend.tui.core.palette

import io.worxbend.tui.core.Color

import org.scalatest.funsuite.AnyFunSuite

final class TailwindSpec extends AnyFunSuite:

  test("spot-checked shades match the published Tailwind hex values"):
    // the same two anchors ratatui asserts on, plus one from each end of a ramp, so a transcription slip in the
    // packed literals cannot pass unnoticed
    assert(Tailwind.Red.c500 == Color.Rgb(239, 68, 68))   // #ef4444
    assert(Tailwind.Blue.c500 == Color.Rgb(59, 130, 246)) // #3b82f6
    assert(Tailwind.Slate.c50 == Color.Rgb(248, 250, 252))
    assert(Tailwind.Slate.c950 == Color.Rgb(2, 6, 23))
    assert(Tailwind.Rose.c500 == Color.Rgb(244, 63, 94))

  test("black and white are the true extremes, not the terminal's idea of them"):
    assert(Tailwind.Black == Color.Rgb(0, 0, 0))
    assert(Tailwind.White == Color.Rgb(255, 255, 255))

  test("the palette publishes twenty-two ramps under their Tailwind names"):
    assert(Tailwind.Ramps.size == 22)
    assert(Tailwind.Ramps.map(_._1).distinct.size == 22)
    assert(Tailwind.ramp("emerald") == Some(Tailwind.Emerald))
    assert(Tailwind.ramp("Emerald").isEmpty) // the names are lower-case, as Tailwind spells them
    assert(Tailwind.ramp("chartreuse").isEmpty)

  test("every shade is a true-colour value inside the channel range"):
    // the whole point of the palette is that it does not depend on the terminal theme, so nothing here may be a
    // named or indexed colour, and no channel may have been mis-transcribed out of 0..255
    for
      (name, shades) <- Tailwind.Ramps
      color          <- shades.all
    do
      color match
        case Color.Rgb(r, g, b) =>
          assert(r >= 0 && r <= 255 && g >= 0 && g <= 255 && b >= 0 && b <= 255, s"$name shade $color")
        case other              => fail(s"$name contains a non-RGB colour: $other")

  test("all lists the eleven shades in ramp order, lightest first"):
    val shades    = Tailwind.Sky.all
    assert(shades.size == 11)
    assert(shades.head == Tailwind.Sky.c50)
    assert(shades.last == Tailwind.Sky.c950)
    // lightness falls monotonically across a ramp — that is what makes a step number mean the same thing everywhere
    val lightness = shades.map(color => Color.toHsl(color)._3)
    assert(lightness.sliding(2).forall { case Seq(a, b) => a > b; case _ => true }, lightness.toString)

  test("shade looks a colour up by its Tailwind step number"):
    assert(Tailwind.Green.shade(500) == Some(Tailwind.Green.c500))
    assert(Tailwind.Green.shade(50) == Some(Tailwind.Green.c50))
    assert(Tailwind.Green.shade(950) == Some(Tailwind.Green.c950))

  test("a step that is not in the ramp answers None rather than the nearest shade"):
    // 0, 550 and 1000 are plausible-looking mistakes; answering with a neighbour would hide them until someone
    // noticed the wrong colour on screen
    assert(Tailwind.Green.shade(0).isEmpty)
    assert(Tailwind.Green.shade(550).isEmpty)
    assert(Tailwind.Green.shade(1000).isEmpty)
    assert(Tailwind.Green.shade(-100).isEmpty)

  test("the same step is the same weight across hues"):
    // c500 is the mid-weight step everywhere, so a chart that swaps Blue.c500 for Amber.c500 keeps its balance
    val midLightness = Tailwind.Ramps.map { case (_, shades) => Color.toHsl(shades.c500)._3 }
    assert(midLightness.forall(l => l > 0.25 && l < 0.75), midLightness.toString)

  test("Steps names every step exactly once, in ramp order"):
    assert(Shades.Steps == Seq(50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950))
    assert(Shades.Steps.size == Tailwind.Slate.all.size)
