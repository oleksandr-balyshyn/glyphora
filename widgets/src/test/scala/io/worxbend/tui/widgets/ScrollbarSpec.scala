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

  test("a declared viewport shorter than the track shortens the thumb"):
    // the bar is 8 rows tall but the pane it describes only shows 4 of the 16 content rows, so the thumb covers a
    // quarter of the track (2 of 8 cells) rather than the half it would if the track spoke for the viewport
    val buffer = rendered(Scrollbar(contentLength = 16, viewportLength = Some(4)), 1, 8)
    assert(lines(buffer) == Seq("█", "█", "│", "│", "│", "│", "│", "│"))

  test("a declared viewport makes the thumb reach the end at the real last offset"):
    // 16 rows of content seen 4 at a time scrolls to offset 12, not to 16 - 8
    val buffer = rendered(Scrollbar(contentLength = 16, position = 12, viewportLength = Some(4)), 1, 8)
    assert(lines(buffer) == Seq("│", "│", "│", "│", "│", "│", "█", "█"))

  test("a declared viewport at least as large as the content draws only the track"):
    val buffer = rendered(Scrollbar(contentLength = 6, viewportLength = Some(6)), 1, 3)
    assert(lines(buffer) == Seq("│", "│", "│"))

  test("a viewport larger than the track never asks for a thumb longer than the bar"):
    val buffer = rendered(Scrollbar(contentLength = 12, position = 0, viewportLength = Some(11)), 1, 3)
    assert(lines(buffer) == Seq("█", "█", "│"))

  test("a nonsensical viewport of zero is treated as one visible row"):
    val buffer = rendered(Scrollbar(contentLength = 4, position = 0, viewportLength = Some(0)), 1, 4)
    assert(lines(buffer) == Seq("█", "│", "│", "│"))

  test("a horizontal scrollbar honours its declared viewport width"):
    val buffer = rendered(Scrollbar(16, 0, Direction.Horizontal, viewportLength = Some(4)), 8, 1)
    assert(lines(buffer) == Seq("██││││││"))
