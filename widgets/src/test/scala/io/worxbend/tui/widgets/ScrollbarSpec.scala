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

  test("arrow caps take the first and last cell and shorten the track"):
    // 6 rows: one cap at each end leaves a 4-row track, and 8 rows of content halve it into a 2-row thumb
    val buffer = rendered(Scrollbar(contentLength = 8, beginSymbol = Some("↑"), endSymbol = Some("↓")), 1, 6)
    assert(lines(buffer) == Seq("↑", "█", "█", "│", "│", "↓"))

  test("the thumb reaches the far end of a capped track without covering the cap"):
    val buffer = rendered(Scrollbar(8, 99, beginSymbol = Some("↑"), endSymbol = Some("↓")), 1, 6)
    assert(lines(buffer) == Seq("↑", "│", "│", "█", "█", "↓"))

  test("a single cap only shortens the track at its own end"):
    val buffer = rendered(Scrollbar(contentLength = 6, beginSymbol = Some("↑")), 1, 4)
    assert(lines(buffer) == Seq("↑", "█", "│", "│"))

  test("a two-cell strip with both caps is all caps and no track"):
    val buffer = rendered(Scrollbar(8, 0, beginSymbol = Some("↑"), endSymbol = Some("↓")), 1, 2)
    assert(lines(buffer) == Seq("↑", "↓"))

  test("a one-cell strip with both caps keeps the begin cap rather than drawing the end one over it"):
    val buffer = rendered(Scrollbar(8, 0, beginSymbol = Some("↑"), endSymbol = Some("↓")), 1, 1)
    assert(lines(buffer) == Seq("↑"))

  test("a horizontal bar draws its caps at the two ends of the bottom row"):
    val buffer = rendered(Scrollbar(8, 0, Direction.Horizontal, beginSymbol = Some("←"), endSymbol = Some("→")), 6, 1)
    assert(lines(buffer) == Seq("←██││→"))

  test("withSymbols draws the whole named set"):
    val buffer = rendered(Scrollbar.withSymbols(8, 0, ScrollbarSymbols.DoubleVertical), 1, 6)
    assert(lines(buffer) == Seq("▲", "█", "█", "║", "║", "▼"))

  test("the ascii set stays inside printable ascii for a terminal without box drawing"):
    val buffer = rendered(Scrollbar.withSymbols(8, 0, ScrollbarSymbols.Ascii), 1, 6)
    assert(lines(buffer) == Seq("^", "#", "#", "|", "|", "v"))

  test("the plain set is what the bare constructor already draws"):
    val plain = rendered(Scrollbar.withSymbols(8, 0, ScrollbarSymbols.Plain), 1, 4)
    val bare  = rendered(Scrollbar(contentLength = 8), 1, 4)
    assert(lines(plain) == lines(bare))

  test("a wide cap glyph claims both of its cells and the track starts after it"):
    // a caller may pass a double-width glyph; it is measured rather than counted, so the track begins two cells in
    val buffer = rendered(Scrollbar(8, 0, Direction.Horizontal, beginSymbol = Some("⏪")), 6, 1)
    assert(lines(buffer) == Seq("⏪██││"))

  test("a wide end cap is placed so that its right half is the last cell"):
    val buffer = rendered(Scrollbar(8, 0, Direction.Horizontal, endSymbol = Some("⏩")), 6, 1)
    assert(lines(buffer) == Seq("██││⏩"))
