package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class RectOpsSpec extends AnyFunSuite:

  test("inner shrinks per-axis"):
    assert(Rect(0, 0, 10, 10).inner(2, 1) == Rect(2, 1, 6, 8))

  test("inner collapses to a zero-sized rect when an axis is exhausted"):
    assert(Rect(0, 0, 4, 10).inner(3, 0).isEmpty)

  test("offset moves without resizing"):
    assert(Rect(1, 2, 3, 4).offset(5, -1) == Rect(6, 1, 3, 4))

  test("centered places a smaller rect in the middle"):
    assert(Rect(0, 0, 10, 6).centered(4, 2) == Rect(3, 2, 4, 2))

  test("centered clamps to the outer bounds"):
    assert(Rect(0, 0, 4, 4).centered(10, 10) == Rect(0, 0, 4, 4))

  test("intersects is true only when the rects share a cell"):
    assert(Rect(0, 0, 4, 4).intersects(Rect(2, 2, 4, 4)))
    assert(!Rect(0, 0, 4, 4).intersects(Rect(4, 0, 4, 4)))

  test("union is the bounding box of both"):
    assert(Rect(0, 0, 2, 2).union(Rect(4, 4, 2, 2)) == Rect(0, 0, 6, 6))

  test("union with an empty rect returns the other"):
    assert(Rect.Zero.union(Rect(3, 3, 2, 2)) == Rect(3, 3, 2, 2))
