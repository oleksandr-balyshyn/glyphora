package io.worxbend.tui.runtime

import io.worxbend.tui.core.{Rect, Size}

import org.scalatest.funsuite.AnyFunSuite

/** Covers [[Viewport.areaIn]]: the pure arithmetic that turns a terminal size into the rectangle a frame is composed
  * into. Every clamp here is reachable by a user dragging their terminal window, not only by a bad argument.
  */
final class ViewportSpec extends AnyFunSuite:

  test("a full-screen viewport is the whole terminal"):
    assert(Viewport.Fullscreen.areaIn(Size(80, 24)) == Rect(0, 0, 80, 24))

  test("an inline viewport is anchored to the bottom rows"):
    assert(Viewport.Inline(5).areaIn(Size(80, 24)) == Rect(0, 19, 80, 5))

  test("a strip taller than the terminal becomes the whole terminal"):
    // What a user shrinking their window produces. A subtraction without the clamp would give a negative origin here,
    // and the composer would be handed a rectangle that starts above the top of the screen.
    assert(Viewport.Inline(40).areaIn(Size(80, 24)) == Rect(0, 0, 80, 24))

  test("a strip of no rows is an empty rectangle at the bottom edge"):
    val area = Viewport.Inline(0).areaIn(Size(80, 24))
    assert(area == Rect(0, 24, 80, 0))
    assert(area.isEmpty)

  test("a negative number of rows is treated as none rather than rejected"):
    assert(Viewport.Inline(-3).areaIn(Size(80, 24)).isEmpty)

  test("a one-row terminal still yields a one-row strip"):
    assert(Viewport.Inline(3).areaIn(Size(20, 1)) == Rect(0, 0, 20, 1))

  test("a terminal of no rows yields an empty rectangle rather than a negative one"):
    assert(Viewport.Inline(3).areaIn(Size(20, 0)) == Rect(0, 0, 20, 0))

  test("only a full-screen run reserves nothing"):
    assert(Viewport.Fullscreen.reservedRows == 0)
    assert(Viewport.Inline(4).reservedRows == 4)
    assert(Viewport.Inline(-1).reservedRows == 0)
