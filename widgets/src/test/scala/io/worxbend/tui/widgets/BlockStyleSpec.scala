package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Color, Line, Modifiers, Rect, Style}
import io.worxbend.tui.testsupport.BufferAssertions.rendered

import org.scalatest.funsuite.AnyFunSuite

/** Covers [[Block]]'s whole-area `style`: the fill that gives a panel a background of its own. */
final class BlockStyleSpec extends AnyFunSuite:

  private val panel: Style = Style.Default.withBg(Color.Blue)

  test("the area style reaches the interior as well as the border"):
    val buffer = rendered(Block(style = panel), 4, 3)
    assert(buffer.get(0, 0).style.bg.contains(Color.Blue)) // a corner
    assert(buffer.get(1, 0).style.bg.contains(Color.Blue)) // a top edge
    assert(buffer.get(1, 1).style.bg.contains(Color.Blue)) // an interior cell nothing was drawn into

  test("a block left at the default style paints no fill"):
    val buffer = rendered(Block(), 4, 3)
    assert(buffer.get(1, 1) == Cell.Empty)

  test("the border style is layered on the area style, not substituted for it"):
    val buffer = rendered(Block(style = panel, borderStyle = Style.Default.withFg(Color.Red)), 4, 3)
    val corner = buffer.get(0, 0)
    assert(corner.symbol == "┌")
    assert(corner.style.fg.contains(Color.Red))
    // the background would be missing here if the border replaced the area style instead of layering onto it
    assert(corner.style.bg.contains(Color.Blue))

  test("a title inherits the panel background too"):
    val buffer = rendered(Block(Seq(BlockTitle.top(Line.raw("Hi"))), style = panel), 8, 3)
    assert(buffer.get(1, 0).symbol == "H")
    assert(buffer.get(1, 0).style.bg.contains(Color.Blue))

  test("the fill keeps content that was drawn before the block"):
    val buffer = Buffer(Rect(0, 0, 5, 3))
    buffer.setString(1, 1, "abc", Style.Default.withFg(Color.Green))
    Block(style = panel).render(Rect(0, 0, 5, 3), buffer)
    val cell   = buffer.get(1, 1)
    assert(cell.symbol == "a")
    assert(cell.style.fg.contains(Color.Green))
    assert(cell.style.bg.contains(Color.Blue))

  test("a fill runs unbroken across a two-column glyph and its continuation"):
    val buffer = Buffer(Rect(0, 0, 6, 3))
    // "漢" is a CJK ideograph and takes two terminal columns, so column 2 is its continuation filler
    buffer.set(1, 1, Cell("漢", Style.Default))
    Block(style = panel).render(Rect(0, 0, 6, 3), buffer)
    assert(buffer.get(1, 1) == Cell("漢", panel))
    // The continuation cell is deliberately left as a continuation rather than restyled: the terminal paints both
    // columns of a wide grapheme from the *left* cell's style, so the background is unbroken on screen, and the
    // diff still refuses to emit column 2 on its own.
    // the diff is taken *towards* this frame — an empty previous frame against this one as the next — because that is
    // the direction a backend flushes in, and it is the next frame's continuation flags that decide what it may paint
    assert(
      !Buffer(Rect(0, 0, 6, 3)).diff(buffer).toSeq.exists { case (position, _) => position.x == 2 && position.y == 1 }
    )

  test("a one-cell area is filled and not an error"):
    val buffer = rendered(Block(style = panel), 1, 1)
    assert(buffer.get(0, 0).style.bg.contains(Color.Blue))

  test("an empty area draws nothing at all"):
    val buffer = Buffer(Rect(0, 0, 3, 3))
    Block(style = panel).render(Rect(0, 0, 0, 0), buffer)
    assert(buffer.diff(Buffer(Rect(0, 0, 3, 3))).isEmpty)

  test("modifiers in the area style reach the interior"):
    val buffer = rendered(Block(style = Style.Default.bold), 3, 3)
    assert(buffer.get(1, 1).style.modifiers.hasAll(Modifiers.Bold))
