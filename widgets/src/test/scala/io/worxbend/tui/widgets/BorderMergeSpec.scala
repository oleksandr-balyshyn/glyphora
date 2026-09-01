package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Line, Rect, Style}
import io.worxbend.tui.testsupport.BufferAssertions.trimmedLines

import org.scalatest.funsuite.AnyFunSuite

final class BorderMergeSpec extends AnyFunSuite:

  private def exact(existing: String, incoming: String): String =
    BorderMerge.merge(existing, incoming, MergeStrategy.Exact)

  test("a corner landing on a line grows the arm the line brings"):
    assert(exact("─", "┌") == "┬")
    assert(exact("─", "└") == "┴")
    assert(exact("│", "─") == "┼")
    assert(exact("┘", "┌") == "┼")
    assert(exact("┐", "└") == "┼")

  test("weights are taken from the incoming glyph where the two disagree"):
    // a thick block drawn over a plain line reads as thick on the arms it brings, plain on the ones it does not
    assert(exact("─", "┏") == "┲")
    assert(exact("━", "┌") == "┭")

  test("Replace never looks at what is underneath"):
    for glyph <- Seq("┌", "─", "A", " ") do assert(BorderMerge.merge(glyph, "┘", MergeStrategy.Replace) == "┘")

  test("a glyph that is not box drawing is left alone in either direction"):
    assert(exact(" ", "┌") == "┌")
    assert(exact("A", "─") == "─")
    assert(exact("─", "A") == "A")
    assert(exact("漢", "│") == "│")

  test("merging a glyph with itself gives that glyph back"):
    for glyph <- Seq("─", "│", "┌", "┼", "╔", "╬", "┏", "╋") do assert(exact(glyph, glyph) == glyph)

  test("a rounded corner merges into the square junction, the only one Unicode has"):
    assert(exact("─", "╭") == "┬")
    assert(exact("╰", "╮") == "┼")
    // and a rounded corner drawn on its own is still written unchanged
    assert(exact(" ", "╭") == "╭")

  test("Fuzzy weakens double arms where Exact has nothing to offer"):
    // Unicode has no double-meets-heavy junction at all
    assert(exact("═", "┃") == "┃")
    assert(BorderMerge.merge("═", "┃", MergeStrategy.Fuzzy) == "╂")
    // where an exact glyph does exist, Fuzzy agrees with Exact rather than weakening anything
    assert(BorderMerge.merge("═", "│", MergeStrategy.Fuzzy) == exact("═", "│"))
    assert(exact("═", "│") == "╪")

  test("two panels sharing a column draw one joined wall"):
    val buffer = Buffer(Rect(0, 0, 9, 3))
    val block  = Block(mergeBorders = MergeStrategy.Exact)
    block.render(Rect(0, 0, 5, 3), buffer)
    block.render(Rect(4, 0, 5, 3), buffer)
    assert(trimmedLines(buffer) == Seq("┌───┬───┐", "│   │   │", "└───┴───┘"))

  test("a third panel meeting the seam from below gives a full cross"):
    val buffer = Buffer(Rect(0, 0, 9, 5))
    val block  = Block(mergeBorders = MergeStrategy.Exact)
    block.render(Rect(0, 0, 5, 3), buffer)
    block.render(Rect(4, 0, 5, 3), buffer)
    block.render(Rect(4, 2, 5, 3), buffer)
    assert(trimmedLines(buffer)(2) == "└───┼───┤")

  test("the merge table is a bijection, so no two glyphs claim the same shape"):
    assert(BorderMerge.glyphCount == BorderMerge.shapeCount)

  test("the default strategy leaves the doubled seam exactly as it was"):
    val buffer = Buffer(Rect(0, 0, 9, 3))
    Block().render(Rect(0, 0, 5, 3), buffer)
    Block().render(Rect(4, 0, 5, 3), buffer)
    assert(trimmedLines(buffer) == Seq("┌───┌───┐", "│   │   │", "└───└───┘"))

  test("a title still overwrites the border it sits on when merging is on"):
    val buffer = Buffer(Rect(0, 0, 9, 3))
    Block().render(Rect(0, 0, 9, 3), buffer)
    Block(Seq(BlockTitle.top(Line.raw("hi"))), mergeBorders = MergeStrategy.Exact)
      .render(Rect(0, 0, 9, 3), buffer)
    assert(trimmedLines(buffer).head == "┌hi─────┐")

  test("merging on a one-cell and an empty block writes nothing outside the area"):
    val buffer = Buffer(Rect(0, 0, 3, 2))
    buffer.setString(0, 0, "───", Style.Default)
    Block(mergeBorders = MergeStrategy.Exact).render(Rect(1, 0, 1, 1), buffer)
    // a 1x1 block has no room for corners, so it draws its left and top edges into the one cell it has; both merge
    // with the run already there and the result carries all four arms
    assert(trimmedLines(buffer).head == "─┼─")
    Block(mergeBorders = MergeStrategy.Exact).render(Rect(0, 0, 0, 0), buffer)
    assert(buffer.area == Rect(0, 0, 3, 2))
