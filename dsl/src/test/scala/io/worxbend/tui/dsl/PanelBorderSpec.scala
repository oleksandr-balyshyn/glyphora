package io.worxbend.tui.dsl

import io.worxbend.tui.testsupport.BufferAssertions.{line as bufferLine, rendered}
import io.worxbend.tui.widgets as w

import org.scalatest.funsuite.AnyFunSuite

/** `w.BorderType` has had four members for a while, but only three of them were reachable from the DSL and none of
  * them could be chosen from a value. These tests pin the top-left corner glyph each builder produces.
  */
final class PanelBorderSpec extends AnyFunSuite:

  private def topLeftCorner(element: Element): Char =
    bufferLine(rendered(element.widget, 6, 3), 0).charAt(0)

  test("the four border glyph sets each draw their own corner"):
    assert(topLeftCorner(panel(text("x"))) == '┌')
    assert(topLeftCorner(panel(text("x")).rounded) == '╭')
    assert(topLeftCorner(panel(text("x")).doubleBorder) == '╔')
    assert(topLeftCorner(panel(text("x")).thick) == '┏')

  test("borderType picks a set from a value, so the named builders are shorthands for it"):
    assert(panel(text("x")).borderType(w.BorderType.Thick) == panel(text("x")).thick)
    assert(topLeftCorner(panel(text("x")).borderType(w.BorderType.Rounded)) == '╭')

  test("the last border choice wins, whichever order the builders are written in"):
    assert(topLeftCorner(panel(text("x")).thick.rounded) == '╭')
    assert(topLeftCorner(panel(text("x")).rounded.thick) == '┏')

  test("a thick border leaves exactly as much room inside as a plain one"):
    // All four glyph sets are one column wide, so `inner` cannot move.
    assert(panel(text("x")).thick.intrinsicHeight(6) == panel(text("x")).intrinsicHeight(6))

  test("an area too small for a border does not throw"):
    assert(rendered(panel(text("x")).thick.widget, 1, 1).area.width == 1)
