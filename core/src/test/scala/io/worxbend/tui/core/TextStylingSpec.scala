package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** The style-transforming accessors on the three text values: `styled` maps every span's style, `under` lays a base
  * style beneath the spans' own choices.
  */
final class TextStylingSpec extends AnyFunSuite:

  test("styled replaces the style and leaves the content and width alone"):
    val span = Span.raw("你好").styled(_.withFg(Color.Green))
    assert(span.content == "你好")
    assert(span.width == 4)
    assert(span.style.fg.contains(Color.Green))

  test("styled composes the same way two Style calls do"):
    val chained  = Span.raw("x").styled(_.bold).styled(_.withFg(Color.Red))
    val combined = Span("x", Style.Default.bold.withFg(Color.Red))
    assert(chained == combined)

  test("styled with identity changes nothing"):
    assert(Span.raw("x").styled(identity) == Span.raw("x"))

  test("under lets the span's own setting win and inherits the rest"):
    val span = Span("x", Style.Default.withFg(Color.Red)).under(Style.Default.withFg(Color.Blue).withBg(Color.Black))
    assert(span.style.fg.contains(Color.Red))
    assert(span.style.bg.contains(Color.Black))

  test("Line.styled maps every span and keeps their differences"):
    val line = Line(Seq(Span("a", Style.Default.withFg(Color.Red)), Span.raw("b"))).styled(_.bold)
    assert(line.spans.forall(_.style.modifiers.hasAny(Modifiers.Bold)))
    assert(line.spans.head.style.fg.contains(Color.Red))
    assert(line.spans(1).style.fg.isEmpty)

  test("Line.styled on an empty line yields an empty line rather than throwing"):
    assert(Line(Seq.empty).styled(_.bold) == Line(Seq.empty))

  test("Line.under preserves the per-span differences under one base"):
    val line = Line(Seq(Span("a", Style.Default.withFg(Color.Red)), Span.raw("b"))).under(Style.Default.withFg(Color.Blue))
    assert(line.spans.head.style.fg.contains(Color.Red))
    assert(line.spans(1).style.fg.contains(Color.Blue))

  test("Text.styled and Text.under reach every line"):
    val text = Text.raw("a\nb").styled(_.italic).under(Style.Default.withBg(Color.Black))
    assert(text.height == 2)
    assert(text.lines.forall(_.spans.forall(span => span.style.modifiers.hasAny(Modifiers.Italic))))
    assert(text.lines.forall(_.spans.forall(_.style.bg.contains(Color.Black))))

  test("Text.styled on an empty text is an empty text"):
    assert(Text(Seq.empty).styled(_.bold) == Text(Seq.empty))

  test("patchStyle keeps per-span colours and adds the new attribute to all of them"):
    val line = Line(Seq(Span("a", Style.Default.withFg(Color.Red)), Span("b", Style.Default.withFg(Color.Blue))))
      .patchStyle(Style.Default.italic)
    assert(line.spans.map(_.style.fg) == Seq(Some(Color.Red), Some(Color.Blue)))
    assert(line.spans.forall(_.style.modifiers.hasAny(Modifiers.Italic)))

  test("patchStyle lets the argument overrule a field the span already set"):
    val span = Span("a", Style.Default.withFg(Color.Red)).patchStyle(Style.Default.withFg(Color.Blue))
    assert(span.style.fg.contains(Color.Blue))

  test("patchStyle is the mirror of under"):
    val own  = Style.Default.withFg(Color.Red)
    val base = Style.Default.withFg(Color.Blue).withBg(Color.Black)
    assert(Span("a", own).under(base).style == base.patch(own))
    assert(Span("a", own).patchStyle(base).style == own.patch(base))

  test("patchStyle layers associatively across two calls"):
    val text = Text.raw("a\nb")
    val one  = Style.Default.bold
    val two  = Style.Default.withFg(Color.Green)
    assert(text.patchStyle(one).patchStyle(two) == text.patchStyle(one.patch(two)))

  test("patchStyle on empty values is a no-op rather than an error"):
    assert(Line(Seq.empty).patchStyle(Style.Default.bold) == Line(Seq.empty))
    assert(Text(Seq.empty).patchStyle(Style.Default.bold) == Text(Seq.empty))
    assert(Span.raw("").patchStyle(Style.Default).style == Style.Default)
