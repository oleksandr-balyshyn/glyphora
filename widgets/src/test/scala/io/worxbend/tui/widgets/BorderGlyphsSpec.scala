package io.worxbend.tui.widgets

import io.worxbend.tui.core.CharWidth
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

/** Covers the border-glyph record: the named sets, their asymmetric sides, and a caller-supplied set. */
final class BorderGlyphsSpec extends AnyFunSuite:

  /** The three rows of a 4x3 block drawn with `borderType`, as plain strings. */
  private def frame(borderType: BorderType): Seq[String] =
    trimmedLines(rendered(Block(borderType = borderType), 4, 3))

  test("the four classic weights are unchanged"):
    assert(frame(BorderType.Plain) == Seq("┌──┐", "│  │", "└──┘"))
    assert(frame(BorderType.Rounded) == Seq("╭──╮", "│  │", "╰──╯"))
    assert(frame(BorderType.Double) == Seq("╔══╗", "║  ║", "╚══╝"))
    assert(frame(BorderType.Thick) == Seq("┏━━┓", "┃  ┃", "┗━━┛"))

  test("the ASCII set uses no box-drawing characters at all"):
    val rows = frame(BorderType.Ascii)
    assert(rows == Seq("+--+", "|  |", "+--+"))
    assert(rows.mkString.forall(_ < 128))

  test("the dashed sets keep solid corners of their own weight"):
    assert(frame(BorderType.LightDoubleDashed) == Seq("┌╌╌┐", "╎  ╎", "└╌╌┘"))
    assert(frame(BorderType.LightTripleDashed) == Seq("┌┄┄┐", "┆  ┆", "└┄┄┘"))
    assert(frame(BorderType.LightQuadrupleDashed) == Seq("┌┈┈┐", "┊  ┊", "└┈┈┘"))
    assert(frame(BorderType.HeavyDoubleDashed) == Seq("┏╍╍┓", "╏  ╏", "┗╍╍┛"))
    assert(frame(BorderType.HeavyTripleDashed) == Seq("┏┅┅┓", "┇  ┇", "┗┅┅┛"))
    assert(frame(BorderType.HeavyQuadrupleDashed) == Seq("┏┉┉┓", "┋  ┋", "┗┉┉┛"))

  test("the quadrant sets are asymmetric — left and right edges differ"):
    // this is the case a single `vertical` glyph could not express: the half-cell hugs the interior from either side
    assert(frame(BorderType.QuadrantOutside) == Seq("▛▀▀▜", "▌  ▐", "▙▄▄▟"))
    assert(frame(BorderType.QuadrantInside) == Seq("▗▄▄▖", "▐  ▌", "▝▀▀▘"))

  test("the McGugan sets are asymmetric on both axes"):
    assert(frame(BorderType.OneEighthWide) == Seq("▁▁▁▁", "▏  ▕", "▔▔▔▔"))
    assert(frame(BorderType.OneEighthTall) == Seq("▕▔▔▏", "▕  ▏", "▕▁▁▏"))

  test("the proportional sets thin the horizontal edges to match the vertical ones"):
    assert(frame(BorderType.ProportionalWide) == Seq("▄▄▄▄", "█  █", "▀▀▀▀"))
    assert(frame(BorderType.ProportionalTall) == Seq("█▀▀█", "█  █", "█▄▄█"))

  test("the solid and blank sets draw one glyph everywhere"):
    assert(frame(BorderType.Full) == Seq("████", "█  █", "████"))
    // `trimmedLines` strips trailing blanks, so a frame made of spaces trims away to nothing
    assert(frame(BorderType.Blank) == Seq("", "", ""))

  test("a blank border still carries its title"):
    val rows = trimmedLines(
      rendered(Block(Seq(BlockTitle.top(io.worxbend.tui.core.Line.raw("Hi"))), borderType = BorderType.Blank), 6, 3)
    )
    assert(rows.head == " Hi")

  test("every named set is one column wide in every position, so inner geometry never moves"):
    for borderType <- BorderType.values do
      val glyphs = BorderGlyphs.of(borderType)
      val all    = Seq(
        glyphs.horizontalTop,
        glyphs.horizontalBottom,
        glyphs.verticalLeft,
        glyphs.verticalRight,
        glyphs.topLeft,
        glyphs.topRight,
        glyphs.bottomLeft,
        glyphs.bottomRight,
      )
      assert(all.forall(glyph => CharWidth.of(glyph) == 1), s"$borderType has a glyph that is not one column wide")

  test("a caller-supplied border set wins over the border type"):
    val stars  = BorderGlyphs.uniform("*")
    val buffer = rendered(Block(borderType = BorderType.Double, borderSet = Some(stars)), 4, 3)
    assert(trimmedLines(buffer) == Seq("****", "*  *", "****"))

  test("a caller-supplied set can be asymmetric"):
    val set    = BorderGlyphs(
      horizontalTop = "T",
      horizontalBottom = "B",
      verticalLeft = "L",
      verticalRight = "R",
      topLeft = "1",
      topRight = "2",
      bottomLeft = "3",
      bottomRight = "4",
    )
    val buffer = rendered(Block(borderSet = Some(set)), 4, 3)
    assert(trimmedLines(buffer) == Seq("1TT2", "L  R", "3BB4"))

  test("symmetric shares one glyph per axis"):
    val set = BorderGlyphs.symmetric("─", "│", "┌", "┐", "└", "┘")
    assert(set.horizontalTop == set.horizontalBottom)
    assert(set.verticalLeft == set.verticalRight)
    assert(set == BorderGlyphs.of(BorderType.Plain))

  test("a single-side border draws only that side's glyph"):
    // with only the left border on, an asymmetric set must use its *left* vertical and not its right one
    val buffer = rendered(Block(borders = Borders.Left, borderType = BorderType.QuadrantOutside), 3, 2)
    assert(trimmedLines(buffer) == Seq("▌", "▌"))
