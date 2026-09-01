package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Color, Style}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

/** The per-dataset drawing surface. */
final class ChartSurfaceSpec extends AnyFunSuite:

  private val diagonal = Dataset("d", Seq((0.0, 0.0), (10.0, 10.0)))

  private def column(buffer: io.worxbend.tui.core.Buffer, x: Int, height: Int): String =
    (0 until height).map(row => buffer.get(x, row).symbol).mkString

  test("a dataset marker overrides the chart marker"):
    val chart  = Chart(Seq(diagonal.copy(marker = Some("*"))), (0.0, 10.0), (0.0, 10.0), marker = "•")
    val buffer = rendered(chart, 6, 5)
    assert(buffer.get(1, 3).symbol == "*")

  test("datasets keep their own resolutions in one plot"):
    val braille = Dataset("b", Seq((0.0, 0.0), (0.0, 10.0)), resolution = Some(CanvasResolution.Braille))
    val cells   = Dataset("c", Seq((10.0, 0.0), (10.0, 10.0)))
    val buffer  = rendered(Chart(Seq(braille, cells), (0.0, 10.0), (0.0, 10.0)), 12, 6)
    val left    = column(buffer, 1, 5)
    val right   = column(buffer, 11, 5)
    assert(left.exists(glyph => glyph >= '⠀' && glyph <= '⣿'), left)
    assert(right.contains('•'), right)

  test("a dataset with no overrides follows the chart, in a single pass"):
    val chart  = Chart(Seq(diagonal), (0.0, 10.0), (0.0, 10.0), marker = "#")
    val buffer = rendered(chart, 6, 5)
    assert(buffer.get(1, 3).symbol == "#")

  test("datasets sharing a surface draw in listing order, so the later one is on top"):
    val points = Seq((0.0, 0.0), (10.0, 10.0))
    val under  = Dataset("u", points, style = Style.fg(Color.Red))
    val over   = Dataset("o", points, style = Style.fg(Color.Blue))
    val buffer = rendered(Chart(Seq(under, over), (0.0, 10.0), (0.0, 10.0)), 7, 7)
    val fills  = for
      row    <- 0 until 6
      column <- 1 until 7
      if buffer.get(column, row).symbol == "•"
    yield buffer.get(column, row).style.fg
    // the two series claim exactly the same cells, so every one of them must carry the second dataset's colour
    assert(fills.nonEmpty)
    assert(fills.forall(_.contains(Color.Blue)), fills.toString)

  test("a dataset on its own surface overdraws an earlier one at the same point"):
    val points = Seq((5.0, 5.0))
    val under  = Dataset("u", points, graphType = GraphType.Scatter, style = Style.fg(Color.Red))
    val over   =
      Dataset("o", points, graphType = GraphType.Scatter, style = Style.fg(Color.Blue), marker = Some("*"))
    val buffer = rendered(Chart(Seq(under, over), (0.0, 10.0), (0.0, 10.0)), 7, 7)
    val marked = for
      row    <- 0 until 6
      column <- 1 until 7
      if buffer.get(column, row).symbol != " "
    yield buffer.get(column, row).symbol
    assert(marked == Seq("*"), marked.toString)

  test("a dataset with no points and an override still leaves the axes drawn"):
    val empty  = Dataset("e", Seq.empty, resolution = Some(CanvasResolution.Braille), marker = Some("x"))
    val buffer = rendered(Chart(Seq(empty), (0.0, 1.0), (0.0, 1.0)), 5, 4)
    assert(trimmedLines(buffer) == Seq("│", "│", "│", "└────"))
