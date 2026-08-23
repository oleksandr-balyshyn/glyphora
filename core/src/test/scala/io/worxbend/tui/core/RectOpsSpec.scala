package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class RectOpsSpec extends AnyFunSuite:

  test("inset shrinks per-axis"):
    assert(Rect(0, 0, 10, 10).inset(2, 1) == Rect(2, 1, 6, 8))

  test("inset with one margin shrinks both axes by it"):
    assert(Rect(0, 0, 10, 10).inset(2) == Rect(2, 2, 6, 6))

  test("inset collapses to a zero-sized rect when an axis is exhausted"):
    assert(Rect(0, 0, 4, 10).inset(3, 0).isEmpty)

  test("inset collapses both axes, at the centre, even when only one is exhausted"):
    // the surviving axis is not kept: a rect with zero height covers no cells, so 100 surviving columns are 100
    // columns nobody can render into. The centre is where an overlay aimed at the leftover expects to find it.
    assert(Rect(0, 0, 100, 1).inset(0, 1) == Rect(50, 0, 0, 0))

  test("offset moves without resizing"):
    assert(Rect(1, 2, 3, 4).offset(5, -1) == Rect(6, 1, 3, 4))

  test("centered places a smaller rect in the middle"):
    assert(Rect(0, 0, 10, 6).centered(4, 2) == Rect(3, 2, 4, 2))

  test("centered clamps to the outer bounds"):
    assert(Rect(0, 0, 4, 4).centered(10, 10) == Rect(0, 0, 4, 4))

  test("intersects is true only when the rects share a cell"):
    assert(Rect(0, 0, 4, 4).intersects(Rect(2, 2, 4, 4)))
    assert(!Rect(0, 0, 4, 4).intersects(Rect(4, 0, 4, 4)))

  test("a zero-sized rect intersects nothing, however it is positioned"):
    // the four half-plane inequalities said a zero-sized rect inside another one overlapped it, while `intersection`
    // of the same pair returned an empty rect. Layout produces zero-sized segments routinely, so the two must agree.
    assert(!Rect(5, 5, 0, 0).intersects(Rect(0, 0, 10, 10)))
    assert(Rect(5, 5, 0, 0).intersection(Rect(0, 0, 10, 10)).isEmpty)
    assert(!Rect.Zero.intersects(Rect.Zero))

  test("position and size split a rect into where it sits and how big it is"):
    val rect = Rect(2, 3, 4, 5)
    assert(rect.position == Position(2, 3))
    assert(rect.size == Size(4, 5))
    // and the two halves put back together are the rect again
    assert(Rect(rect.position.x, rect.position.y, rect.size.width, rect.size.height) == rect)

  test("contains agrees whether it is given a Position or a coordinate pair"):
    val rect = Rect(2, 3, 4, 5)
    assert(rect.contains(2, 3) == rect.contains(Position(2, 3)))
    assert(rect.contains(5, 7) && !rect.contains(6, 7))
    assert(!rect.contains(1, 3) && !rect.contains(2, 2))

  test("union is the bounding box of both"):
    assert(Rect(0, 0, 2, 2).union(Rect(4, 4, 2, 2)) == Rect(0, 0, 6, 6))

  test("union with an empty rect returns the other"):
    assert(Rect.Zero.union(Rect(3, 3, 2, 2)) == Rect(3, 3, 2, 2))
