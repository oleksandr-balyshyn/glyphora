package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Color, Style}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class ChartSpec extends AnyFunSuite:

  test("axes draw along the left and bottom edges"):
    val chart = Chart(Seq.empty, (0.0, 1.0), (0.0, 1.0))
    val lines = trimmedLines(rendered(chart, 5, 4))
    assert(lines == Seq("│", "│", "│", "└────"))

  test("a line dataset plots a connected series inside the axes"):
    val dataset = Dataset("d", Seq((0.0, 0.0), (3.0, 3.0)))
    val chart   = Chart(Seq(dataset), (0.0, 3.0), (0.0, 3.0))
    val buffer  = rendered(chart, 5, 5)
    // the plot area excludes the axis column/row; the diagonal runs corner to corner within it
    assert(buffer.get(1, 3).symbol == "•")
    assert(buffer.get(4, 0).symbol == "•")

  test("a scatter dataset plots isolated points"):
    val dataset = Dataset("d", Seq((0.0, 0.0), (3.0, 3.0)), graphType = GraphType.Scatter)
    val chart   = Chart(Seq(dataset), (0.0, 3.0), (0.0, 3.0))
    val buffer  = rendered(chart, 5, 5)
    assert(buffer.get(1, 3).symbol == "•")
    assert(buffer.get(4, 0).symbol == "•")
    assert(buffer.get(2, 2).symbol == " ") // no connecting segment

  test("a degenerate area renders nothing"):
    val chart = Chart(Seq.empty, (0.0, 1.0), (0.0, 1.0))
    assert(trimmedLines(rendered(chart, 2, 2)).forall(_.isEmpty))

  test("a chart renders at braille resolution with axis labels"):
    val chart  = Chart(
      Seq(Dataset("d", Seq((0.0, 0.0), (10.0, 10.0)))),
      (0.0, 10.0),
      (0.0, 10.0),
      resolution = CanvasResolution.Braille,
      showLabels = true,
    )
    val buffer = rendered(chart, 12, 6)
    val text   = trimmedLines(buffer).mkString("\n")
    assert(text.contains("10"))                                        // y-max label
    assert(text.exists(c => c >= 0x2800.toChar && c <= 0x28ff.toChar)) // braille cells
    // the labels are two columns wide, so the axis sits at x = 2 and no digit shares a cell with the plot
    assert(buffer.get(2, 0).symbol == "│")
    assert(buffer.get(2, 5).symbol == "└")
    assert((0 until 6).forall(row => !buffer.get(3, row).symbol.headOption.exists(_.isDigit)))

  test("labels sit in a gutter left of the axis instead of over the plot"):
    val chart  = Chart(
      Seq(Dataset("d", Seq((0.0, 1000.0), (10.0, 1000.0)))),
      (0.0, 10.0),
      (0.0, 1000.0),
      showLabels = true,
    )
    val buffer = rendered(chart, 20, 6)
    assert(trimmedLines(buffer).head.startsWith("1000│"))
    assert(buffer.get(5, 0).symbol == "•") // the series still reaches the top row, right of the axis

  test("label alignment places the short label inside the gutter"):
    def labelRow(alignment: Alignment): String =
      val chart = Chart(Seq.empty, (0.0, 1.0), (5.0, 1000.0), showLabels = true, labelAlignment = alignment)
      trimmedLines(rendered(chart, 20, 6))(4) // the y-min row, one above the x axis
    assert(labelRow(Alignment.Right) == "   5│")
    assert(labelRow(Alignment.Left) == "5   │")
    assert(labelRow(Alignment.Center) == " 5  │")

  test("a pane too narrow for the gutter drops the labels, not the plot"):
    val chart  = Chart(Seq.empty, (0.0, 1.0), (0.0, 100000.0), showLabels = true)
    val buffer = rendered(chart, 6, 4)
    assert(buffer.get(0, 0).symbol == "│")
    assert(trimmedLines(buffer).mkString.forall(!_.isDigit))

  test("a legend lists each named dataset in its own style"):
    val cpu    = Dataset("cpu", Seq((0.0, 0.0)), Style.Default.withFg(Color.Red))
    val mem    = Dataset("mem", Seq((0.0, 1.0)), Style.Default.withFg(Color.Blue))
    val buffer = rendered(Chart(Seq(cpu, mem), (0.0, 1.0), (0.0, 1.0), showLegend = true), 30, 8)
    assert(trimmedLines(buffer)(0).endsWith("■ cpu"))
    assert(trimmedLines(buffer)(1).endsWith("■ mem"))
    assert(buffer.get(25, 0).style.fg.contains(Color.Red))
    assert(buffer.get(25, 1).style.fg.contains(Color.Blue))

  test("a dataset with no name gets no legend entry"):
    val chart  =
      Chart(Seq(Dataset("", Seq.empty), Dataset("only", Seq.empty)), (0.0, 1.0), (0.0, 1.0), showLegend = true)
    val buffer = rendered(chart, 20, 6)
    assert(trimmedLines(buffer)(0).endsWith("■ only"))
    assert(!trimmedLines(buffer)(1).contains("■"))

  test("the legend is off unless it is asked for"):
    val datasets = Seq(Dataset("cpu", Seq.empty))
    val silent   = rendered(Chart(datasets, (0.0, 1.0), (0.0, 1.0)), 20, 6)
    val explicit = rendered(Chart(datasets, (0.0, 1.0), (0.0, 1.0), showLegend = false), 20, 6)
    assert(trimmedLines(silent) == trimmedLines(explicit))
    assert(!trimmedLines(silent).mkString.contains("cpu"))

  test("a legend wider than the plot is truncated rather than painted outside it"):
    val chart  = Chart(Seq(Dataset("a" * 40, Seq.empty)), (0.0, 1.0), (0.0, 1.0), showLegend = true)
    val buffer = rendered(chart, 12, 5)
    // the axis column is untouched and the entry occupies only the plot columns after it
    assert(buffer.get(0, 0).symbol == "│")
    assert(trimmedLines(buffer)(0) == "│" + "■ " + "a" * 9)

  test("a chart too short for every entry drops the overflow instead of overwriting the axis"):
    val datasets = Seq(Dataset("one", Seq.empty), Dataset("two", Seq.empty), Dataset("three", Seq.empty))
    val buffer   = rendered(Chart(datasets, (0.0, 1.0), (0.0, 1.0), showLegend = true), 20, 3)
    assert(trimmedLines(buffer)(2) == "└───────────────────")
    assert(!trimmedLines(buffer).mkString.contains("three"))

  test("a legend measures a wide-character name in columns, not characters"):
    // 負荷 is two CJK ideographs, four terminal columns wide; the entry is "■ " plus those four
    val buffer = rendered(Chart(Seq(Dataset("負荷", Seq.empty)), (0.0, 1.0), (0.0, 1.0), showLegend = true), 12, 5)
    assert(trimmedLines(buffer)(0) == "│     ■ 負荷")
