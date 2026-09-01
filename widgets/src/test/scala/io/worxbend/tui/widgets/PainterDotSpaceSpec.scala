package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Rect, Style}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

/** The dot-space half of [[Painter]]: the mapping a user-written [[Shape]] uses when it scan-converts instead of
  * sampling its outline in world units.
  */
final class PainterDotSpaceSpec extends AnyFunSuite:

  private val unit = (0.0, 1.0)

  private def painterOn(width: Int, height: Int, resolution: CanvasResolution): Painter =
    Painter(Rect(0, 0, width, height), unit, unit, resolution, "•")

  test("dotSize reports the surface extent, not the cell extent"):
    assert(painterOn(4, 3, CanvasResolution.Cell).dotSize == (4, 3))
    assert(painterOn(4, 3, CanvasResolution.HalfBlock).dotSize == (4, 6))
    assert(painterOn(4, 3, CanvasResolution.Braille).dotSize == (8, 12))

  test("bounds hands back exactly the world rectangle the canvas was given"):
    assert(
      Painter(Rect(0, 0, 2, 2), (-1.0, 5.0), (2.0, 8.0), CanvasResolution.Cell, "•").bounds ==
        ((-1.0, 5.0), (2.0, 8.0))
    )

  test("getPoint puts world y-max at dot row zero, at every resolution"):
    List(CanvasResolution.Cell, CanvasResolution.HalfBlock, CanvasResolution.Braille).foreach { resolution =>
      val painter         = painterOn(4, 3, resolution)
      val (columns, rows) = painter.dotSize
      assert(painter.getPoint(0.0, 1.0) == Some((0, 0)), s"top-left at $resolution")
      assert(painter.getPoint(1.0, 1.0) == Some((columns - 1, 0)), s"top-right at $resolution")
      assert(painter.getPoint(0.0, 0.0) == Some((0, rows - 1)), s"bottom-left at $resolution")
      assert(painter.getPoint(1.0, 0.0) == Some((columns - 1, rows - 1)), s"bottom-right at $resolution")
    }

  test("getPoint refuses points outside the bounds"):
    val painter = painterOn(4, 3, CanvasResolution.Cell)
    assert(painter.getPoint(1.5, 0.5).isEmpty)
    assert(painter.getPoint(-0.5, 0.5).isEmpty)
    assert(painter.getPoint(0.5, 2.0).isEmpty)

  test("getPoint refuses non-finite coordinates by contract, not by accident"):
    val painter = painterOn(4, 3, CanvasResolution.Cell)
    assert(painter.getPoint(Double.NaN, 0.5).isEmpty)
    assert(painter.getPoint(0.5, Double.NaN).isEmpty)
    assert(painter.getPoint(Double.PositiveInfinity, 0.5).isEmpty)
    assert(painter.getPoint(0.5, Double.NegativeInfinity).isEmpty)

  test("getPoint refuses degenerate bounds and an empty area"):
    val flat  = Painter(Rect(0, 0, 4, 3), (2.0, 2.0), unit, CanvasResolution.Cell, "•")
    assert(flat.getPoint(2.0, 0.5).isEmpty)
    val empty = painterOn(0, 0, CanvasResolution.Braille)
    assert(empty.dotSize == (0, 0))
    assert(empty.getPoint(0.5, 0.5).isEmpty)

  test("paint and getPoint plus paintDot agree cell for cell"):
    val points = Seq((0.0, 1.0), (0.37, 0.62), (1.0, 0.0), (0.5, 0.5))

    final case class ViaWorld(style: Style) extends Shape:
      def draw(painter: Painter): Unit = points.foreach((x, y) => painter.paint(x, y, style))

    final case class ViaDots(style: Style) extends Shape:
      def draw(painter: Painter): Unit =
        points.foreach((x, y) => painter.getPoint(x, y).foreach((c, r) => painter.paintDot(c, r, style)))

    val world = rendered(Canvas(unit, unit, Seq(ViaWorld(Style.Default)), "*"), 7, 5)
    val dots  = rendered(Canvas(unit, unit, Seq(ViaDots(Style.Default)), "*"), 7, 5)
    assert(trimmedLines(world) == trimmedLines(dots))
    assert(trimmedLines(world).exists(_.contains("*")))

  test("paintDot drops dots off the grid instead of clamping them to the edge"):
    final case class OffGrid() extends Shape:
      def draw(painter: Painter): Unit =
        val (columns, rows) = painter.dotSize
        painter.paintDot(-1, 0, Style.Default)
        painter.paintDot(0, -1, Style.Default)
        painter.paintDot(columns, 0, Style.Default)
        painter.paintDot(0, rows, Style.Default)

    assert(trimmedLines(rendered(Canvas(unit, unit, Seq(OffGrid()), "*"), 5, 4)).forall(_.isEmpty))

  test("a shape can scan-convert a filled box in dot space"):
    // The whole point of the dot-space API: fill exactly the dots inside the world box, once each, with no sample
    // count guessed in world units. Braille packs 2x4, so a 2x1 cell area is a 4x4 dot grid.
    final case class FilledBox(x1: Double, y1: Double, x2: Double, y2: Double) extends Shape:
      def draw(painter: Painter): Unit =
        val corners =
          for
            (c1, r1) <- painter.getPoint(x1, y1)
            (c2, r2) <- painter.getPoint(x2, y2)
          yield (c1, r1, c2, r2)
        corners.foreach { (c1, r1, c2, r2) =>
          for
            column <- math.min(c1, c2) to math.max(c1, c2)
            row    <- math.min(r1, r2) to math.max(r1, r2)
          do painter.paintDot(column, row, Style.Default)
        }

    val canvas = Canvas(unit, unit, Seq(FilledBox(0.0, 0.0, 1.0, 1.0)), resolution = CanvasResolution.Braille)
    val buffer = rendered(canvas, 2, 1)
    // every one of the eight dots in each cell lit: mask 0xFF
    assert(buffer.get(0, 0).symbol == (0x2800 + 0xff).toChar.toString)
    assert(buffer.get(1, 0).symbol == (0x2800 + 0xff).toChar.toString)
