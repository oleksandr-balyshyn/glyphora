package io.worxbend.tui.widgets

import io.worxbend.tui.core.Direction
import io.worxbend.tui.testsupport.BufferAssertions.{lines, rendered}

import org.scalatest.funsuite.AnyFunSuite

final class ScrollbarSpec extends AnyFunSuite:

  test("content that fits draws only the track"):
    val buffer = rendered(Scrollbar(contentLength = 3), 1, 4)
    assert(lines(buffer) == Seq("│", "│", "│", "│"))

  test("the thumb sits at the top when scrolled to the start"):
    val buffer = rendered(Scrollbar(contentLength = 8, position = 0), 1, 4)
    assert(lines(buffer) == Seq("█", "█", "│", "│"))

  test("the thumb reaches the bottom when scrolled to the end"):
    val buffer = rendered(Scrollbar(contentLength = 8, position = 4), 1, 4)
    assert(lines(buffer) == Seq("│", "│", "█", "█"))

  test("a position past the end pins the thumb to the end rather than off the track"):
    val buffer = rendered(Scrollbar(contentLength = 8, position = 999), 1, 4)
    assert(lines(buffer) == Seq("│", "│", "█", "█"))

  test("a horizontal scrollbar renders along the bottom edge"):
    val buffer = rendered(Scrollbar(8, 0, orientation = Direction.Horizontal), 4, 1)
    assert(lines(buffer) == Seq("██││"))

  test("the vertical scrollbar draws on the rightmost column of a wider area"):
    val buffer = rendered(Scrollbar(2, 0), 3, 2)
    assert(lines(buffer) == Seq("  │", "  │"))
