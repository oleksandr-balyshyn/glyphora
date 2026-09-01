package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class TextSpec extends AnyFunSuite:

  test("span width is display width, not string length"):
    assert(Span.raw("你好").width == 4)

  test("line width sums its spans"):
    val line = Line(Seq(Span.raw("ab"), Span.raw("你")))
    assert(line.width == 4)

  test("Text.raw splits on newlines"):
    val text = Text.raw("one\ntwo")
    assert(text.height == 2)
    assert(text.lines.map(_.spans.head.content) == Seq("one", "two"))

  test("text width is the widest line"):
    assert(Text.raw("a\nlonger\nxy").width == 6)

  test("Span.styled carries the given style and agrees with the case class apply"):
    val warn = Style.Default.bold
    assert(Span.styled("hi", warn) == Span("hi", warn))

  test("Span.styled builds the same single span Line.styled does"):
    val warn = Style.Default.italic
    assert(Line.styled("hi", warn).spans == Seq(Span.styled("hi", warn)))

  test("Span(content) is the unstyled span"):
    assert(Span("hi") == Span.raw("hi"))
    assert(Span("hi").style == Style.Default)

  test("Span.raw still eta-expands as a function value"):
    assert(Seq("a", "你").map(Span.raw).map(_.width) == Seq(1, 2))

  test("styled text carries the style on every line"):
    val text = Text.styled("a\nb", Style.Default.bold)
    assert(text.lines.forall(_.spans.forall(_.style.modifiers.hasAny(Modifiers.Bold))))
