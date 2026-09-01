package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** Covers hue-space mixing, the counterpart to the RGB-space [[Color.mix]] and [[Color.gradient]]. */
final class ColorHueGradientSpec extends AnyFunSuite:

  private def saturationOf(color: Color): Double = Color.toHsl(color)._2
  private def hueOf(color: Color): Double        = Color.toHsl(color)._1

  test("the ends are exactly the colors given"):
    val red  = Color.Rgb(255, 0, 0)
    val cyan = Color.Rgb(0, 255, 255)
    assert(Color.mixHsl(red, cyan, 0.0) == red)
    assert(Color.mixHsl(red, cyan, 1.0) == cyan)

  test("halfway between two hues stays saturated where the RGB mix goes grey"):
    // red and cyan are opposite on the wheel, so their channels cancel: the RGB midpoint is a dead grey
    val red    = Color.Rgb(255, 0, 0)
    val cyan   = Color.Rgb(0, 255, 255)
    val rgbMid = Color.mix(red, cyan, 0.5)
    val hslMid = Color.mixHsl(red, cyan, 0.5)
    assert(saturationOf(rgbMid) < 0.05, rgbMid.toString)
    assert(saturationOf(hslMid) > 0.9, hslMid.toString)

  test("the hue travels the shorter arc"):
    // red (0) to magenta (300) is 60 degrees backwards through purple, not 300 forwards through green
    val midpoint = Color.mixHsl(Color.hsl(0, 1.0, 0.5), Color.hsl(300, 1.0, 0.5), 0.5)
    assert(math.abs(hueOf(midpoint) - 330.0) < 1.0, hueOf(midpoint).toString)

  test("mixing toward a grey keeps the hue instead of swinging through red"):
    // a grey has no hue of its own; borrowing the other end's is what stops the ramp turning red on the way
    val blue     = Color.hsl(240, 0.8, 0.5)
    val grey     = Color.Rgb(128, 128, 128)
    val midpoint = Color.mixHsl(blue, grey, 0.5)
    assert(math.abs(hueOf(midpoint) - 240.0) < 1.0, hueOf(midpoint).toString)
    assert(saturationOf(midpoint) < saturationOf(blue))

  test("an interpolation factor outside 0..1 is clamped, and NaN yields the start color"):
    val a = Color.hsl(20, 0.7, 0.5)
    val b = Color.hsl(200, 0.7, 0.5)
    assert(Color.mixHsl(a, b, -1.0) == a)
    assert(Color.mixHsl(a, b, 2.0) == b)
    assert(Color.mixHsl(a, b, Double.NaN) == a)

  test("a hue gradient yields evenly spaced stops from first to last inclusive"):
    val stops = Color.gradientHsl(Color.hsl(0, 1.0, 0.5), Color.hsl(120, 1.0, 0.5), 3)
    assert(stops.size == 3)
    assert(stops.head == Color.hsl(0, 1.0, 0.5))
    assert(stops.last == Color.hsl(120, 1.0, 0.5))
    assert(math.abs(hueOf(stops(1)) - 60.0) < 1.0)

  test("a hue gradient stays saturated across every stop where an RGB one sags"):
    val from = Color.Rgb(255, 0, 0)
    val to   = Color.Rgb(0, 255, 255)
    val hue  = Color.gradientHsl(from, to, 7)
    val rgb  = Color.gradient(from, to, 7)
    assert(hue.forall(color => saturationOf(color) > 0.9), hue.toString)
    assert(rgb.exists(color => saturationOf(color) < 0.1), rgb.toString)

  test("gradient step counts behave as the RGB gradient does"):
    assert(Color.gradientHsl(Color.Red, Color.Blue, 0).isEmpty)
    assert(Color.gradientHsl(Color.Red, Color.Blue, -3).isEmpty)
    assert(Color.gradientHsl(Color.Red, Color.Blue, 1) == Seq(Color.mixHsl(Color.Red, Color.Blue, 0)))

  test("named and indexed colors work through their palette approximation"):
    val stops = Color.gradientHsl(Color.Red, Color.Indexed(21), 4)
    assert(stops.size == 4)
    assert(stops.forall { case Color.Rgb(_, _, _) => true; case _ => false })

  test("the fluent forms agree with the functions they delegate to"):
    assert(Color.Red.mixedThroughHueWith(Color.Blue, 0.25) == Color.mixHsl(Color.Red, Color.Blue, 0.25))
    assert(Color.Red.hueGradientTo(Color.Blue, 3) == Color.gradientHsl(Color.Red, Color.Blue, 3))
