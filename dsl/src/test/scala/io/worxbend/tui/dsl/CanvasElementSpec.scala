package io.worxbend.tui.dsl

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}
import io.worxbend.tui.widgets as w

import org.scalatest.funsuite.AnyFunSuite

/** The `canvas(...)` factory used to hard-wire the widget's marker and resolution, so a view could only ever get the
  * coarsest drawing mode. These tests pin that the three builders reach the widget.
  */
final class CanvasElementSpec extends AnyFunSuite:

  /** A single world point at the origin of a 1x1 window, so exactly one cell is painted whatever the resolution. */
  private val dot: w.Shape = w.Shape.Points(Seq((0.0, 0.0)))

  private def glyphs(element: CanvasElement, width: Int, height: Int): String =
    trimmedLines(rendered(element.widget, width, height)).mkString

  test("the default canvas paints one marker glyph per hit cell"):
    assert(glyphs(canvas((0.0, 1.0), (0.0, 1.0))(dot), 4, 2) == "•")

  test("markers replaces the glyph and stays in cell resolution"):
    val element = canvas((0.0, 1.0), (0.0, 1.0))(dot).markers("*")
    assert(element.resolution == w.CanvasResolution.Cell)
    assert(glyphs(element, 4, 2) == "*")

  test("halfBlocks and braille pick the sub-cell resolutions the widget supports"):
    val half = canvas((0.0, 1.0), (0.0, 1.0))(dot).halfBlocks
    assert(half.resolution == w.CanvasResolution.HalfBlock)
    assert(glyphs(half, 4, 2).forall(character => "▀▄█".contains(character)))

    val braille = canvas((0.0, 1.0), (0.0, 1.0))(dot).braille
    assert(braille.resolution == w.CanvasResolution.Braille)
    // U+2800 is the empty braille pattern; every filled pattern is the next 255 code points.
    assert(glyphs(braille, 4, 2).forall(character => character > '⠀' && character <= '⣿'))

  test("a marker wider than one column is refused by the widget rather than smeared across two cells"):
    // A CJK ideograph is two columns wide. The canvas substitutes its fallback marker instead of overrunning.
    assert(glyphs(canvas((0.0, 1.0), (0.0, 1.0))(dot).markers("漢"), 4, 2) != "漢")

  test("builders are order-independent and keep the element's own type"):
    val element: CanvasElement = canvas((0.0, 1.0), (0.0, 1.0))(dot).braille.markers("+")
    assert(element.marker == "+" && element.resolution == w.CanvasResolution.Cell)
    assert(canvas((0.0, 1.0), (0.0, 1.0))(dot).markers("+").braille.marker == "+")

  test("an empty area paints nothing and does not throw"):
    assert(glyphs(canvas((0.0, 1.0), (0.0, 1.0))(dot).braille, 0, 0).isEmpty)
