package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Direction, Rect}
import io.worxbend.tui.testsupport.BufferAssertions.lines

import org.scalatest.funsuite.AnyFunSuite

final class ScrollbarSpec extends AnyFunSuite:

  private def renderedWith(state: ScrollbarState, width: Int = 1, height: Int = 4): Buffer =
    val buffer = Buffer(Rect(0, 0, width, height))
    Scrollbar().render(buffer.area, buffer, state)
    buffer

  test("content that fits draws only the track"):
    val buffer = renderedWith(ScrollbarState(contentLength = 3))
    assert(lines(buffer) == Seq("│", "│", "│", "│"))

  test("the thumb sits at the top when scrolled to the start"):
    val buffer = renderedWith(ScrollbarState(contentLength = 8, position = 0))
    assert(lines(buffer) == Seq("█", "█", "│", "│"))

  test("the thumb reaches the bottom when scrolled to the end"):
    val buffer = renderedWith(ScrollbarState(contentLength = 8, position = 4))
    assert(lines(buffer) == Seq("│", "│", "█", "█"))

  test("a horizontal scrollbar renders along the bottom edge"):
    val buffer = Buffer(Rect(0, 0, 4, 1))
    Scrollbar(orientation = Direction.Horizontal).render(buffer.area, buffer, ScrollbarState(8, 0))
    assert(lines(buffer) == Seq("██││"))

  test("the vertical scrollbar draws on the rightmost column of a wider area"):
    val buffer = Buffer(Rect(0, 0, 3, 2))
    Scrollbar().render(buffer.area, buffer, ScrollbarState(2, 0))
    assert(lines(buffer) == Seq("  │", "  │"))
