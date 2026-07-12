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
    val buffer = rendered(Block(title = Some(Line.raw("Hi"))), 8, 3)
    assert(trimmedLines(buffer).head == "┌Hi────┐")

  test("a long title is truncated inside the corners"):
    val buffer = rendered(Block(title = Some(Line.raw("much too long"))), 6, 3)
    assert(trimmedLines(buffer).head == "┌much┐")

  test("inner shrinks the area by the border on every side"):
    assert(Block().inner(Rect(0, 0, 10, 6)) == Rect(1, 1, 8, 4))

  test("a degenerate 1x1 area degrades to a single edge cell"):
    val buffer = rendered(Block(), 1, 1)
    assert(trimmedLines(buffer) == Seq("─"))

  test("per-side borders draw only the requested sides"):
    val topOnly = rendered(Block(borders = Borders.Top), 4, 2)
    assert(trimmedLines(topOnly) == Seq("────", ""))
    val band = rendered(Block(borders = Borders.Top | Borders.Bottom), 4, 3)
    assert(trimmedLines(band) == Seq("────", "", "────"))
    val leftOnly = rendered(Block(borders = Borders.Left), 3, 2)
    assert(trimmedLines(leftOnly) == Seq("│", "│"))

  test("corners appear only where two adjacent sides meet"):
    val noRight = rendered(Block(borders = Borders.Top | Borders.Bottom | Borders.Left), 4, 3)
    assert(trimmedLines(noRight) == Seq("┌───", "│", "└───"))

  test("title alignment positions the title on the top border"):
    val centered = rendered(Block(title = Some(Line.raw("Hi")), titleAlignment = Alignment.Center), 8, 3)
    assert(trimmedLines(centered).head == "┌──Hi──┐")
    val right = rendered(Block(title = Some(Line.raw("Hi")), titleAlignment = Alignment.Right), 8, 3)
    assert(trimmedLines(right).head == "┌────Hi┐")

  test("padding shrinks the inner area inside the borders"):
    val block = Block(padding = 1)
    assert(block.inner(Rect(0, 0, 10, 6)) == Rect(2, 2, 6, 2))

  test("inner accounts for missing sides"):
    val block = Block(borders = Borders.Top)
    assert(block.inner(Rect(0, 0, 10, 6)) == Rect(0, 1, 10, 5))
