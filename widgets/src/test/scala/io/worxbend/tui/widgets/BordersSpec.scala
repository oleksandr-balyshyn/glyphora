package io.worxbend.tui.widgets

import org.scalatest.funsuite.AnyFunSuite

final class BordersSpec extends AnyFunSuite:

  test("the axis constants are the pairs they name"):
    assert(Borders.Vertical == (Borders.Left | Borders.Right))
    assert(Borders.Horizontal == (Borders.Top | Borders.Bottom))
    assert((Borders.Horizontal | Borders.Vertical) == Borders.All)

  test("without clears the named sides"):
    assert(Borders.All.without(Borders.Top) == (Borders.Right | Borders.Bottom | Borders.Left))
    assert(!Borders.All.without(Borders.Top).hasAny(Borders.Top))
    assert(Borders.All.without(Borders.All).isEmpty)
    assert(Borders.Top.without(Borders.Left) == Borders.Top)

  test("hasAll requires every named side"):
    assert(Borders.All.hasAll(Borders.Vertical))
    assert(!Borders.Top.hasAll(Borders.Horizontal))
    assert(Borders.None.hasAll(Borders.None))

  test("intersection keeps only the shared sides"):
    assert((Borders.All & Borders.Horizontal) == Borders.Horizontal)
    assert((Borders.Top & Borders.Left).isEmpty)

  test("show names the set sides in declaration order"):
    assert(Borders.None.show == "None")
    assert(Borders.All.show == "Top|Right|Bottom|Left")
    assert((Borders.Left | Borders.Top).show == "Top|Left")
    assert(Borders.Vertical.names == Seq("Right", "Left"))
