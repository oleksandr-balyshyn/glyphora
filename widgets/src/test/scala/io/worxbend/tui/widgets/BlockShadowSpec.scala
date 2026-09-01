package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Modifiers, Rect, Style}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

/** Covers a [[Block]] that casts a [[Shadow]]: the room it gives up, and where the band lands. */
final class BlockShadowSpec extends AnyFunSuite:

  private val solid: Shadow = Shadow.shade(ShadowFill.Solid)

  test("the frame gives up a column and a row, and the shadow takes the strip"):
    val buffer = rendered(Block(shadow = Some(solid)), 5, 4)
    assert(
      trimmedLines(buffer) == Seq(
        "┌──┐",
        "│  │█",
        "└──┘█",
        " ████",
      )
    )

  test("inner shrinks by the shadow as well as by the border"):
    val area = Rect(0, 0, 6, 5)
    assert(Block(shadow = Some(solid)).inner(area) == Rect(1, 1, 3, 2))
    assert(Block().inner(area) == Rect(1, 1, 4, 3))

  test("a shadow cast up and to the left moves the frame down and right"):
    val buffer = rendered(Block(shadow = Some(Shadow.shade(ShadowFill.Solid, -1, -1))), 5, 4)
    assert(
      trimmedLines(buffer) == Seq(
        "████",
        "█┌──┐",
        "█│  │",
        " └──┘",
      )
    )

  test("the border wins wherever it meets the band"):
    // the shadow is painted first, so no frame cell may end up carrying a shadow glyph
    val buffer = rendered(Block(shadow = Some(solid)), 5, 4)
    assert(buffer.get(0, 0).symbol == "┌")
    assert(buffer.get(3, 2).symbol == "┘")

  test("an area too small to leave a frame behind drops the shadow"):
    // 3x3 minus the shadow leaves a 2x2 frame, which still works
    assert(trimmedLines(rendered(Block(shadow = Some(solid)), 3, 3)) == Seq("┌┐", "└┘█", " ██"))
    // 2x2 minus the shadow would leave 1x1, so the block renders as if it had no shadow at all
    assert(trimmedLines(rendered(Block(shadow = Some(solid)), 2, 2)) == trimmedLines(rendered(Block(), 2, 2)))

  test("a block with no shadow is untouched"):
    assert(trimmedLines(rendered(Block(), 5, 4)) == trimmedLines(rendered(Block(shadow = None), 5, 4)))

  test("the dim shadow keeps what was drawn behind the block"):
    val area   = Rect(0, 0, 5, 4)
    val buffer = Buffer(area)
    buffer.setString(0, 3, "abcde", Style.Default)
    Block(shadow = Some(Shadow.Default)).render(area, buffer)
    // row 3 is the horizontal strip of the band: the glyphs survive and only their style changes
    assert(buffer.get(1, 3).symbol == "b")
    assert(buffer.get(1, 3).style.modifiers.hasAll(Modifiers.Dim))
    // column 0 of row 3 is outside the band (the shadow is offset one to the right), so it stays plain
    assert(!buffer.get(0, 3).style.modifiers.hasAll(Modifiers.Dim))

  test("the panel fill covers the frame and not the shadow band"):
    val buffer =
      rendered(Block(style = Style.Default.withBg(io.worxbend.tui.core.Color.Blue), shadow = Some(solid)), 5, 4)
    assert(buffer.get(1, 1).style.bg.contains(io.worxbend.tui.core.Color.Blue))
    assert(buffer.get(4, 1).style.bg.isEmpty)

  test("a zero-size area with a shadow draws nothing"):
    val buffer = Buffer(Rect(0, 0, 4, 4))
    Block(shadow = Some(solid)).render(Rect(0, 0, 0, 0), buffer)
    assert(buffer.diff(Buffer(Rect(0, 0, 4, 4))).isEmpty)

  test("a title still lands on the frame, not on the area edge"):
    val buffer = rendered(
      Block(Seq(BlockTitle.top(io.worxbend.tui.core.Line.raw("Hi"))), shadow = Some(solid)),
      8,
      4,
    )
    assert(trimmedLines(buffer).head == "┌Hi───┐")
