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

  test("clamp leaves a rect that already fits alone"):
    val container = Rect(0, 0, 80, 24)
    assert(Rect(10, 5, 20, 6).clamp(container) == Rect(10, 5, 20, 6))

  test("clamp slides a rect back inside instead of cropping it"):
    val container = Rect(0, 0, 80, 24)
    val popup     = Rect(45, 20, 40, 10)
    assert(popup.clamp(container) == Rect(40, 14, 40, 10))
    // the contrast with intersection, which crops: same area before and after for clamp, smaller for intersection
    assert(popup.clamp(container).area == popup.area)
    assert(popup.intersection(container) == Rect(45, 20, 35, 4))

  test("clamp pushes a rect hanging off the top-left corner back to the container origin"):
    val container = Rect(5, 3, 20, 10)
    assert(Rect(-4, -1, 6, 4).clamp(container) == Rect(5, 3, 6, 4))

  test("clamp shrinks a rect larger than the container to the container"):
    val container = Rect(5, 3, 20, 10)
    val result    = Rect(0, 0, 100, 100).clamp(container)
    assert(result == container)

  test("clamp always lands inside a non-empty container"):
    val container  = Rect(5, 3, 20, 10)
    val candidates =
      for
        x <- -10 to 40
        y <- -10 to 30
      yield Rect(x, y, 7, 4).clamp(container)
    assert(candidates.forall(r => r.x >= container.x && r.right <= container.right))
    assert(candidates.forall(r => r.y >= container.y && r.bottom <= container.bottom))

  test("clamp against an empty container yields a zero-sized rect at that container's origin"):
    val result = Rect(9, 9, 40, 10).clamp(Rect(4, 4, 0, 0))
    assert(result == Rect(4, 4, 0, 0))
    assert(result.isEmpty)
