package io.worxbend.tui.dsl

import io.worxbend.tui.testsupport.BufferAssertions

import org.scalatest.funsuite.AnyFunSuite

final class LayoutExtrasSpec extends AnyFunSuite:

  private def render(element: Element, width: Int, height: Int): Seq[String] =
    BufferAssertions.lines(BufferAssertions.rendered(element.widget, width, height))

  test("place centers a fixed-size block both ways by default"):
    val out = render(place(3, 1)(text("###")), 7, 3)
    assert(out == Seq("       ", "  ###  ", "       "))

  test("place aligns to Start on both axes"):
    val out = render(place(3, 1, Align.Start, Align.Start)(text("###")), 7, 3)
    assert(out.head == "###    ")

  test("place aligns to End on both axes"):
    val out = render(place(3, 1, Align.End, Align.End)(text("###")), 7, 3)
    assert(out(2) == "    ###")

  test("row .flex(SpaceBetween) pushes children to the edges"):
    val el  = row(text("A").length(1), text("B").length(1)).flex(Flex.SpaceBetween)
    val out = render(el, 5, 1)
    assert(out.head == "A   B")

  test("row .margin trims the area before layout"):
    val out = render(row(text("AB").length(2)).margin(1, 0), 6, 1)
    assert(out.head == " AB   ")

  test("column .spacing inserts blank rows between children"):
    val out = render(column(text("A").length(1), text("B").length(1)).spacing(1), 1, 3)
    assert(out == Seq("A", " ", "B"))
