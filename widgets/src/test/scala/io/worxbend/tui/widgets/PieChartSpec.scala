package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class PieChartSpec extends AnyFunSuite:

  test("a pie chart fills a disc and renders the legend with percentages"):
    val text = trimmedLines(rendered(PieChart(Seq(("a", 3.0), ("b", 1.0))), 30, 9)).mkString("\n")
    assert(text.contains("█"))
    assert(text.contains("■ a 75%"))
    assert(text.contains("■ b 25%"))

  test("pie sectors use distinct palette styles"):
    val buffer = rendered(PieChart(Seq(("a", 1.0), ("b", 1.0)), showLegend = false), 12, 7)
    val colors = (for
      y <- 0 until 7
      x <- 0 until 12
      cell = buffer.get(x, y)
      if cell.symbol == "█"
    yield cell.style.fg).distinct
    assert(colors.size == 2)

  test("a pie legend stays inside the area when it is wider than the widget"):
    val pie    = PieChart(Seq(("a-very-long-series-label", 1.0)))
    val buffer = Buffer(Rect(0, 0, 20, 3))
    pie.render(Rect(10, 0, 10, 3), buffer)
    // the legend cannot fit next to a disc, but it must not paint to the left of x = 10 either
    assert(trimmedLines(buffer).forall(line => line.take(10).isBlank))
