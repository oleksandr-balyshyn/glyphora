package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Color, Rect, Style}

import org.scalatest.funsuite.AnyFunSuite

/** Half-block resolution carries *two* colours per cell, which is the reason to choose it over a finer resolution.
  *
  * A terminal cell has one foreground and one background colour. `▀` fills its top half with the foreground and leaves
  * the bottom half showing the background, so a half-block cell is genuinely two coloured pixels — where a braille cell
  * packs eight dots that must all share one colour. Two stacked points used to collapse to whichever was drawn second.
  */
final class CanvasHalfBlockColourSpec extends AnyFunSuite:

  private val unit = (0.0, 1.0)

  /** Renders one cell, drawing each `(y, style)` in turn so the last writer of a half is the last in the list. */
  private def cellFor(points: (Double, Style)*): Cell =
    final case class Plot() extends Shape:
      def draw(painter: Painter): Unit = points.foreach((y, style) => painter.paint(0.0, y, style))
    val buffer = Buffer(Rect(0, 0, 1, 1))
    Canvas(unit, unit, Seq(Plot()), resolution = CanvasResolution.HalfBlock).render(Rect(0, 0, 1, 1), buffer)
    buffer.get(0, 0)

  private val red  = Style.Default.withFg(Color.Red)
  private val blue = Style.Default.withFg(Color.Blue)

  test("two differently coloured points in one cell keep both colours"):
    val cell = cellFor(1.0 -> red, 0.0 -> blue)
    assert(cell.symbol == "▀")
    assert(cell.style.fg.contains(Color.Red)) // the upper half
    assert(cell.style.bg.contains(Color.Blue)) // the lower half, moved into the background

  test("the order the two halves are drawn in does not matter"):
    assert(cellFor(0.0 -> blue, 1.0 -> red) == cellFor(1.0 -> red, 0.0 -> blue))

  test("two points of the same colour become a solid block with no background"):
    val cell = cellFor(1.0 -> red, 0.0 -> red)
    assert(cell.symbol == "█")
    assert(cell.style.fg.contains(Color.Red))
    assert(cell.style.bg.isEmpty)

  test("one lit half leaves the other half's colour alone"):
    val upperOnly = cellFor(1.0 -> red)
    assert(upperOnly.symbol == "▀")
    assert(upperOnly.style.fg.contains(Color.Red))
    assert(upperOnly.style.bg.isEmpty)
    val lowerOnly = cellFor(0.0 -> blue)
    assert(lowerOnly.symbol == "▄")
    assert(lowerOnly.style.fg.contains(Color.Blue))
    assert(lowerOnly.style.bg.isEmpty)

  test("a lower half with no colour of its own does not invent a background"):
    // There is nothing to move into the background, so the pair falls back to the solid block.
    val cell = cellFor(1.0 -> red, 0.0 -> Style.Default)
    assert(cell.symbol == "█")
    assert(cell.style.fg.contains(Color.Red))
    assert(cell.style.bg.isEmpty)

  test("two points in the same half still take the last writer"):
    val cell = cellFor(1.0 -> red, 1.0 -> blue)
    assert(cell.symbol == "▀")
    assert(cell.style.fg.contains(Color.Blue))

  test("the finer resolutions still give a cell one colour, as they must"):
    // Their glyphs have no second colour to put anything in, so last-writer-wins is still the only answer there.
    Seq(CanvasResolution.Braille, CanvasResolution.Quadrant, CanvasResolution.Octant).foreach { resolution =>
      final case class Plot() extends Shape:
        def draw(painter: Painter): Unit =
          painter.paint(0.0, 1.0, red)
          painter.paint(0.0, 0.0, blue)
      val buffer = Buffer(Rect(0, 0, 1, 1))
      Canvas(unit, unit, Seq(Plot()), resolution = resolution).render(Rect(0, 0, 1, 1), buffer)
      assert(buffer.get(0, 0).style.fg.contains(Color.Blue), s"$resolution")
      assert(buffer.get(0, 0).style.bg.isEmpty, s"$resolution")
    }

  test("an empty area draws nothing and allocates no slot to write into"):
    val buffer = Buffer(Rect(0, 0, 0, 0))
    val canvas =
      Canvas(unit, unit, Seq(Shape.Points(Seq((0.5, 0.5)))), resolution = CanvasResolution.HalfBlock)
    canvas.render(Rect(0, 0, 0, 0), buffer)
    succeed
