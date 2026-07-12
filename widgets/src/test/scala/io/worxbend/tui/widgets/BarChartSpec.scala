package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class BarChartSpec extends AnyFunSuite:

  test("bars scale against the maximum with labels underneath"):
    val chart = BarChart(Seq(("a", 8), ("b", 4)), barWidth = 1, barGap = 1, max = Some(8))
    val buffer = rendered(chart, 3, 3)
    assert(trimmedLines(buffer) == Seq("█", "█ █", "a b"))

  test("partial values top out with a fractional block"):
    val chart = BarChart(Seq(("", 3)), barWidth = 1, max = Some(8), barGap = 0)
    val buffer = rendered(chart, 1, 1)
    assert(trimmedLines(buffer) == Seq("▃"))

  test("bars that do not fit the width are clipped"):
    val chart = BarChart(Seq(("a", 8), ("b", 8), ("c", 8)), barWidth = 2, barGap = 1, max = Some(8))
    val buffer = rendered(chart, 5, 2)
    assert(trimmedLines(buffer).head == "██ ██")

  test("empty data renders nothing"):
    val buffer = rendered(BarChart(Seq.empty), 5, 3)
    assert(trimmedLines(buffer).forall(_.isEmpty))
