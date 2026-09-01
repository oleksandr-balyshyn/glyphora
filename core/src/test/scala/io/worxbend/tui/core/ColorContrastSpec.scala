package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** Covers the WCAG luminance and contrast arithmetic.
  *
  * The numbers here are '''nominal''': for the sixteen named ANSI colours, the RGB a terminal actually paints is chosen
  * by the terminal emulator, so these results describe the palette this library assumes rather than what any particular
  * terminal shows. That is still the useful thing to test, because a theme definition is written against the same
  * assumed palette.
  */
final class ColorContrastSpec extends AnyFunSuite:

  /** WCAG's own anchors are exact, so the tolerance only has to absorb floating-point noise. */
  private val Tolerance: Double = 1e-9

  private def isClose(a: Double, b: Double, tolerance: Double = Tolerance): Boolean = math.abs(a - b) <= tolerance

  /** The seventeen singleton colors — the sixteen ANSI names plus `Reset`. `Color` is an enum with non-singleton cases
    * (`Rgb` and `Indexed` take arguments), so it has no generated `values` array to iterate.
    */
  private val NamedColors: Seq[Color] = Seq(
    Color.Reset,
    Color.Black,
    Color.Red,
    Color.Green,
    Color.Yellow,
    Color.Blue,
    Color.Magenta,
    Color.Cyan,
    Color.White,
    Color.BrightBlack,
    Color.BrightRed,
    Color.BrightGreen,
    Color.BrightYellow,
    Color.BrightBlue,
    Color.BrightMagenta,
    Color.BrightCyan,
    Color.BrightWhite,
  )

  test("luminance runs from zero at black to one at white"):
    assert(isClose(Color.luminance(Color.Black), 0.0))
    assert(isClose(Color.luminance(Color.BrightWhite), 1.0))

  test("green carries most of the perceived brightness and blue almost none"):
    // the uneven WCAG channel weights are the whole reason luminance is not the average of the three channels
    val green = Color.luminance(Color.Rgb(0, 255, 0))
    val red   = Color.luminance(Color.Rgb(255, 0, 0))
    val blue  = Color.luminance(Color.Rgb(0, 0, 255))
    assert(isClose(green, 0.7152))
    assert(isClose(red, 0.2126))
    assert(isClose(blue, 0.0722))
    assert(green > red && red > blue)

  test("black against white is the maximum contrast ratio of 21"):
    assert(isClose(Color.contrastRatio(Color.Black, Color.BrightWhite), 21.0, 1e-6))

  test("a color against itself is the minimum ratio of 1"):
    val samples = NamedColors ++ Seq(Color.Rgb(17, 200, 40), Color.Indexed(3), Color.Indexed(120), Color.Indexed(250))
    for color <- samples do assert(isClose(Color.contrastRatio(color, color), 1.0), color.toString)

  test("contrast is symmetric in its arguments"):
    val pairs = Seq(
      (Color.Red, Color.Black),
      (Color.BrightYellow, Color.Blue),
      (Color.Rgb(1, 2, 3), Color.Indexed(200)),
      (Color.Reset, Color.White),
    )
    for (a, b) <- pairs do assert(isClose(Color.contrastRatio(a, b), Color.contrastRatio(b, a)), s"$a vs $b")

  test("every pair of colors lands inside the 1..21 range"):
    val samples = NamedColors ++ Seq(Color.Rgb(0, 0, 0), Color.Rgb(255, 255, 255), Color.Indexed(232))
    for
      a <- samples
      b <- samples
    do
      val ratio = Color.contrastRatio(a, b)
      assert(ratio >= 1.0 - Tolerance && ratio <= 21.0 + Tolerance, s"$a vs $b gave $ratio")

  test("an out-of-range Rgb literal is clamped rather than skewing the luminance"):
    // the Rgb case takes its channels unchecked; approximateRgb clamps, and luminance must go through it
    assert(isClose(Color.luminance(Color.Rgb(-10, 300, 0)), Color.luminance(Color.Rgb(0, 255, 0))))

  test("readableOn picks the label color that actually reads better"):
    assert(Color.readableOn(Color.BrightWhite) == Color.Black)
    assert(Color.readableOn(Color.Black) == Color.White)
    // and it is never the worse of the two, for any background
    for background <- NamedColors ++ Seq(Color.Rgb(128, 128, 128), Color.Indexed(88)) do
      val chosen = Color.readableOn(background)
      val other  = if chosen == Color.Black then Color.White else Color.Black
      assert(
        Color.contrastRatio(chosen, background) >= Color.contrastRatio(other, background),
        s"$chosen on $background",
      )

  test("a mid-grey background gets black text, the documented tie-breaking convention"):
    // 0.5 luminance is the crossover; the convention is that dark text wins an unknown mid-tone
    assert(Color.readableOn(Color.Rgb(119, 119, 119)) == Color.Black)

  test("readableOn clears the AA threshold for interface components on every named color"):
    // 3.0 is WCAG AA for large text and user-interface components
    for background <- NamedColors do
      assert(Color.contrastRatio(Color.readableOn(background), background) >= 3.0, background.toString)

  test("a mid-tone background cannot reach the AA normal-text threshold with black or white"):
    // this is a real limit, not a defect in readableOn: the nominal Red (205, 49, 49) sits near the crossover, so
    // the better of black and white still only reaches about 4.09 against it, short of the 4.5 AA asks for normal
    // text. A design that needs 4.5 has to change the background, which is exactly what contrastRatio is for.
    val best  = Color.contrastRatio(Color.readableOn(Color.Red), Color.Red)
    assert(best > 4.0 && best < 4.5, best.toString)
    val worst = NamedColors.map(background => Color.contrastRatio(Color.readableOn(background), background)).min
    assert(worst < 4.5)

  test("the fluent forms agree with the functions they delegate to"):
    assert(Color.Red.relativeLuminance == Color.luminance(Color.Red))
    assert(Color.Red.contrastWith(Color.Black) == Color.contrastRatio(Color.Red, Color.Black))
