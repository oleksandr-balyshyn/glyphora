package io.worxbend.tui.widgets

import org.scalatest.funsuite.AnyFunSuite

final class PaddingSpec extends AnyFunSuite:

  test("each single-side constructor fills exactly its own side"):
    assert(Padding.left(2) == Padding(left = 2, right = 0, top = 0, bottom = 0))
    assert(Padding.right(2) == Padding(left = 0, right = 2, top = 0, bottom = 0))
    assert(Padding.top(2) == Padding(left = 0, right = 0, top = 2, bottom = 0))
    assert(Padding.bottom(2) == Padding(left = 0, right = 0, top = 0, bottom = 2))

  test("a single side costs cells on one axis only"):
    assert(Padding.left(3).horizontalCells == 3)
    assert(Padding.left(3).verticalCells == 0)
    assert(Padding.top(3).verticalCells == 3)
    assert(Padding.top(3).horizontalCells == 0)

  test("a negative count costs nothing, like every other padding"):
    assert(Padding.left(-1).horizontalCells == 0)
    assert(Padding.bottom(-1).verticalCells == 0)
