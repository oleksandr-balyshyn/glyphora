package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

/** Segments on a [[Canvas]]: clipped to the world bounds first, then stepped once per dot.
  *
  * Both halves used to be wrong in a way that compounded. The sample count came from the *world* delta, so a line
  * across normalized bounds got five samples and rendered as scattered dots; and points outside the bounds were dropped
  * one at a time, so a line arriving from off-screen spent nearly all of its samples outside the window and contributed
  * a couple of dots inside it.
  */
final class CanvasSegmentSpec extends AnyFunSuite:

  private val unit = (0.0, 1.0)

  /** Every column of `row` that holds something other than a blank. */
  private def litColumns(lines: Seq[String], row: Int): Seq[Int] =
    val line = lines.lift(row).getOrElse("")
    line.indices.filter(column => line.charAt(column) != ' ')

  test("a horizontal line across normalized bounds fills every column"):
    val canvas = Canvas(unit, unit, Seq(Shape.SegmentShape(0.0, 0.5, 1.0, 0.5)), marker = "*")
    val lines  = trimmedLines(rendered(canvas, 20, 5))
    assert(lines(2) == "*" * 20)

  test("the same line is drawn identically whatever the world scale"):
    def linesFor(scale: Double): Seq[String] =
      val bounds = (0.0, scale)
      val canvas =
        Canvas(bounds, bounds, Seq(Shape.SegmentShape(0.0, scale / 2, scale, scale / 2)), marker = "*")
      trimmedLines(rendered(canvas, 20, 5))
    assert(linesFor(1.0) == linesFor(1000.0))
    assert(linesFor(1.0) == linesFor(0.001))

  test("a braille line across normalized bounds leaves no blank cell"):
    val canvas =
      Canvas(unit, unit, Seq(Shape.SegmentShape(0.0, 0.5, 1.0, 0.5)), resolution = CanvasResolution.Braille)
    val buffer = rendered(canvas, 20, 3)
    assert((0 until 20).forall(column => buffer.get(column, 1).symbol != " "))

  test("a diagonal lights exactly one cell per column"):
    val canvas = Canvas(unit, unit, Seq(Shape.SegmentShape(0.0, 0.0, 1.0, 1.0)), marker = "*")
    val lines  = trimmedLines(rendered(canvas, 9, 9))
    // Bresenham steps along the longer axis, and on a square grid a 45-degree line has one dot per column.
    assert(lines.map(_.count(_ == '*')).sum == 9)
    assert((0 until 9).forall(row => litColumns(lines, row).size == 1))

  test("a segment arriving from far off-screen draws a solid run up to where it stops"):
    val canvas = Canvas((0.0, 10.0), (0.0, 10.0), Seq(Shape.SegmentShape(-1000.0, 5.0, 5.0, 5.0)), marker = "*")
    val lines  = trimmedLines(rendered(canvas, 11, 11))
    // world x 0..5 of 0..10 is the left half of an 11-cell row, inclusive of the midpoint.
    assert(lines(5) == "******")

  test("a segment wholly outside the bounds draws nothing"):
    val canvas = Canvas(unit, unit, Seq(Shape.SegmentShape(-5.0, -5.0, -1.0, -1.0)), marker = "*")
    assert(trimmedLines(rendered(canvas, 11, 11)).forall(_.isEmpty))

  test("a clipped segment paints nothing outside the canvas area"):
    val canvas = Canvas(unit, unit, Seq(Shape.SegmentShape(-5.0, 0.5, 5.0, 0.5)), marker = "*")
    val buffer = rendered(canvas, 10, 5)
    assert(trimmedLines(buffer)(2) == "*" * 10)
    assert(trimmedLines(buffer).count(_.nonEmpty) == 1)

  test("a segment covering a fraction of a wide world is still continuous"):
    // 0.0 to 0.4 of a unit world is the left 40% of the row: under world-unit sampling this got a single sample.
    val canvas = Canvas(unit, unit, Seq(Shape.SegmentShape(0.0, 0.5, 0.4, 0.5)), marker = "*")
    val lines  = trimmedLines(rendered(canvas, 20, 5))
    val lit    = litColumns(lines, 2)
    assert(lit.head == 0)
    assert(lit == (lit.head to lit.last)) // contiguous, no gaps
    assert(lit.last >= 7 && lit.last <= 8)

  test("an enormous world renders in bounded time and touches both ends"):
    val huge   = (0.0, 1e9)
    val canvas = Canvas(huge, huge, Seq(Shape.SegmentShape(0.0, 0.0, 1e9, 1e9)), marker = "*")
    val buffer = rendered(canvas, 5, 5)
    assert(buffer.get(0, 4).symbol == "*")
    assert(buffer.get(4, 0).symbol == "*")

  test("a segment with a non-finite endpoint is skipped rather than half drawn"):
    val cases = Seq(
      Shape.SegmentShape(0.0, 0.0, Double.PositiveInfinity, 1.0),
      Shape.SegmentShape(Double.NaN, 0.0, 1.0, 1.0),
      Shape.SegmentShape(0.0, Double.NegativeInfinity, 1.0, 1.0),
    )
    cases.foreach { segment =>
      val canvas = Canvas(unit, unit, Seq(segment), marker = "*")
      assert(trimmedLines(rendered(canvas, 5, 5)).forall(_.isEmpty), s"$segment drew something")
    }

  test("degenerate inputs render without throwing"):
    val zeroLength = Canvas(unit, unit, Seq(Shape.SegmentShape(0.5, 0.5, 0.5, 0.5)), marker = "*")
    assert(trimmedLines(rendered(zeroLength, 5, 5)).count(_.nonEmpty) == 1)
    val flatBounds = Canvas((1.0, 1.0), unit, Seq(Shape.SegmentShape(1.0, 0.0, 1.0, 1.0)), marker = "*")
    assert(trimmedLines(rendered(flatBounds, 5, 5)).forall(_.isEmpty))
    val noRoom     = Canvas(unit, unit, Seq(Shape.SegmentShape(0.0, 0.0, 1.0, 1.0)), marker = "*")
    assert(trimmedLines(rendered(noRoom, 0, 0)).forall(_.isEmpty))

  test("a polyline inherits the clipping and the dot-space stepping"):
    val points = Seq((-3.0, 0.5), (0.5, 0.5), (0.5, 1.0))
    val canvas = Canvas(unit, unit, Seq(Shape.Polyline(points)), marker = "*")
    val lines  = trimmedLines(rendered(canvas, 11, 5))
    val lit    = litColumns(lines, 2)
    assert(lit == (0 to 5)) // solid run from the clipped left edge to the corner
    assert(litColumns(lines, 0).contains(5)) // the vertical leg reaches the top
