package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Color, Rect}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class StackedBarChartSpec extends AnyFunSuite:

  test("stacked bars stack series segments bottom-up with palette styles"):
    val chart  = StackedBarChart(Seq(("x", Seq(1L, 1L)), ("y", Seq(2L, 2L))), barWidth = 1, barGap = 0)
    val buffer = rendered(chart, 2, 5) // 4 chart rows + label
    assert(buffer.get(0, 4).symbol == "x")
    assert(buffer.get(0, 3).style.fg.contains(Color.Cyan)) // series 0 at the bottom
    assert(buffer.get(0, 2).style.fg.contains(Color.Green)) // series 1 above
    assert(buffer.get(0, 1).symbol == " ") // the shorter bar stops here
    assert(buffer.get(1, 0).style.fg.contains(Color.Green)) // the max bar fills to the top

  test("stacked segments never spill above the chart when their rounding overshoots"):
    // six equal segments each round up to 2 of the 10 chart rows, asking for 12 rows in total
    val chart  = StackedBarChart(Seq(("", Seq.fill(6)(15L))), barWidth = 1, barGap = 0)
    val buffer = Buffer(Rect(0, 0, 1, 13))
    chart.render(Rect(0, 3, 1, 10), buffer)
    assert(trimmedLines(buffer).take(3) == Seq("", "", ""))

  test("a pinned max keeps two charts on the same scale"):
    // the same stack drawn beside a taller one: without a pinned ceiling it would shrink to half the height
    val small  = StackedBarChart(Seq(("", Seq(4L))), barWidth = 1, barGap = 0, max = Some(8))
    val both   = StackedBarChart(Seq(("", Seq(4L)), ("", Seq(8L))), barWidth = 1, barGap = 0, max = Some(8))
    assert(trimmedLines(rendered(small, 1, 4)) == Seq("", "", "█", "█"))
    val beside = rendered(both, 2, 4)
    assert((0 to 3).map(row => beside.get(0, row).symbol) == Seq(" ", " ", "█", "█"))

  test("without a max the scale still follows the tallest stack"):
    val chart  = StackedBarChart(Seq(("", Seq(4L)), ("", Seq(8L))), barWidth = 1, barGap = 0)
    val buffer = rendered(chart, 2, 4)
    assert((0 to 3).map(row => buffer.get(0, row).symbol) == Seq(" ", " ", "█", "█"))

  test("a stack taller than a pinned max is clamped to the top of the chart"):
    val chart  = StackedBarChart(Seq(("", Seq(100L))), barWidth = 1, barGap = 0, max = Some(8))
    val buffer = Buffer(Rect(0, 0, 1, 6))
    chart.render(Rect(0, 2, 1, 4), buffer)
    // the two rows above the chart's own area stay untouched, whatever the data asks for
    assert(trimmedLines(buffer) == Seq("", "", "█", "█", "█", "█"))

  test("a max of zero draws nothing rather than dividing by zero"):
    val chart = StackedBarChart(Seq(("", Seq(4L))), barWidth = 1, barGap = 0, max = Some(0))
    assert(trimmedLines(rendered(chart, 1, 4)).forall(_.isEmpty))
