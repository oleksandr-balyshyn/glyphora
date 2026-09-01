package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** `Alignment` is pure arithmetic: given the columns available and the display width of what goes in them, it says
  * which column the content starts at.
  */
final class AlignmentSpec extends AnyFunSuite:

  test("left alignment starts at the area's own first column"):
    assert(Alignment.Left.originAt(3, 10, 4) == 3)

  test("centre alignment splits the leftover columns, rounding the extra one to the right"):
    assert(Alignment.Center.originAt(0, 10, 4) == 3)
    assert(Alignment.Center.originAt(0, 10, 5) == 2)

  test("right alignment ends the content at the area's last column"):
    assert(Alignment.Right.originAt(2, 10, 4) == 8)

  test("content wider than the area is pinned to the first column instead of starting left of it"):
    // Without the clamp the difference goes negative and half of it lands outside the area entirely.
    assert(Alignment.Center.originAt(5, 3, 9) == 5)
    assert(Alignment.Right.originAt(5, 3, 9) == 5)

  test("content exactly as wide as the area starts at the first column whichever way it is placed"):
    Alignment.values.foreach(alignment => assert(alignment.originAt(1, 6, 6) == 1))
