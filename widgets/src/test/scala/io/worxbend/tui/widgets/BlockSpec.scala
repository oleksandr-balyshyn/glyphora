package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Line, Rect}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class BlockSpec extends AnyFunSuite:

  test("a plain block draws all four borders and corners"):
    val buffer = rendered(Block(), 5, 3)
    assert(
      trimmedLines(buffer) == Seq(
        "┌───┐",
        "│   │",
        "└───┘",
      )
    )

  test("a rounded block uses rounded corners"):
    val buffer = rendered(Block(borderType = BorderType.Rounded), 4, 3)
    assert(
      trimmedLines(buffer) == Seq(
        "╭──╮",
        "│  │",
        "╰──╯",
      )
    )

  test("double and thick border types use their glyph sets"):
    assert(trimmedLines(rendered(Block(borderType = BorderType.Double), 3, 2)) == Seq("╔═╗", "╚═╝"))
    assert(trimmedLines(rendered(Block(borderType = BorderType.Thick), 3, 2)) == Seq("┏━┓", "┗━┛"))

  test("the title renders on the top border"):
    val buffer = rendered(Block(Seq(BlockTitle.top(Line.raw("Hi")))), 8, 3)
    assert(trimmedLines(buffer).head == "┌Hi────┐")

  test("a long title is truncated inside the corners"):
    val buffer = rendered(Block(Seq(BlockTitle.top(Line.raw("much too long")))), 6, 3)
    assert(trimmedLines(buffer).head == "┌much┐")

  test("inner shrinks the area by the border on every side"):
    assert(Block().inner(Rect(0, 0, 10, 6)) == Rect(1, 1, 8, 4))

  test("a degenerate 1x1 area degrades to a single edge cell"):
    val buffer = rendered(Block(), 1, 1)
    assert(trimmedLines(buffer) == Seq("─"))

  test("per-side borders draw only the requested sides"):
    val topOnly  = rendered(Block(borders = Borders.Top), 4, 2)
    assert(trimmedLines(topOnly) == Seq("────", ""))
    val band     = rendered(Block(borders = Borders.Top | Borders.Bottom), 4, 3)
    assert(trimmedLines(band) == Seq("────", "", "────"))
    val leftOnly = rendered(Block(borders = Borders.Left), 3, 2)
    assert(trimmedLines(leftOnly) == Seq("│", "│"))

  test("corners appear only where two adjacent sides meet"):
    val noRight = rendered(Block(borders = Borders.Top | Borders.Bottom | Borders.Left), 4, 3)
    assert(trimmedLines(noRight) == Seq("┌───", "│", "└───"))

  test("title alignment positions the title on the top border"):
    val centered = rendered(Block(Seq(BlockTitle.top(Line.raw("Hi"), Alignment.Center))), 8, 3)
    assert(trimmedLines(centered).head == "┌──Hi──┐")
    val right    = rendered(Block(Seq(BlockTitle.top(Line.raw("Hi"), Alignment.Right))), 8, 3)
    assert(trimmedLines(right).head == "┌────Hi┐")

  test("uniform padding shrinks the inner area inside the borders"):
    val block = Block(padding = Padding.uniform(1))
    assert(block.inner(Rect(0, 0, 10, 6)) == Rect(2, 2, 6, 2))

  test("padding is deducted per side, not per axis"):
    val block = Block(padding = Padding(left = 3, right = 1, top = 2, bottom = 0))
    assert(block.inner(Rect(0, 0, 12, 8)) == Rect(4, 3, 6, 4))

  test("proportional padding doubles the horizontal count so both axes look equal"):
    assert(Padding.proportional(1) == Padding(left = 2, right = 2, top = 1, bottom = 1))

  test("a bottom title renders on the bottom border"):
    val buffer = rendered(Block(Seq(BlockTitle.bottom(Line.raw("Hi"), Alignment.Right))), 8, 3)
    assert(trimmedLines(buffer).last == "└────Hi┘")

  test("a top and a bottom title coexist without costing a content row"):
    val block  = Block(Seq(BlockTitle.top(Line.raw("name")), BlockTitle.bottom(Line.raw("ok"), Alignment.Right)))
    val buffer = rendered(block, 10, 3)
    assert(trimmedLines(buffer).head == "┌name────┐")
    assert(trimmedLines(buffer).last == "└──────ok┘")
    assert(block.inner(Rect(0, 0, 10, 3)) == Rect(1, 1, 8, 1))

  test("titles sharing a border and an alignment are joined by a single space"):
    val buffer = rendered(Block(Seq(BlockTitle.top(Line.raw("a")), BlockTitle.top(Line.raw("b")))), 8, 3)
    assert(trimmedLines(buffer).head == "┌a b───┐")

  test("inner accounts for missing sides"):
    val block = Block(borders = Borders.Top)
    assert(block.inner(Rect(0, 0, 10, 6)) == Rect(0, 1, 10, 5))

  test("Borders.All.without draws the same frame as spelling out the remaining sides"):
    val subtracted = rendered(Block(borders = Borders.All.without(Borders.Right)), 4, 3)
    assert(
      trimmedLines(subtracted) == trimmedLines(
        rendered(Block(borders = Borders.Top | Borders.Bottom | Borders.Left), 4, 3)
      )
    )

  test("single-side padding insets only that side of the inner area"):
    assert(Block(padding = Padding.left(2)).inner(Rect(0, 0, 10, 3)) == Rect(3, 1, 6, 1))
    assert(Block(padding = Padding.top(1)).inner(Rect(0, 0, 10, 5)) == Rect(1, 2, 8, 2))
