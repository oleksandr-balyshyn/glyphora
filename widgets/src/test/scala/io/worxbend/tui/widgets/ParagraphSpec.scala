package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Color, Line, Modifiers, Span, Style, Text}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class ParagraphSpec extends AnyFunSuite:

  test("plain text renders line by line"):
    val buffer = rendered(Paragraph(Text.raw("one\ntwo")), 5, 3)
    assert(trimmedLines(buffer) == Seq("one", "two", ""))

  test("lines are clipped at the area width without wrapping"):
    val buffer = rendered(Paragraph(Text.raw("abcdefgh")), 4, 1)
    assert(trimmedLines(buffer) == Seq("abcd"))

  test("wrapping breaks long lines at the area width"):
    val buffer = rendered(Paragraph(Text.raw("abcdef"), overflow = Overflow.Wrap), 4, 2)
    assert(trimmedLines(buffer) == Seq("abcd", "ef"))

  test("wrapping never splits a wide character"):
    val buffer = rendered(Paragraph(Text.raw("ab你cd"), overflow = Overflow.Wrap), 3, 2)
    assert(trimmedLines(buffer) == Seq("ab", "你c"))

  test("center alignment offsets each line by its own width"):
    val buffer = rendered(Paragraph(Text.raw("ab\nabcd"), alignment = Alignment.Center), 6, 2)
    assert(trimmedLines(buffer) == Seq("  ab", " abcd"))

  test("right alignment pushes lines to the right edge"):
    val buffer = rendered(Paragraph(Text.raw("ab"), alignment = Alignment.Right), 5, 1)
    assert(trimmedLines(buffer) == Seq("   ab"))

  test("span styles survive rendering and layer over the paragraph style"):
    val line   = Line(Seq(Span.raw("a"), Span("b", Style.Default.withFg(Color.Red))))
    val buffer = rendered(Paragraph(Text(Seq(line)), style = Style.Default.bold), 3, 1)
    assert(buffer.get(0, 0).style.modifiers.hasAny(Modifiers.Bold))
    assert(buffer.get(1, 0).style.fg.contains(Color.Red))
    assert(buffer.get(1, 0).style.modifiers.hasAny(Modifiers.Bold))

  test("excess lines are clipped at the area height"):
    val buffer = rendered(Paragraph(Text.raw("1\n2\n3")), 3, 2)
    assert(trimmedLines(buffer) == Seq("1", "2"))

  test("a line's own alignment overrides the paragraph's for that row only"):
    val text   = Text(Seq(Line.raw("ab"), Line.raw("ab").centered, Line.raw("ab").rightAligned))
    val buffer = rendered(Paragraph(text), 6, 3)
    assert(trimmedLines(buffer) == Seq("ab", "  ab", "    ab"))

  test("a line marked left-aligned escapes a right-aligned paragraph"):
    val text   = Text(Seq(Line.raw("ab").leftAligned, Line.raw("ab")))
    val buffer = rendered(Paragraph(text, alignment = Alignment.Right), 6, 2)
    assert(trimmedLines(buffer) == Seq("ab", "    ab"))

  test("a wrapped line keeps its own alignment on every row it spills onto"):
    val paragraph = Paragraph(Text(Seq(Line.raw("abcdef").rightAligned)), overflow = Overflow.Wrap)
    val buffer    = rendered(paragraph, 4, 2)
    assert(trimmedLines(buffer) == Seq("abcd", "  ef"))
    assert(paragraph.heightAt(4).contains(2))

  test("a line's alignment is measured in display columns, not characters"):
    // "你好" is two characters but four terminal columns, so centring it in nine columns starts it at column 2.
    val buffer = rendered(Paragraph(Text(Seq(Line.raw("你好").centered))), 9, 1)
    assert(trimmedLines(buffer) == Seq("  你好"))

  test("a line wider than the area is pinned to the left edge whatever it asked for"):
    val buffer = rendered(Paragraph(Text(Seq(Line.raw("abcdef").rightAligned))), 4, 1)
    assert(trimmedLines(buffer) == Seq("abcd"))

  test("a one-column area clips a centred line instead of starting it off-screen"):
    val buffer = rendered(Paragraph(Text(Seq(Line.raw("ab").centered))), 1, 1)
    assert(trimmedLines(buffer) == Seq("a"))

  test("a Text restyled with styled reaches the rendered cells"):
    val buffer = rendered(Paragraph(Text.raw("hi").styled(_.withFg(Color.Green).bold)), 4, 1)
    assert(buffer.get(0, 0).style.fg.contains(Color.Green))
    assert(buffer.get(0, 0).style.modifiers.hasAny(Modifiers.Bold))

  test("restyling a Text leaves its column widths alone"):
    val buffer = rendered(Paragraph(Text.raw("漢字").styled(_.withFg(Color.Red))), 6, 1)
    assert(trimmedLines(buffer) == Seq("漢字"))
    assert(buffer.get(2, 0).symbol == "字") // still two columns for the first character

  test("patchStyle survives the render path and layers over the span's own style"):
    val text   = Text(Seq(Line(Seq(Span("hi", Style.Default.withFg(Color.Green))))))
    val buffer = rendered(Paragraph(text.patchStyle(Style.Default.italic)), 4, 1)
    assert(buffer.get(0, 0).style.fg.contains(Color.Green))
    assert(buffer.get(0, 0).style.modifiers.hasAny(Modifiers.Italic))
