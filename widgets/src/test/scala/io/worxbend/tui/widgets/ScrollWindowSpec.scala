package io.worxbend.tui.widgets

import org.scalatest.funsuite.AnyFunSuite

/** Pure arithmetic, no buffer: [[ScrollWindow.offsetFor]] is the one rule every scrolling widget in this module shares,
  * so a change to it is felt in six places at once and is worth pinning down on its own.
  */
final class ScrollWindowSpec extends AnyFunSuite:

  test("with no selection the offset is only clamped into range"):
    assert(ScrollWindow.offsetFor(offset = 99, selected = None, total = 20, viewportHeight = 5) == 15)
    assert(ScrollWindow.offsetFor(offset = -3, selected = None, total = 20, viewportHeight = 5) == 0)
    assert(ScrollWindow.offsetFor(offset = 4, selected = None, total = 20, viewportHeight = 5) == 4)

  test("without padding the selection is allowed to rest on the first and last visible rows"):
    // the window is rows 4..8; selecting row 8 keeps it there rather than scrolling to give the reader a look ahead
    assert(ScrollWindow.offsetFor(offset = 4, selected = Some(8), total = 20, viewportHeight = 5) == 4)
    assert(ScrollWindow.offsetFor(offset = 4, selected = Some(4), total = 20, viewportHeight = 5) == 4)
    // one past either edge scrolls by exactly one row
    assert(ScrollWindow.offsetFor(offset = 4, selected = Some(9), total = 20, viewportHeight = 5) == 5)
    assert(ScrollWindow.offsetFor(offset = 4, selected = Some(3), total = 20, viewportHeight = 5) == 3)

  test("padding starts the scroll before the selection reaches the edge"):
    // window rows 0..9 over 50 items. With padding 2 the selection may sit as low as row 7; row 8 pulls the list up.
    assert(ScrollWindow.offsetFor(offset = 0, selected = Some(7), total = 50, viewportHeight = 10, padding = 2) == 0)
    assert(ScrollWindow.offsetFor(offset = 0, selected = Some(8), total = 50, viewportHeight = 10, padding = 2) == 1)
    assert(ScrollWindow.offsetFor(offset = 0, selected = Some(9), total = 50, viewportHeight = 10, padding = 2) == 2)

  test("padding is symmetric: scrolling back up also stops short of the top edge"):
    assert(ScrollWindow.offsetFor(offset = 10, selected = Some(12), total = 50, viewportHeight = 10, padding = 2) == 10)
    assert(ScrollWindow.offsetFor(offset = 10, selected = Some(11), total = 50, viewportHeight = 10, padding = 2) == 9)

  test("padding degrades at the ends of the list, where there is nothing left to reveal"):
    // nothing exists above item 0, so the selection legitimately sits on the top row and the offset stays 0
    assert(ScrollWindow.offsetFor(offset = 0, selected = Some(0), total = 50, viewportHeight = 10, padding = 2) == 0)
    assert(ScrollWindow.offsetFor(offset = 0, selected = Some(1), total = 50, viewportHeight = 10, padding = 2) == 0)
    // and the last item can reach the bottom row: the offset cannot exceed total - viewportHeight
    assert(ScrollWindow.offsetFor(offset = 40, selected = Some(49), total = 50, viewportHeight = 10, padding = 2) == 40)

  test("a padding larger than the window is capped instead of oscillating"):
    // ask for 50 rows of padding in a 5-row window; the answer must be a fixed point, not a value that flips each frame
    val once  = ScrollWindow.offsetFor(offset = 0, selected = Some(20), total = 50, viewportHeight = 5, padding = 50)
    val twice = ScrollWindow.offsetFor(once, selected = Some(20), total = 50, viewportHeight = 5, padding = 50)
    assert(once == twice)
    assert(once >= 0 && once <= 45)

  test("a negative padding is treated as none at all"):
    val padded = ScrollWindow.offsetFor(offset = 4, selected = Some(8), total = 20, viewportHeight = 5, padding = -7)
    assert(padded == ScrollWindow.offsetFor(offset = 4, selected = Some(8), total = 20, viewportHeight = 5))

  test("a selection index outside the content still yields an offset inside it"):
    // callers index their content with the result directly, so an out-of-range selection must not produce a bad offset
    assert(ScrollWindow.offsetFor(offset = 0, selected = Some(999), total = 20, viewportHeight = 5) == 15)
    assert(ScrollWindow.offsetFor(offset = 0, selected = Some(-4), total = 20, viewportHeight = 5) == 0)

  test("content shorter than the viewport never scrolls"):
    assert(ScrollWindow.offsetFor(offset = 3, selected = Some(2), total = 3, viewportHeight = 10, padding = 4) == 0)

  test("an empty viewport or empty content answers zero"):
    assert(ScrollWindow.offsetFor(offset = 5, selected = Some(2), total = 0, viewportHeight = 5) == 0)
    assert(ScrollWindow.offsetFor(offset = 5, selected = Some(2), total = 20, viewportHeight = 0) == 2)
