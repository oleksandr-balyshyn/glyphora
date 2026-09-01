package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class RectSpec extends AnyFunSuite:

  test("area is width times height"):
    assert(Rect(1, 2, 3, 4).area == 12)

  test("a rect with zero width or height is empty"):
    assert(Rect(0, 0, 0, 5).isEmpty)
    assert(Rect(0, 0, 5, 0).isEmpty)
    assert(!Rect(0, 0, 1, 1).isEmpty)

  test("a negatively sized rect is empty and has no area"):
    // widgets guard with `if !area.isEmpty`; a negative extent from arithmetic upstream used to pass that guard
    // and then report a negative area, so a widget rendered into a region that does not exist
    assert(Rect(0, 0, -4, 4).isEmpty)
    assert(Rect(0, 0, -4, 4).area == 0)
    assert(Rect(0, 0, 4, -4).isEmpty)
    assert(!Rect(0, 0, -4, 4).contains(Position(0, 0)))

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

  test("rows yields one single-row rect per row, top to bottom"):
    assert(Rect(3, 5, 4, 3).rows.toList == List(Rect(3, 5, 4, 1), Rect(3, 6, 4, 1), Rect(3, 7, 4, 1)))

  test("columns yields one single-column rect per column, left to right"):
    assert(Rect(3, 5, 2, 4).columns.toList == List(Rect(3, 5, 1, 4), Rect(4, 5, 1, 4)))

  test("rows and columns of an empty or negatively sized rect are empty"):
    assert(Rect(0, 0, 5, 0).rows.isEmpty)
    assert(Rect(0, 0, 0, 5).columns.isEmpty)
    assert(Rect(0, 0, -4, 4).rows.isEmpty)
    assert(Rect(0, 0, 4, -4).columns.isEmpty)

  test("rows is lazy, so a renderer can take only the rows it can see"):
    assert(Rect(0, 0, 3, 1000000).rows.take(2).toList == List(Rect(0, 0, 3, 1), Rect(0, 1, 3, 1)))

  test("positions walks every cell row-major"):
    assert(
      Rect(1, 2, 2, 2).positions.toList == List(Position(1, 2), Position(2, 2), Position(1, 3), Position(2, 3))
    )

  test("positions of an empty rect is empty and of a full rect has one entry per cell"):
    assert(Rect(4, 4, 0, 3).positions.isEmpty)
    assert(Rect(0, 0, 3, -1).positions.isEmpty)
    assert(Rect(0, 0, 7, 5).positions.size == Rect(0, 0, 7, 5).area)

  test("positions and contains agree on which cells belong to the rect"):
    val rect = Rect(2, 3, 4, 5)
    assert(rect.positions.forall(rect.contains))

  test("foreachPosition visits the same coordinates as positions, in the same order"):
    val rect    = Rect(2, 3, 3, 2)
    val visited = List.newBuilder[Position]
    rect.foreachPosition((col, row) => visited += Position(col, row))
    assert(visited.result() == rect.positions.toList)

  test("foreachPosition on an empty rect does nothing"):
    var calls = 0
    Rect(1, 1, 0, 4).foreachPosition((_, _) => calls += 1)
    Rect(1, 1, 4, -2).foreachPosition((_, _) => calls += 1)
    assert(calls == 0)

  test("Size.Zero and Position.Origin are the zero values of the two extents"):
    assert(Size.Zero == Size(0, 0))
    assert(Position.Origin == Position(0, 0))

  test("Size(rect) and Position(rect) mirror the accessors on Rect"):
    val rect = Rect(3, 4, 10, 6)
    assert(Size(rect) == rect.size)
    assert(Position(rect) == rect.position)
    assert(Rect(Position(rect).x, Position(rect).y, Size(rect).width, Size(rect).height) == rect)
    assert(Size(Rect.Zero) == Size.Zero)
    assert(Position(Rect.Zero) == Position.Origin)
