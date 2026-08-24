package io.worxbend.tui.dsl

import io.worxbend.tui.testsupport.BufferAssertions

import org.scalatest.funsuite.AnyFunSuite

final class LayoutExtrasSpec extends AnyFunSuite:

  private def render(element: Element, width: Int, height: Int): Seq[String] =
    BufferAssertions.lines(BufferAssertions.rendered(element.widget, width, height))

  test("place centers a fixed-size block both ways by default"):
    val out = render(place(3, 1)(text("###")), 7, 3)
    assert(out == Seq("       ", "  ###  ", "       "))

  test("place aligns to the near edge of both axes"):
    val out = render(place(3, 1, Alignment.Left, VerticalAlignment.Top)(text("###")), 7, 3)
    assert(out.head == "###    ")

  test("place aligns to the far edge of both axes"):
    val out = render(place(3, 1, Alignment.Right, VerticalAlignment.Bottom)(text("###")), 7, 3)
    assert(out(2) == "    ###")

  test("row .flex(SpaceBetween) pushes children to the edges"):
    val el  = row(text("A").length(1), text("B").length(1)).flex(Flex.SpaceBetween)
    val out = render(el, 5, 1)
    assert(out.head == "A   B")

  test("column .gap inserts blank rows between children"):
    val out = render(column(text("A").length(1), text("B").length(1)).gap(1), 1, 3)
    assert(out == Seq("A", " ", "B"))

  /** The same knob, inside the border, with no intervening `column`. Before `PanelElement` was a `FlexContainer` this
    * needed `panel("t")(column(...).gap(1))` — an extra node purely to reach a parameter the panel's own `w.Column`
    * already had.
    */
  test("panel .gap inserts blank rows between children inside the border"):
    val out = render(panel(text("A").length(1), text("B").length(1)).gap(1), 3, 5)
    assert(out == Seq("┌─┐", "│A│", "│ │", "│B│", "└─┘"))
