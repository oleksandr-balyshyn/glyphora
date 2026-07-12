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
