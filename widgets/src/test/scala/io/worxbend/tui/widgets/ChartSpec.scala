package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Color, Constraint, Style}
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
    val buffer = rendered(Chart(Seq(cpu, mem), (0.0, 1.0), (0.0, 1.0), showLegend = true), 40, 12)
    assert(trimmedLines(buffer)(0).endsWith("\u25a0 cpu"))
    assert(trimmedLines(buffer)(1).endsWith("\u25a0 mem"))
    assert(buffer.get(35, 0).style.fg.contains(Color.Red))
    assert(buffer.get(35, 1).style.fg.contains(Color.Blue))

  test("a dataset with no name gets no legend entry"):
    val datasets = Seq(Dataset("", Seq.empty), Dataset("only", Seq.empty))
    val buffer   = rendered(Chart(datasets, (0.0, 1.0), (0.0, 1.0), showLegend = true), 40, 12)
    assert(trimmedLines(buffer)(0).endsWith("\u25a0 only"))
    assert(!trimmedLines(buffer)(1).contains("\u25a0"))

  test("the legend is off unless it is asked for"):
    val datasets = Seq(Dataset("cpu", Seq.empty))
    val silent   = rendered(Chart(datasets, (0.0, 1.0), (0.0, 1.0)), 40, 12)
    val explicit = rendered(Chart(datasets, (0.0, 1.0), (0.0, 1.0), showLegend = false), 40, 12)
    assert(trimmedLines(silent) == trimmedLines(explicit))
    assert(!trimmedLines(silent).mkString.contains("cpu"))

  test("a legend measures a wide-character name in columns, not characters"):
    // \u8ca0\u8377 is two CJK ideographs, four terminal columns wide, so the entry is six columns of the plot's 39
    val chart  = Chart(Seq(Dataset("\u8ca0\u8377", Seq.empty)), (0.0, 1.0), (0.0, 1.0), showLegend = true)
    val buffer = rendered(chart, 40, 12)
    assert(trimmedLines(buffer)(0).endsWith("\u25a0 \u8ca0\u8377"))
    assert(buffer.get(34, 0).symbol == "\u25a0")

  test("a legend wider than a quarter of the plot is dropped, not truncated"):
    val chart  = Chart(Seq(Dataset("a" * 20, Seq.empty)), (0.0, 1.0), (0.0, 1.0), showLegend = true)
    val buffer = rendered(chart, 40, 12)
    // 22 columns of key against a 39-column plot: the data wins and the key is not drawn at all
    assert(!trimmedLines(buffer).mkString.contains("a"))
    assert(buffer.get(0, 0).symbol == "\u2502")

  test("a legend taller than a quarter of the plot is dropped as a whole"):
    val datasets = (1 to 5).map(index => Dataset(s"series $index", Seq.empty))
    val buffer   = rendered(Chart(datasets, (0.0, 1.0), (0.0, 1.0), showLegend = true), 40, 12)
    // five rows of key against an 11-row plot: no half key, because a reader cannot tell what is missing
    assert(!trimmedLines(buffer).mkString.contains("series"))

  test("relaxing the constraints lets an otherwise hidden legend through"):
    val datasets = (1 to 5).map(index => Dataset(s"series $index", Seq.empty))
    val chart    = Chart(
      datasets,
      (0.0, 1.0),
      (0.0, 1.0),
      showLegend = true,
      hiddenLegendConstraints = (Constraint.Percentage(100), Constraint.Percentage(100)),
    )
    val buffer   = rendered(chart, 40, 12)
    assert(trimmedLines(buffer)(0).endsWith("\u25a0 series 1"))
    assert(trimmedLines(buffer)(4).endsWith("\u25a0 series 5"))
