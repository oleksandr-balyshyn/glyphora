package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

/** The filled shapes: a segment carrying the area between it and a baseline, and the solid rectangle built on it. */
final class CanvasFillSpec extends AnyFunSuite:

  private val unit = (0.0, 1.0)

  test("a horizontal filled line fills every column from the line down to the baseline"):
    val canvas = Canvas(unit, unit, Seq(Shape.FilledLine(0.0, 0.5, 1.0, 0.5, 0.0)), marker = "#")
    assert(trimmedLines(rendered(canvas, 6, 5)) == Seq("", "", "######", "######", "######"))

  test("a baseline above the line fills upward"):
    val canvas = Canvas(unit, unit, Seq(Shape.FilledLine(0.0, 0.5, 1.0, 0.5, 1.0)), marker = "#")
    assert(trimmedLines(rendered(canvas, 6, 5)) == Seq("######", "######", "######", "", ""))

  test("a sloped filled line leaves no column empty and no gap in a column"):
    val canvas = Canvas(unit, unit, Seq(Shape.FilledLine(0.0, 0.0, 1.0, 1.0, 0.0)), marker = "#")
    val lines  = trimmedLines(rendered(canvas, 10, 10))
    (0 until 10).foreach { column =>
      val litRows = (0 until 10).filter(row => lines(row).lift(column).contains('#'))
      assert(litRows.nonEmpty, s"column $column empty")
      assert(litRows == (litRows.head to litRows.last), s"column $column has a gap: $litRows")
      assert(litRows.last == 9, s"column $column does not reach the baseline")
    }

  test("a steep filled line has no holes along its upper edge"):
    // A near-vertical span covers several rows in one column; taking only the mid-column row would dot the edge.
    val canvas = Canvas(unit, unit, Seq(Shape.FilledLine(0.4, 0.0, 0.6, 1.0, 0.0)), marker = "#")
    val lines  = trimmedLines(rendered(canvas, 11, 11))
    val lit    = (0 until 11).filter(column => lines.exists(_.lift(column).contains('#')))
    assert(lit == (lit.head to lit.last))

  test("a baseline outside the bounds fills to the edge instead of drawing nothing"):
    val canvas = Canvas(unit, unit, Seq(Shape.FilledLine(0.0, 0.5, 1.0, 0.5, -100.0)), marker = "#")
    assert(trimmedLines(rendered(canvas, 4, 5)) == Seq("", "", "####", "####", "####"))

  test("a filled line lying on its own baseline draws just the line"):
    val canvas = Canvas(unit, unit, Seq(Shape.FilledLine(0.0, 0.5, 1.0, 0.5, 0.5)), marker = "#")
    assert(trimmedLines(rendered(canvas, 4, 5)) == Seq("", "", "####", "", ""))

  test("a filled line is clipped like any other segment"):
    val canvas  = Canvas(unit, unit, Seq(Shape.FilledLine(-9.0, 0.5, 0.5, 0.5, 0.0)), marker = "#")
    val lines   = trimmedLines(rendered(canvas, 10, 5))
    assert(lines(2) == "######")
    assert(lines(4) == "######")
    val outside = Canvas(unit, unit, Seq(Shape.FilledLine(-9.0, 0.5, -8.0, 0.5, 0.0)), marker = "#")
    assert(trimmedLines(rendered(outside, 10, 5)).forall(_.isEmpty))

  test("non-finite coordinates and a non-finite baseline draw nothing"):
    Seq(
      Shape.FilledLine(0.0, 0.5, Double.NaN, 0.5, 0.0),
      Shape.FilledLine(0.0, 0.5, 1.0, 0.5, Double.PositiveInfinity),
      Shape.FilledLine(0.0, Double.NegativeInfinity, 1.0, 0.5, 0.0),
    ).foreach { shape =>
      assert(trimmedLines(rendered(Canvas(unit, unit, Seq(shape), marker = "#"), 6, 5)).forall(_.isEmpty), s"$shape")
    }

  test("a filled polyline joins and fills consecutive spans"):
    val points = Seq((0.0, 0.25), (0.5, 1.0), (1.0, 0.25))
    val canvas = Canvas(unit, unit, Seq(Shape.FilledPolyline(points, 0.0)), marker = "#")
    val lines  = trimmedLines(rendered(canvas, 9, 5))
    assert(lines.last == "#########") // the baseline row is solid under the whole span
    // the shape narrows toward the apex: the top row is a short run centred on the middle column
    assert(lines.head.count(_ == '#') < 4)
    assert(lines.head.indexOf('#') >= 3)

  test("a single-point filled polyline fills nothing, because a span needs two ends"):
    val canvas = Canvas(unit, unit, Seq(Shape.FilledPolyline(Seq((0.5, 0.5)), 0.0)), marker = "#")
    assert(trimmedLines(rendered(canvas, 6, 5)).forall(_.isEmpty))

  test("a filled rectangle paints its interior, unlike the outline shape"):
    val bounds  = (0.0, 4.0)
    val filled  = Canvas(bounds, bounds, Seq(Shape.FilledRectangle(1.0, 1.0, 2.0, 2.0)), marker = "#")
    assert(trimmedLines(rendered(filled, 5, 5)) == Seq("", " ###", " ###", " ###", ""))
    val outline = Canvas(bounds, bounds, Seq(Shape.RectangleShape(1.0, 1.0, 2.0, 2.0)), marker = "#")
    assert(trimmedLines(rendered(outline, 5, 5)) == Seq("", " ###", " # #", " ###", ""))

  test("a filled rectangle read from the opposite corner covers the same area"):
    val bounds                             = (0.0, 4.0)
    def linesOf(shape: Shape): Seq[String] =
      trimmedLines(rendered(Canvas(bounds, bounds, Seq(shape), marker = "#"), 5, 5))
    assert(linesOf(Shape.FilledRectangle(3.0, 3.0, -2.0, -2.0)) == linesOf(Shape.FilledRectangle(1.0, 1.0, 2.0, 2.0)))

  test("a filled rectangle with no extent still marks its line"):
    val bounds = (0.0, 4.0)
    val canvas = Canvas(bounds, bounds, Seq(Shape.FilledRectangle(1.0, 2.0, 2.0, 0.0)), marker = "#")
    assert(trimmedLines(rendered(canvas, 5, 5)) == Seq("", "", " ###", "", ""))

  test("a fill leaves no gaps at braille resolution"):
    val canvas =
      Canvas(unit, unit, Seq(Shape.FilledLine(0.0, 0.5, 1.0, 0.5, 0.0)), resolution = CanvasResolution.Braille)
    val buffer = rendered(canvas, 8, 4)
    // the bottom two cell rows are entirely below the line: every dot lit is the full braille cell
    assert((0 until 8).forall(column => buffer.get(column, 3).symbol == (0x2800 + 0xff).toChar.toString))

  test("degenerate canvases render without throwing"):
    val shape = Shape.FilledLine(0.0, 0.5, 1.0, 0.5, 0.0)
    assert(trimmedLines(rendered(Canvas(unit, unit, Seq(shape), marker = "#"), 0, 0)).forall(_.isEmpty))
    val flat  = Canvas((1.0, 1.0), unit, Seq(shape), marker = "#")
    assert(trimmedLines(rendered(flat, 5, 5)).forall(_.isEmpty))
    assert(trimmedLines(rendered(Canvas(unit, unit, Seq(shape), marker = "#"), 1, 1)).count(_.nonEmpty) == 1)

  test("a filled rectangle taller than the world still fills the visible part"):
    val world    = Canvas((0.0, 5.0), (0.0, 3.0), Seq(Shape.FilledRectangle(1.0, 0.0, 3.0, 20.0)), marker = "#")
    val inBounds = Canvas((0.0, 5.0), (0.0, 3.0), Seq(Shape.FilledRectangle(1.0, 0.0, 3.0, 3.0)), marker = "#")
    assert(trimmedLines(rendered(world, 6, 4)) == trimmedLines(rendered(inBounds, 6, 4)))

  test("a filled line entirely above the world still fills the whole canvas down to the baseline"):
    val canvas = Canvas(unit, unit, Seq(Shape.FilledLine(0.0, 5.0, 1.0, 5.0, 0.0)), marker = "#")
    assert(trimmedLines(rendered(canvas, 6, 3)) == Seq("######", "######", "######"))

  test("a filled line entirely below the world fills nothing above its baseline"):
    val canvas = Canvas(unit, unit, Seq(Shape.FilledLine(0.0, -5.0, 1.0, -5.0, 0.0)), marker = "#")
    assert(trimmedLines(rendered(canvas, 6, 3)) == Seq("", "", "######"))

  test("a filled line that leaves the top of the world keeps its sloped part sloped"):
    val canvas      = Canvas(unit, unit, Seq(Shape.FilledLine(0.0, 0.0, 1.0, 2.0, 0.0)), marker = "#")
    val lines       = trimmedLines(rendered(canvas, 10, 10))
    // the right half of the line is above the world, so those columns are full height; the left half still climbs
    val blankPrefix = lines(0).takeWhile(_ == ' ').length
    assert(blankPrefix > 2, s"the top row is filled too far left: '${lines(0)}'")
    assert(lines(0).drop(blankPrefix).forall(_ == '#'), s"the top row has a hole in it: '${lines(0)}'")
    (0 until 10).foreach(column => assert(lines(9).lift(column).contains('#'), s"column $column misses the baseline"))
