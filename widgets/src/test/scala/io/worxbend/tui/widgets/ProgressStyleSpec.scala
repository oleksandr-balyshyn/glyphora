package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Cell, Color, Modifiers, Style}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class ProgressStyleSpec extends AnyFunSuite:

  test("every built-in style is reachable by name and uniquely named"):
    val names = ProgressStyle.All.map(_.name)
    assert(names.distinct.size == names.size, s"duplicate names: ${names.diff(names.distinct).distinct}")
    ProgressStyle.All.foreach: style =>
      assert(ProgressStyle.byName(style.name).contains(style))
      assert(style.fill.nonEmpty && style.track.nonEmpty, s"${style.name} has an empty glyph")
    assert(ProgressStyle.byName("no-such-style").isEmpty)

  /** The bar has to fill exactly the width it was given, at every fraction — a bar that returns width-1 glyphs leaves a
    * hole at its right edge that the caller has no way to notice.
    */
  test("a style always yields exactly the requested number of cells"):
    ProgressStyle.All.foreach: style =>
      (0 to 20).foreach: width =>
        (0 to 10).foreach: step =>
          val glyphs = style.glyphs(step / 10.0, width)
          assert(glyphs.size == width, s"${style.name} at width $width, step $step gave ${glyphs.size} cells")

  test("empty and full bars are all track and all fill"):
    ProgressStyle.All.foreach: style =>
      assert(style.glyphs(0.0, 6) == Vector.fill(6)(style.track), s"${style.name} at 0 is not empty")
      assert(style.glyphs(1.0, 6) == Vector.fill(6)(style.fill), s"${style.name} at 1 is not full")
      assert(style.filledCells(0.0, 6) == 0)
      assert(style.filledCells(1.0, 6) == 6)

  /** Out-of-range and NaN ratios reach these from user arithmetic like `done / total` with `total == 0`. NaN must read
    * as no progress: `NaN.toInt` is 0, but `math.round(NaN)` is also 0, so an unguarded bar silently shows empty —
    * indistinguishable from a real zero — while an unclamped one would index out of bounds.
    */
  test("out-of-range and NaN fractions clamp instead of throwing"):
    ProgressStyle.All.foreach: style =>
      assert(style.glyphs(-0.5, 4) == Vector.fill(4)(style.track), s"${style.name} did not clamp below 0")
      assert(style.glyphs(1.5, 4) == Vector.fill(4)(style.fill), s"${style.name} did not clamp above 1")
      assert(style.glyphs(Double.NaN, 4) == Vector.fill(4)(style.track), s"${style.name} read NaN as progress")
      assert(style.glyphs(Double.PositiveInfinity, 4) == Vector.fill(4)(style.fill))

  test("a non-positive width yields no cells rather than throwing"):
    ProgressStyle.All.foreach: style =>
      assert(style.glyphs(0.5, 0).isEmpty)
      assert(style.glyphs(0.5, -3).isEmpty)
      assert(style.filledCells(0.5, 0) == 0)

  /** Sub-cell partials are the whole point of the distinction: an 8-cell `Blocks` bar must show more than 9 distinct
    * renderings, where a whole-cell style shows exactly 9.
    */
  test("a sub-cell style resolves finer than one cell, a whole-cell style does not"):
    def distinctRenderings(style: ProgressStyle): Int =
      (0 to 800).map(step => style.glyphs(step / 800.0, 8)).distinct.size
    assert(!ProgressStyle.Line.isSubCell)
    assert(ProgressStyle.Blocks.isSubCell)
    assert(distinctRenderings(ProgressStyle.Line) == 9, "a whole-cell bar has one rendering per cell boundary")
    assert(distinctRenderings(ProgressStyle.Blocks) > 50, "eight partials should multiply the resolution")

  /** With partials the fill is floored so the bar never claims progress that has not happened; without them it rounds
    * to nearest, because that is the closest a whole cell can get.
    */
  test("sub-cell styles floor the fill and whole-cell styles round it"):
    assert(ProgressStyle.Line.filledCells(0.6, 10) == 6)
    assert(ProgressStyle.Line.filledCells(0.65, 10) == 7, "rounds up to the nearest cell")
    assert(ProgressStyle.Blocks.filledCells(0.65, 10) == 6, "floors, and the remainder becomes a partial glyph")
    assert(ProgressStyle.Blocks.glyphs(0.65, 10)(6) != ProgressStyle.Blocks.track, "the boundary cell is drawn")

  test("progress never goes backwards as the fraction rises"):
    ProgressStyle.All.foreach: style =>
      val counts = (0 to 100).map(step => style.filledCells(step / 100.0, 12))
      assert(counts == counts.sorted, s"${style.name} filled-cell count is not monotonic")

  test("a head glyph caps the fill while the bar is incomplete"):
    val arrow = ProgressStyle.Arrow
    assert(arrow.glyphs(0.5, 4) == Vector("=", ">", "-", "-"))
    assert(arrow.glyphs(1.0, 4) == Vector("=", "=", "=", "="), "a complete bar has no head to draw")

  // ---------------------------------------------------------------------- the widgets that use them

  test("the default line gauge renders exactly as before"):
    assert(trimmedLines(rendered(LineGauge(0.5), 12, 1)) == Seq("50% ━━━━────"))
    assert(trimmedLines(rendered(LineGauge(0.0), 8, 1)) == Seq("0% ─────"))
    assert(trimmedLines(rendered(LineGauge(1.0), 9, 1)) == Seq("100% ━━━━"))

  test("a line gauge draws with whichever style it is given"):
    assert(trimmedLines(rendered(LineGauge(0.5, progressStyle = ProgressStyle.Ascii), 12, 1)) == Seq("50% ####----"))
    assert(trimmedLines(rendered(LineGauge(0.5, progressStyle = ProgressStyle.Arrow), 12, 1)) == Seq("50% ===>----"))

  /** A dropped caption gives its columns to the bar rather than leaving them blank: at width 8 the bar is 8 cells, not
    * the 4 it would get behind a `"50% "` caption.
    */
  test("a line gauge label can be replaced or dropped"):
    assert(trimmedLines(rendered(LineGauge(0.5, label = ProgressLabel.Text("sync")), 13, 1)) == Seq("sync ━━━━────"))
    assert(trimmedLines(rendered(LineGauge(0.5, label = ProgressLabel.Hidden), 8, 1)) == Seq("━━━━────"))
    assert(trimmedLines(rendered(LineGauge(1.0, label = ProgressLabel.Hidden), 8, 1)) == Seq("━━━━━━━━"))

  test("LineGauge.of turns counts into a ratio and survives a zero total"):
    assert(trimmedLines(rendered(LineGauge.of(3, 10), 12, 1)).head.startsWith("30%"))
    assert(trimmedLines(rendered(LineGauge.of(1, 0), 12, 1)).head.startsWith("0%"))

  /** The ramp only replaces the fill's foreground, so modifiers set on `filledStyle` survive it. Sampled at column 0,
    * which is filled at every ratio above zero.
    */
  test("a fill ramp colors the bar by progress without dropping its modifiers"):
    def ramped(ratio: Double): Cell =
      rendered(
        LineGauge(
          ratio,
          label = ProgressLabel.Hidden,
          filledStyle = Style.Default.bold,
          fillRamp = Some(ColorRamp(Color.Red, Color.Green)),
        ),
        8,
        1,
      ).get(0, 0)
    assert(ramped(0.25).style.fg.contains(ColorRamp(Color.Red, Color.Green).at(0.25)))
    assert(ramped(1.0).style.fg.contains(ColorRamp(Color.Red, Color.Green).at(1.0)))
    assert(ramped(0.25).style.fg != ramped(1.0).style.fg, "the ramp must actually move")
    assert(ramped(1.0).style.modifiers.hasAny(Modifiers.Bold), "the ramp must not drop bold")

  test("without a ramp the fill keeps the style it was given"):
    val cell = rendered(
      LineGauge(1.0, label = ProgressLabel.Hidden, filledStyle = Style.Default.withFg(Color.Magenta)),
      8,
      1,
    ).get(0, 0)
    assert(cell.style.fg.contains(Color.Magenta))
