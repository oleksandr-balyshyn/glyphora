package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Color, Line, Modifiers, Span, Style, Text}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

/** What a [[io.worxbend.tui.core.Text]]'s own style and alignment do once a [[Paragraph]] draws it. */
final class TextBaseLayerRenderSpec extends AnyFunSuite:

  private val bold = Style.Default.bold
  private val red  = Style.Default.withFg(Color.Red)
  private val blue = Style.Default.withFg(Color.Blue)

  private def cell(buffer: Buffer, x: Int, y: Int): Style = buffer.get(x, y).style

  test("all four style layers reach the cell, the innermost winning where they disagree"):
    val line      = Line.styled(Seq(Span.raw("ab"), Span("cd", blue)), red)
    // paragraph: bold (outermost) → text: underline → line: red → span: blue on "cd"
    val text      = Text.styled(Seq(line), Style.Default.underline)
    val paragraph = Paragraph(text, style = bold)
    val buffer    = rendered(paragraph, 4, 1)
    assert(trimmedLines(buffer) == Seq("abcd"))
    assert(cell(buffer, 0, 0).fg.contains(Color.Red))
    assert(cell(buffer, 2, 0).fg.contains(Color.Blue))
    assert(cell(buffer, 0, 0).modifiers.hasAll(Modifiers.Bold | Modifiers.Underline))
    assert(cell(buffer, 2, 0).modifiers.hasAll(Modifiers.Bold | Modifiers.Underline))

  test("a paragraph style no longer erases the style the text was built with"):
    val buffer = rendered(Paragraph(Text.raw("hi").withStyle(red), style = bold), 4, 1)
    assert(cell(buffer, 0, 0).fg.contains(Color.Red))
    assert(cell(buffer, 0, 0).modifiers.hasAny(Modifiers.Bold))

  test("a cleared modifier on the text switches off one the paragraph set"):
    val buffer = rendered(Paragraph(Text.raw("hi").withStyle(Style.Default.notBold), style = bold), 4, 1)
    assert(!cell(buffer, 0, 0).modifiers.hasAny(Modifiers.Bold))

  test("the text's alignment overrides the paragraph's for every row that has none of its own"):
    val buffer = rendered(Paragraph(Text.raw("ab\ncd").rightAligned, alignment = Alignment.Left), 4, 2)
    assert(trimmedLines(buffer) == Seq("  ab", "  cd"))

  test("a line's own alignment still beats the text's"):
    val text   = Text(Seq(Line.raw("ab").leftAligned, Line.raw("cd"))).rightAligned
    val buffer = rendered(Paragraph(text), 4, 2)
    assert(trimmedLines(buffer) == Seq("ab", "  cd"))

  test("a centred text of wide characters is placed by display columns, not by string length"):
    // "你好" is four columns wide in a six-column area, so it starts one column in — a `length`-based origin would
    // have put it two columns in and left the row lopsided.
    val buffer = rendered(Paragraph(Text.raw("你好").centered), 6, 1)
    assert(trimmedLines(buffer) == Seq(" 你好"))

  test("the text's layers survive wrapping onto extra rows"):
    val text   = Text.raw("alpha beta").centered.withStyle(red)
    val buffer = rendered(Paragraph(text, overflow = Overflow.Wrap), 7, 2)
    // `trimmedLines` trims the trailing blanks only, so the leading space each centred row starts with is visible
    assert(trimmedLines(buffer) == Seq(" alpha", " beta"))
    assert(cell(buffer, 1, 0).fg.contains(Color.Red))
    assert(cell(buffer, 1, 1).fg.contains(Color.Red))

  test("a one-column area and a zero-height area render without throwing"):
    val narrow = rendered(Paragraph(Text.raw("你好").centered.withStyle(red)), 1, 1)
    assert(narrow.area.width == 1)
    val flat   = rendered(Paragraph(Text.raw("x").rightAligned), 3, 0)
    assert(flat.area.height == 0)
