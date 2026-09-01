package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** Building a `Line` or a `Text` a piece at a time. These values are immutable, so every helper returns a copy and
  * leaves the receiver alone; the awkward case each test pins down is the empty one.
  */
final class TextBuildingSpec extends AnyFunSuite:

  test("Line.appended keeps the span order and leaves the receiver untouched"):
    val original = Line(Seq(Span.raw("a")))
    val grown    = original.appended(Span.raw("b"))
    assert(grown.spans.map(_.content) == Seq("a", "b"))
    assert(original.spans.map(_.content) == Seq("a"))

  test("Line.appended on an empty line measures the new span in display columns"):
    val line = Line.Empty.appended(Span.raw("世"))
    assert(line.spans.size == 1)
    assert(line.width == 2) // two columns, not the one UTF-16 code unit

  test("Line.appendedAll with an empty line is the identity in both directions"):
    val line = Line(Seq(Span.raw("a"), Span("b", Style.Default.bold)))
    assert(line.appendedAll(Line.Empty) == line)
    assert(Line.Empty.appendedAll(line) == line)

  test("Text.appended grows the height by one and widens to the new line"):
    val text = Text.raw("ab").appended(Line.raw("longer"))
    assert(text.height == 2)
    assert(text.width == 6)

  test("Text.appendedToLast on an empty text starts the first line"):
    val text = Text.Empty.appendedToLast(Span.raw("x"))
    assert(text.height == 1)
    assert(text.plainText == "x")

  test("Text.appendedToLast fills a present-but-empty last line without adding a row"):
    val text = Text(Seq(Line.raw("first"), Line.Empty)).appendedToLast(Span.raw("x"))
    assert(text.height == 2)
    assert(text.plainText == "first\nx")

  test("Text.appendedToLast touches only the last line"):
    val text = Text(Seq(Line.raw("one"), Line.raw("two"))).appendedToLast(Span.raw("!"))
    assert(text.plainText == "one\ntwo!")

  test("Text.appendedAll with an empty text is the identity in both directions"):
    val text = Text.raw("a\nb")
    assert(text.appendedAll(Text.Empty) == text)
    assert(Text.Empty.appendedAll(text) == text)

  test("Empty is zero-sized"):
    assert(Line.Empty.width == 0)
    assert(Text.Empty.height == 0)
    assert(Text.Empty.width == 0)

  test("a text folded up span by span equals the same text written out"):
    val spans = Seq(Span.raw("你"), Span("好", Style.Default.bold))
    val folded = spans.foldLeft(Text.Empty)((text, span) => text.appendedToLast(span))
    assert(folded == Text(Seq(Line(spans))))
    assert(folded.width == 4)

  test("two spans added together make a one-row line, each keeping its style"):
    val red  = Span("a", Style.Default.withFg(Color.Red))
    val blue = Span("b", Style.Default.withFg(Color.Blue))
    val line = red + blue
    assert(line == Line(Seq(red, blue)))
    assert(line.width == 2)

  test("the horizontal operators agree with the named helpers"):
    val line = Line(Seq(Span.raw("a")))
    assert((line + Span.raw("b")) == line.appended(Span.raw("b")))
    assert((line ++ Line.raw("cd")) == line.appendedAll(Line.raw("cd")))

  test("the vertical operators agree with the named helpers"):
    val text = Text.raw("a")
    assert((text + Line.raw("b")) == text.appended(Line.raw("b")))
    assert((text ++ Text.raw("c\nd")) == text.appendedAll(Text.raw("c\nd")))
    assert((text ++ Text.raw("c\nd")).height == 3)

  test("adding an empty line to a text still adds a blank row"):
    assert((Text.raw("a") + Line.Empty).height == 2)

  test("composed width is measured in columns, not code units"):
    val line = Span.raw("👨‍👩‍👧") + Span.raw("あ")
    assert(line.width == CharWidth.of(line.plainText))
    assert(line.width == 4) // two columns for the family emoji, two for the kana
