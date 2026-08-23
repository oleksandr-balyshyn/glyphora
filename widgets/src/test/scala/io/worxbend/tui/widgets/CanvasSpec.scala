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

  test("braille resolution packs sub-pixels into braille glyphs"):
    val canvas =
      Canvas((0.0, 1.0), (0.0, 3.0), Seq(Shape.Points(Seq((0.0, 3.0)))), resolution = CanvasResolution.Braille)
    assert(rendered(canvas, 1, 1).get(0, 0).symbol == "⠁") // top-left dot only

  test("braille accumulates multiple dots in one cell"):
    val points = Shape.Points(Seq((0.0, 3.0), (0.0, 2.0), (1.0, 0.0)))
    val canvas = Canvas((0.0, 1.0), (0.0, 3.0), Seq(points), resolution = CanvasResolution.Braille)
    // dots 1 (0,0), 2 (0,1) and 8 (1,3): 0x01 | 0x02 | 0x80 = 0x83
    assert(rendered(canvas, 1, 1).get(0, 0).symbol == (0x2800 + 0x83).toChar.toString)

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

  test("a marker wider than one column is refused rather than smeared into the next cell"):
    // at Cell resolution the marker *is* the glyph, and the neighbouring column belongs to the cell next door — which
    // this same canvas may also be drawing into. A two-column marker is replaced by the fallback rather than clipped.
    val canvas = Canvas(bounds, bounds, Seq(Shape.Points(Seq((0.0, 0.0)))), marker = "🙂")
    val buffer = rendered(canvas, 5, 5)
    assert(trimmedLines(buffer) == Seq("", "", "", "", SubCell.FallbackMarker))
