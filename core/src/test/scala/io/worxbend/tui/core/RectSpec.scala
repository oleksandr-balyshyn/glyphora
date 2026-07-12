package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class RectSpec extends AnyFunSuite:

  test("area is width times height"):
    assert(Rect(1, 2, 3, 4).area == 12)

  test("a rect with zero width or height is empty"):
    assert(Rect(0, 0, 0, 5).isEmpty)
    assert(Rect(0, 0, 5, 0).isEmpty)
    assert(!Rect(0, 0, 1, 1).isEmpty)

  test("intersection of overlapping rects is the shared region"):
    assert(Rect(0, 0, 10, 10).intersection(Rect(5, 5, 10, 10)) == Rect(5, 5, 5, 5))

  test("intersection of disjoint rects is empty"):
    assert(Rect(0, 0, 3, 3).intersection(Rect(10, 10, 3, 3)).isEmpty)

  test("contains covers the top-left corner and excludes the exclusive edges"):
    val rect = Rect(2, 3, 4, 5)
    assert(rect.contains(Position(2, 3)))
    assert(rect.contains(Position(5, 7)))
    assert(!rect.contains(Position(6, 3)))
    assert(!rect.contains(Position(2, 8)))

  test("inset shrinks every side by the margin"):
    assert(Rect(0, 0, 10, 10).inset(1) == Rect(1, 1, 8, 8))

  test("inset collapses to empty when the margin exhausts the rect"):
    assert(Rect(0, 0, 2, 2).inset(1).isEmpty)

  test("a rect from a size sits at the origin"):
    assert(Rect(Size(80, 24)) == Rect(0, 0, 80, 24))
