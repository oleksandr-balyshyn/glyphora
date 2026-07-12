package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Color, Rect}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class VizExtrasSpec extends AnyFunSuite:

  test("braille resolution packs sub-pixels into braille glyphs"):
    val canvas =
      Canvas((0.0, 1.0), (0.0, 3.0), Seq(Shape.Points(Seq((0.0, 3.0)))), resolution = CanvasResolution.Braille)
    val buffer = rendered(canvas, 1, 1)
    assert(buffer.get(0, 0).symbol == "⠁") // top-left dot only

  test("braille accumulates multiple dots in one cell"):
    val points = Shape.Points(Seq((0.0, 3.0), (0.0, 2.0), (1.0, 0.0)))
    val canvas = Canvas((0.0, 1.0), (0.0, 3.0), Seq(points), resolution = CanvasResolution.Braille)
    val buffer = rendered(canvas, 1, 1)
    // dots 1 (0,0), 2 (0,1) and 8 (1,3): 0x01 | 0x02 | 0x80 = 0x83
    assert(buffer.get(0, 0).symbol == (0x2800 + 0x83).toChar.toString)

  test("half-block resolution renders upper, lower, and full blocks"):
    def cellFor(ys: Seq[Double]): String =
      val canvas =
        Canvas(
          (0.0, 1.0),
          (0.0, 1.0),
          Seq(Shape.Points(ys.map(y => (0.0, y)))),
          resolution = CanvasResolution.HalfBlock,
        )
      rendered(canvas, 1, 1).get(0, 0).symbol
    assert(cellFor(Seq(1.0)) == "▀")
    assert(cellFor(Seq(0.0)) == "▄")
    assert(cellFor(Seq(0.0, 1.0)) == "█")

  test("a pie chart fills a disc and renders the legend with percentages"):
    val pie = PieChart(Seq(("a", 3.0), ("b", 1.0)))
    val buffer = rendered(pie, 30, 9)
    val text = trimmedLines(buffer).mkString("\n")
    assert(text.contains("█"))
    assert(text.contains("■ a 75%"))
    assert(text.contains("■ b 25%"))

  test("pie sectors use distinct palette styles"):
    val pie = PieChart(Seq(("a", 1.0), ("b", 1.0)), showLegend = false)
    val buffer = rendered(pie, 12, 7)
    val colors = (for
      y <- 0 until 7
      x <- 0 until 12
      cell = buffer.get(x, y)
      if cell.symbol == "█"
    yield cell.style.fg).distinct
    assert(colors.size == 2)

  test("stacked bars stack series segments bottom-up with palette styles"):
    val chart = StackedBarChart(Seq(("x", Seq(1L, 1L)), ("y", Seq(2L, 2L))), barWidth = 1, barGap = 0)
    val buffer = rendered(chart, 2, 5) // 4 chart rows + label
    assert(buffer.get(0, 4).symbol == "x")
    assert(buffer.get(0, 3).style.fg.contains(Color.Cyan)) // series 0 at the bottom
    assert(buffer.get(0, 2).style.fg.contains(Color.Green)) // series 1 above
    assert(buffer.get(0, 1).symbol == " ") // the shorter bar stops here
    assert(buffer.get(1, 0).style.fg.contains(Color.Green)) // the max bar fills to the top

  test("a heatmap maps values onto the shade ramp"):
    val heat = Heatmap(Seq(Seq(0.0, 0.5, 1.0)))
    val buffer = rendered(heat, 3, 1)
    assert(buffer.get(0, 0).symbol == " ")
    assert(buffer.get(1, 0).symbol == "▒")
    assert(buffer.get(2, 0).symbol == "█")

  test("heatmap rows clip to the area"):
    val heat = Heatmap(Seq(Seq(1.0), Seq(1.0), Seq(1.0)))
    val buffer = Buffer(Rect(0, 0, 1, 2))
    heat.render(buffer.area, buffer)
    assert(trimmedLines(buffer) == Seq("█", "█"))

  test("a chart renders at braille resolution with axis labels"):
    val chart = Chart(
      Seq(Dataset("d", Seq((0.0, 0.0), (10.0, 10.0)))),
      (0.0, 10.0),
      (0.0, 10.0),
      resolution = CanvasResolution.Braille,
      showLabels = true,
    )
    val buffer = rendered(chart, 12, 6)
    val text = trimmedLines(buffer).mkString("\n")
    assert(text.contains("10")) // y-max label
    assert(text.exists(c => c >= 0x2800.toChar && c <= 0x28ff.toChar)) // braille cells

  test("a link renders its label with the url attached to the style"):
    val buffer = rendered(Link("docs", "https://example.com"), 10, 1)
    assert(trimmedLines(buffer).head == "docs")
    assert(buffer.get(0, 0).style.link.contains("https://example.com"))
