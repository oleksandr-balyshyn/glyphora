package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Color, Rect, Style}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.{DurationInt, FiniteDuration}

final class AnimatedTextSpec extends AnyFunSuite:

  private def line(effect: TextEffect, at: FiniteDuration, content: String, width: Int): String =
    trimmedLines(rendered(AnimatedText(content, at, effect), width, 1)).headOption.getOrElse("")

  private def every: Seq[TextEffect] = Seq(
    TextEffect.Typewriter(),
    TextEffect.Wave(),
    TextEffect.Gradient(ColorRamp.Traffic),
    TextEffect.Shimmer(),
    TextEffect.Bounce(),
  )

  /** Whatever the effect, the text is the widget's whole job: it must stay inside its rect and must not throw for any
    * moment or any degenerate area.
    */
  test("every effect stays inside its area at every moment"):
    every.foreach: effect =>
      Seq(0, 1, 2, 5, 20).foreach: width =>
        Seq(0, 1, 3).foreach: height =>
          (0 to 20).foreach: step =>
            val buffer = rendered(AnimatedText("hello world", (step * 137).millis, effect), width, height)
            assert(buffer.area.width == width, s"$effect resized its area")

  /** Compared as cells, not as text: Wave and Gradient move emphasis and colour without moving a single glyph, so a
    * text-only comparison would call them static.
    */
  test("every effect animates rather than sitting still"):
    // four rows, because Bounce is flat in one by design and would otherwise look static here
    def snapshot(effect: TextEffect, at: FiniteDuration): Seq[(String, Option[Color], Boolean)] =
      val buffer = rendered(AnimatedText("hello world", at, effect), 20, 4)
      for
        y <- 0 until 4
        x <- 0 until 20
      yield
        val cell = buffer.get(x, y)
        (cell.symbol, cell.style.fg, cell.style.modifiers.has(io.worxbend.tui.core.Modifiers.Bold))
    every.foreach: effect =>
      val frames = (0 to 30).map(step => snapshot(effect, (step * 120).millis)).distinct
      assert(frames.size > 1, s"$effect never changed across three seconds")

  test("an empty string renders nothing rather than throwing"):
    every.foreach: effect =>
      assert(line(effect, 500.millis, "", 12) == "")

  // ---------------------------------------------------------------- typewriter

  /** The reveal is a function of elapsed time, so it is monotonic and lands exactly on the full string. */
  test("a typewriter reveals its text progressively and then stops"):
    val effect  = TextEffect.Typewriter(charactersPerSecond = 10.0)
    val lengths = (0 to 12).map(tenths => line(effect, (tenths * 100).millis, "abcde", 12).takeWhile(_ != '▋').length)
    assert(lengths == lengths.sorted, s"the reveal went backwards: $lengths")
    assert(lengths.head == 0)
    assert(lengths.last == 5, "after half a second at 10/s the whole five-character string is out")

  /** A cursor that kept blinking after the last character would read as "still typing" forever. */
  test("the typewriter cursor disappears once the text is complete"):
    val effect = TextEffect.Typewriter(charactersPerSecond = 100.0)
    val done   = (0 to 8).map(step => line(effect, 1.second + (step * 250).millis, "abc", 12))
    assert(done.forall(_ == "abc"), s"the cursor outlived the text: ${done.distinct}")

  test("a typewriter mid-reveal shows a cursor at the write head"):
    val effect = TextEffect.Typewriter(charactersPerSecond = 4.0)
    val frames = (0 to 8).map(step => line(effect, (step * 125).millis, "abcdef", 12))
    assert(frames.exists(_.endsWith("▋")), s"no frame showed a cursor: $frames")

  // ---------------------------------------------------------------- gradient

  test("a gradient colors each grapheme from the ramp and scrolls it"):
    val effect = TextEffect.Gradient(ColorRamp(Color.Red, Color.Green))
    val buffer = rendered(AnimatedText("abcdef", 0.millis, effect), 12, 1)
    val colors = (0 until 6).map(x => buffer.get(x, 0).style.fg).distinct
    assert(colors.size > 1, "a gradient that paints one color is not a gradient")
    val later  = rendered(AnimatedText("abcdef", 700.millis, effect), 12, 1)
    assert((0 until 6).map(x => later.get(x, 0).style.fg) != (0 until 6).map(x => buffer.get(x, 0).style.fg))
    assert(trimmedLines(later).head == "abcdef", "the text must stay put while the ramp scrolls")

  // ---------------------------------------------------------------- shimmer

  test("a shimmer sweeps a highlight across and rests between sweeps"):
    val effect    = TextEffect.Shimmer(period = 1000.millis)
    val highlight = Style.Default.bold
    val litCounts = (0 to 20).map: step =>
      val buffer = rendered(AnimatedText("abcdefgh", (step * 50).millis, effect, highlightStyle = highlight), 12, 1)
      (0 until 8).count(x => buffer.get(x, 0).style.modifiers.has(io.worxbend.tui.core.Modifiers.Bold))
    assert(litCounts.exists(_ > 0), "the shimmer never lit anything")
    assert(litCounts.exists(_ == 0), "the shimmer never rested, so it reads as a strobe")

  // ---------------------------------------------------------------- bounce

  /** The bounce is the one effect that needs vertical room; at one row it must degrade to a flat line rather than
    * vanish or write outside its rect.
    */
  test("a bounce uses the rows it is given and degrades to flat in one row"):
    val effect = TextEffect.Bounce(trail = 1)
    val tall   = rendered(AnimatedText("abcd", 300.millis, effect), 8, 4)
    val rows   = (0 until 4).count(y => (0 until 8).exists(x => tall.get(x, y).symbol.trim.nonEmpty))
    assert(rows > 1, "a bounce in four rows should occupy more than one")

    val flat = rendered(AnimatedText("abcd", 300.millis, effect), 8, 1)
    assert(trimmedLines(flat).head == "abcd")

  test("a bouncing grapheme is at a different height at different moments"):
    val effect                         = TextEffect.Bounce(trail = 0, cyclesPerSecond = 1.0)
    def rowOf(at: FiniteDuration): Int =
      val buffer = rendered(AnimatedText("a", at, effect), 4, 5)
      (0 until 5).indexWhere(y => buffer.get(0, y).symbol == "a")
    assert((0 to 10).map(step => rowOf((step * 100).millis)).distinct.size > 1)

  test("preferredHeight asks for room only when the effect needs it"):
    assert(AnimatedText("x", 0.millis, TextEffect.Wave()).preferredHeight == 1)
    assert(AnimatedText("x", 0.millis, TextEffect.Typewriter()).preferredHeight == 1)
    assert(AnimatedText("x", 0.millis, TextEffect.Bounce(trail = 3)).preferredHeight >= 4)

  /** Wide graphemes must not be split across the boundary or double-counted, the repo-wide rule for any glyph work. */
  test("wide graphemes keep their continuation cell"):
    every.foreach: effect =>
      val buffer = rendered(AnimatedText("你好", 250.millis, effect), 6, 3)
      assert(buffer.area == Rect(0, 0, 6, 3))
