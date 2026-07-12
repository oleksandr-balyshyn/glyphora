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

  test("a degenerate area renders nothing"):
    val buffer = rendered(Block(), 1, 1)
    assert(trimmedLines(buffer) == Seq(""))
