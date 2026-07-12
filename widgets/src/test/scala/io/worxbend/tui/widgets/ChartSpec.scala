package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class ChartSpec extends AnyFunSuite:

  test("axes draw along the left and bottom edges"):
    val chart = Chart(Seq.empty, (0.0, 1.0), (0.0, 1.0))
    val lines = trimmedLines(rendered(chart, 5, 4))
    assert(lines == Seq("│", "│", "│", "└────"))

  test("a line dataset plots a connected series inside the axes"):
    val dataset = Dataset("d", Seq((0.0, 0.0), (3.0, 3.0)))
    val chart = Chart(Seq(dataset), (0.0, 3.0), (0.0, 3.0))
    val buffer = rendered(chart, 5, 5)
    // the plot area excludes the axis column/row; the diagonal runs corner to corner within it
    assert(buffer.get(1, 3).symbol == "•")
    assert(buffer.get(4, 0).symbol == "•")

  test("a scatter dataset plots isolated points"):
    val dataset = Dataset("d", Seq((0.0, 0.0), (3.0, 3.0)), graphType = GraphType.Scatter)
    val chart = Chart(Seq(dataset), (0.0, 3.0), (0.0, 3.0))
    val buffer = rendered(chart, 5, 5)
    assert(buffer.get(1, 3).symbol == "•")
    assert(buffer.get(4, 0).symbol == "•")
    assert(buffer.get(2, 2).symbol == " ") // no connecting segment

  test("a degenerate area renders nothing"):
    val chart = Chart(Seq.empty, (0.0, 1.0), (0.0, 1.0))
    assert(trimmedLines(rendered(chart, 2, 2)).forall(_.isEmpty))
