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
    val buffer = rendered(Paragraph(Text.raw("abcdef"), wrap = true), 4, 2)
    assert(trimmedLines(buffer) == Seq("abcd", "ef"))

  test("wrapping never splits a wide character"):
    val buffer = rendered(Paragraph(Text.raw("ab你cd"), wrap = true), 3, 2)
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
    assert(buffer.get(0, 0).style.modifiers.has(Modifiers.Bold))
    assert(buffer.get(1, 0).style.fg.contains(Color.Red))
    assert(buffer.get(1, 0).style.modifiers.has(Modifiers.Bold))

  test("excess lines are clipped at the area height"):
    val buffer = rendered(Paragraph(Text.raw("1\n2\n3")), 3, 2)
    assert(trimmedLines(buffer) == Seq("1", "2"))
