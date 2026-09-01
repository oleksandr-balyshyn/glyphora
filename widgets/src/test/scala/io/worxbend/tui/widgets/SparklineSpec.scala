package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Color, Modifiers, Style}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class SparklineSpec extends AnyFunSuite:

  test("one row scales values to the eight block levels"):
    val buffer = rendered(Sparkline(Seq(1, 2, 4, 8), max = Some(8)), 4, 1)
    assert(trimmedLines(buffer) == Seq("▁▂▄█"))

  test("the scale ceiling defaults to the data maximum"):
    val buffer = rendered(Sparkline(Seq(2, 4)), 2, 1)
    assert(trimmedLines(buffer) == Seq("▄█"))

  test("excess data points are clipped at the area width"):
    val buffer = rendered(Sparkline(Seq(8, 8, 8, 8), max = Some(8)), 2, 1)
    assert(trimmedLines(buffer) == Seq("██"))

  test("a taller area stacks full blocks under the partial top"):
    val buffer = rendered(Sparkline(Seq(8, 4), max = Some(8)), 2, 2)
    assert(trimmedLines(buffer) == Seq("█", "██"))

  test("zero values draw nothing"):
    val buffer = rendered(Sparkline(Seq(0, 8), max = Some(8)), 2, 1)
    assert(trimmedLines(buffer) == Seq(" █"))

  test("dual sparklines render in the top and bottom halves"):
    val widget = DualSparkline(Seq(8, 8), Seq(4, 4), max = Some(8))
    assert(trimmedLines(rendered(widget, 2, 2)) == Seq("██", "▄▄"))

  test("right-to-left keeps the newest points where left-to-right keeps the oldest"):
    val data   = Seq(1L, 2L, 4L, 8L)
    assert(trimmedLines(rendered(Sparkline(data, max = Some(8)), 2, 1)) == Seq("▁▂"))
    val newest = Sparkline(data, max = Some(8), direction = SparkDirection.RightToLeft)
    assert(trimmedLines(rendered(newest, 2, 1)) == Seq("▄█"))

  test("right-to-left pins a series shorter than the area to the right edge"):
    val widget = Sparkline(Seq(8, 8), max = Some(8), direction = SparkDirection.RightToLeft)
    val buffer = rendered(widget, 5, 1)
    assert(buffer.get(0, 0).symbol == " ")
    assert(buffer.get(2, 0).symbol == " ")
    assert(buffer.get(3, 0).symbol == "█")
    assert(buffer.get(4, 0).symbol == "█")

  test("the ceiling comes from the whole series, not from the visible window"):
    // the 100 has scrolled off the left, but it still sets the scale — otherwise the trace would jump
    // to full height the moment the peak left the window
    val widget = Sparkline(Seq(100, 50, 50), direction = SparkDirection.RightToLeft)
    assert(trimmedLines(rendered(widget, 2, 1)) == Seq("▄▄"))

  test("an empty series and a degenerate area draw nothing in either direction"):
    SparkDirection.values.foreach { direction =>
      assert(trimmedLines(rendered(Sparkline(Seq.empty, direction = direction), 4, 1)).forall(_.isEmpty))
      assert(trimmedLines(rendered(Sparkline(Seq(1, 2), direction = direction), 0, 0)).forall(_.isEmpty))
    }

  test("dual sparklines anchor both halves to the same edge"):
    val widget = DualSparkline(Seq(8, 8, 8), Seq(0, 8, 8), max = Some(8), direction = SparkDirection.RightToLeft)
    assert(trimmedLines(rendered(widget, 2, 2)) == Seq("██", "██"))

  test("an absent reading leaves a hole in the trace instead of a zero-height bar"):
    val chart  = Sparkline.ofReadings(Seq(Some(8L), None, Some(8L)), max = Some(8), barSet = BarSet.Solid)
    val buffer = rendered(chart, 3, 1)
    // BarSet.Solid gives every drawn column a glyph, so the untouched middle column is visibly a gap
    assert(trimmedLines(buffer) == Seq("█ █"))

  test("an absent column can be marked with a glyph of its own"):
    val chart  = Sparkline.ofReadings(Seq(Some(8L), None, Some(8L)), max = Some(8), absentSymbol = Some("·"))
    val buffer = rendered(chart, 3, 2)
    // the marker runs the whole height of the area, because half a column of it would read as a value
    assert(trimmedLines(buffer) == Seq("█·█", "█·█"))

  test("an absent point does not set the scale"):
    // The placeholder stored for the absent reading is a zero, but a caller building the widget by hand may leave
    // anything there; either way the ceiling comes from the readings that exist.
    val chart  = Sparkline(Seq(8L, 800L, 4L), absentColumns = Set(1), barSet = BarSet.Solid)
    val buffer = rendered(chart, 3, 2)
    // 8 is the ceiling, so the first column is full height and the third is half of it
    assert(trimmedLines(buffer) == Seq("█", "█ █"))

  test("absent positions are indices into the data, not into the visible columns"):
    // RightToLeft drops the oldest points off the left edge, and the absent index must still name the same reading.
    val chart  = Sparkline(
      Seq(1L, 2L, 3L, 4L),
      max = Some(4),
      direction = SparkDirection.RightToLeft,
      barSet = BarSet.Solid,
      absentColumns = Set(2),
      absentSymbol = Some("·"),
    )
    val buffer = rendered(chart, 2, 1)
    assert(trimmedLines(buffer) == Seq("·█"))

  test("an absent index outside the data changes nothing"):
    val plain   = rendered(Sparkline(Seq(8L, 4L), max = Some(8)), 2, 2)
    val ignored = rendered(Sparkline(Seq(8L, 4L), max = Some(8), absentColumns = Set(7)), 2, 2)
    assert(trimmedLines(plain) == trimmedLines(ignored))

  test("a series with no readings at all draws only its gaps and does not divide by zero"):
    val chart  = Sparkline.ofReadings(Seq(None, None), absentSymbol = Some("·"))
    val buffer = rendered(chart, 2, 1)
    assert(trimmedLines(buffer) == Seq("··"))

  test("the absent style layers over the sparkline style"):
    val chart  = Sparkline.ofReadings(
      Seq(None),
      style = Style.Default.bold,
      absentSymbol = Some("·"),
      absentStyle = Style.Default.withFg(Color.Red),
    )
    val buffer = rendered(chart, 1, 1)
    assert(buffer.get(0, 0).style.fg.contains(Color.Red))
    assert(buffer.get(0, 0).style.modifiers.hasAny(Modifiers.Bold))
