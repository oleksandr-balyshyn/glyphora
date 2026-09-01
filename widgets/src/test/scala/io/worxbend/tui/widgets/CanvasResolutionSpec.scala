package io.worxbend.tui.widgets

import io.worxbend.tui.core.Style
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

/** The six sub-cell resolutions, checked one dot at a time.
  *
  * A glyph table that is off by one is invisible in a rendered picture — the line still looks like a line — so the
  * assertions here light *individual* dots and name the glyph each one must produce.
  */
final class CanvasResolutionSpec extends AnyFunSuite:

  private val everyResolution = CanvasResolution.values.toSeq

  /** Renders one cell whose dot grid is exactly `dotsPerCell`, with the listed dots lit. */
  private def cellWith(resolution: CanvasResolution, dots: (Int, Int)*): String =
    final case class LitDots() extends Shape:
      def draw(painter: Painter): Unit = dots.foreach((column, row) => painter.paintDot(column, row, Style.Default))
    val canvas = Canvas((0.0, 1.0), (0.0, 1.0), Seq(LitDots()), resolution = resolution)
    rendered(canvas, 1, 1).get(0, 0).symbol

  test("every resolution reports the dot packing its glyphs actually encode"):
    assert(SubCell.dotsPerCell(CanvasResolution.Cell) == (1, 1))
    assert(SubCell.dotsPerCell(CanvasResolution.HalfBlock) == (1, 2))
    assert(SubCell.dotsPerCell(CanvasResolution.Quadrant) == (2, 2))
    assert(SubCell.dotsPerCell(CanvasResolution.Sextant) == (2, 3))
    assert(SubCell.dotsPerCell(CanvasResolution.Braille) == (2, 4))
    assert(SubCell.dotsPerCell(CanvasResolution.Octant) == (2, 4))

  test("quadrant dots map to the corners of the quadrant blocks"):
    assert(cellWith(CanvasResolution.Quadrant, (0, 0)) == "▘")
    assert(cellWith(CanvasResolution.Quadrant, (1, 0)) == "▝")
    assert(cellWith(CanvasResolution.Quadrant, (0, 1)) == "▖")
    assert(cellWith(CanvasResolution.Quadrant, (1, 1)) == "▗")
    assert(cellWith(CanvasResolution.Quadrant, (0, 0), (1, 0)) == "▀")
    assert(cellWith(CanvasResolution.Quadrant, (0, 0), (0, 1)) == "▌")
    assert(cellWith(CanvasResolution.Quadrant, (1, 0), (0, 1)) == "▞")
    assert(cellWith(CanvasResolution.Quadrant, (0, 0), (1, 0), (0, 1), (1, 1)) == "█")

  test("sextant dots map to the 2x3 legacy-computing blocks"):
    assert(cellWith(CanvasResolution.Sextant, (0, 0)) == "🬀")
    assert(cellWith(CanvasResolution.Sextant, (1, 0)) == "🬁")
    assert(cellWith(CanvasResolution.Sextant, (0, 2), (1, 2)) == "🬭")
    // the two patterns Unicode did not encode twice: they reuse the half blocks
    assert(cellWith(CanvasResolution.Sextant, (0, 0), (0, 1), (0, 2)) == "▌")
    assert(cellWith(CanvasResolution.Sextant, (1, 0), (1, 1), (1, 2)) == "▐")
    val everyDot = for row <- 0 until 3; column <- 0 until 2 yield (column, row)
    assert(cellWith(CanvasResolution.Sextant, everyDot*) == "█")

  test("octant dots map to the 2x4 legacy-computing supplement blocks"):
    assert(cellWith(CanvasResolution.Octant, (0, 0)) == "𜺨")
    assert(cellWith(CanvasResolution.Octant, (0, 0), (1, 0)) == "🮂")
    // patterns that already had code points keep them: a quadrant is two octant rows
    assert(cellWith(CanvasResolution.Octant, (0, 0), (0, 1)) == "▘")
    assert(cellWith(CanvasResolution.Octant, (0, 2), (0, 3)) == "▖")
    val everyDot = for row <- 0 until 4; column <- 0 until 2 yield (column, row)
    assert(cellWith(CanvasResolution.Octant, everyDot*) == "█")

  test("octant and braille pack the same dots but draw solid rather than dotted"):
    val topLeftHalf = Seq((0, 0), (0, 1), (0, 2), (0, 3))
    assert(cellWith(CanvasResolution.Octant, topLeftHalf*) == "▌")
    assert(cellWith(CanvasResolution.Braille, topLeftHalf*) == "⡇")

  test("no resolution's glyph table can be indexed out of range"):
    // The tables live behind `glyphFor`; every mask a surface can produce must resolve to exactly one code point.
    everyResolution.foreach { resolution =>
      val (across, down) = SubCell.dotsPerCell(resolution)
      val masks          = 1 until (1 << (across * down))
      masks.foreach { mask =>
        val glyph = SubCell.glyphFor(resolution, mask, "•")
        assert(glyph.codePointCount(0, glyph.length) == 1, s"mask $mask at $resolution gave '$glyph'")
      }
    }

  test("every dot of every cell is reachable and distinct"):
    // A bit-layout mistake shows up as two dots sharing a bit, which this catches as a duplicate.
    everyResolution.foreach { resolution =>
      val (across, down) = SubCell.dotsPerCell(resolution)
      val bits = for row <- 0 until down; column <- 0 until across yield SubCell.bitFor(resolution, column, row)
      if resolution != CanvasResolution.Cell then
        assert(bits.distinct.size == bits.size, s"$resolution reuses a bit: $bits")
        assert(bits.forall(bit => bit > 0 && bit < (1 << (across * down))), s"$resolution has a stray bit: $bits")
    }

  test("a line drawn at each resolution fills the cells it crosses"):
    everyResolution.foreach { resolution =>
      val canvas = Canvas(
        (0.0, 1.0),
        (0.0, 1.0),
        Seq(Shape.SegmentShape(0.0, 0.5, 1.0, 0.5)),
        marker = "*",
        resolution = resolution,
      )
      val buffer = rendered(canvas, 12, 3)
      assert((0 until 12).forall(column => buffer.get(column, 1).symbol != " "), s"gap at $resolution")
    }

  test("a circle keeps its round-figure correction only where a dot is not square"):
    assert(SubCell.columnAspect(CanvasResolution.Cell) == 2)
    assert(SubCell.columnAspect(CanvasResolution.Quadrant) == 2)
    assert(SubCell.columnAspect(CanvasResolution.HalfBlock) == 1)
    assert(SubCell.columnAspect(CanvasResolution.Sextant) == 1)
    assert(SubCell.columnAspect(CanvasResolution.Braille) == 1)
    assert(SubCell.columnAspect(CanvasResolution.Octant) == 1)

  test("an empty area draws nothing at any resolution"):
    everyResolution.foreach { resolution =>
      val canvas =
        Canvas((0.0, 1.0), (0.0, 1.0), Seq(Shape.Points(Seq((0.5, 0.5)))), resolution = resolution)
      assert(trimmedLines(rendered(canvas, 0, 0)).forall(_.isEmpty), s"$resolution")
    }
