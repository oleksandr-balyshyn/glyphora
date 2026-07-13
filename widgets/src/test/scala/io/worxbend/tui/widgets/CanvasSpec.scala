package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class CanvasSpec extends AnyFunSuite:

  private val bounds = (0.0, 4.0)

  test("points map into the cell grid with y pointing up"):
    val canvas = Canvas(bounds, bounds, Seq(Shape.Points(Seq((0.0, 0.0), (4.0, 4.0)))), marker = "x")
    val buffer = rendered(canvas, 5, 5)
    assert(trimmedLines(buffer) == Seq("    x", "", "", "", "x"))

  test("points outside the bounds are dropped"):
    val canvas = Canvas(bounds, bounds, Seq(Shape.Points(Seq((9.0, 9.0), (-1.0, 0.0)))))
    val buffer = rendered(canvas, 5, 5)
    assert(trimmedLines(buffer).forall(_.isEmpty))

  test("a segment paints a gapless diagonal touching both endpoints"):
    val canvas = Canvas(bounds, bounds, Seq(Shape.SegmentShape(0.0, 0.0, 4.0, 4.0)), marker = "*")
    val buffer = rendered(canvas, 5, 5)
    val lines  = trimmedLines(buffer)
    assert(buffer.get(0, 4).symbol == "*") // start
    assert(buffer.get(4, 0).symbol == "*") // end
    assert(lines.forall(_.contains("*"))) // no vertical gaps (oversampled stepping may double cells)

  test("a polyline joins consecutive points"):
    val canvas = Canvas(bounds, bounds, Seq(Shape.Polyline(Seq((0.0, 0.0), (2.0, 4.0), (4.0, 0.0)))), marker = "*")
    val buffer = rendered(canvas, 5, 5)
    assert(trimmedLines(buffer).head == "  *")
    assert(trimmedLines(buffer).last == "*   *")

  test("a rectangle paints its outline"):
    val canvas = Canvas(bounds, bounds, Seq(Shape.RectangleShape(0.0, 0.0, 4.0, 4.0)), marker = "#")
    val buffer = rendered(canvas, 5, 5)
    assert(trimmedLines(buffer).head == "#####")
    assert(trimmedLines(buffer).last == "#####")
    assert(trimmedLines(buffer)(2) == "#   #")

  test("a circle stays within its radius"):
    val canvas = Canvas((0.0, 10.0), (0.0, 10.0), Seq(Shape.CircleShape(5.0, 5.0, 3.0)), marker = "o")
    val buffer = rendered(canvas, 11, 11)
    assert(buffer.get(5, 2).symbol == "o") // top of the circle: world (5, 8) maps to row 2
    assert(buffer.get(5, 5).symbol == " ") // center untouched
