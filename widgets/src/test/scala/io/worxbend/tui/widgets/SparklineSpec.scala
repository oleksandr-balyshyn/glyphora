package io.worxbend.tui.widgets

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
