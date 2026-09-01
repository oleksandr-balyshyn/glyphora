package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** The line-level base style: the middle layer of the widget → line → span cascade.
  *
  * "Base" means the layer the spans are drawn *on top of*. A span that chose a colour keeps it; a span that said
  * nothing about colour picks the line's up. These tests pin that direction, because getting it backwards renders
  * plausibly wrong rather than obviously wrong — every character would be the same colour and nobody would see which
  * layer had won.
  */
final class LineBaseStyleSpec extends AnyFunSuite:

  private val bold = Style.Default.bold
  private val red  = Style.Default.withFg(Color.Red)
  private val blue = Style.Default.withFg(Color.Blue)

  test("a line built the ordinary way has no base style of its own"):
    assert(Line.raw("x").style == Style.Default)
    assert(Line("x").style == Style.Default)
    assert(Line(Seq(Span.raw("x"))).style == Style.Default)
    assert(Line.styled("x", bold).style == Style.Default)

  test("withStyle sets the base layer and leaves every span exactly as it was"):
    val line   = Line(Seq(Span.raw("a"), Span("b", red)))
    val tinted = line.withStyle(bold)
    assert(tinted.style == bold)
    assert(tinted.spans == line.spans)

  test("withStyleOf edits the base layer rather than replacing it"):
    val line = Line.raw("x").withStyle(red).withStyleOf(_.bold)
    assert(line.style == red.bold)

  test("Line.styled(spans, style) puts the style under the spans instead of onto them"):
    val spans = Seq(Span.raw("a"), Span("b", red))
    val line  = Line.styled(spans, bold)
    assert(line.style == bold)
    assert(line.spans == spans)
    assert(line.alignment.isEmpty)

  test("styledGraphemes resolves base, then the line style, then the span style"):
    // "a" says nothing about colour, so it inherits the line's red; "b" chose blue and keeps it. Both inherit the
    // bold that came in as the widget-level base, because neither of the inner layers speaks about bold.
    val line     = Line.styled(Seq(Span.raw("a"), Span("b", blue)), red)
    val resolved = line.styledGraphemes(bold).toSeq
    assert(resolved.map(_.cluster) == Seq("a", "b"))
    assert(resolved.head.style == bold.patch(red))
    assert(resolved(1).style == bold.patch(red).patch(blue))

  test("a cleared modifier at the line level switches off a modifier the base layer set"):
    val line     = Line.styled(Seq(Span.raw("a")), Style.Default.notBold)
    val resolved = line.styledGraphemes(bold).toSeq
    assert(!resolved.head.style.modifiers.hasAny(Modifiers.Bold))

  test("the span-walking helpers carry the base style and the alignment through"):
    val line = Line.styled(Seq(Span.raw("a")), red).centered
    assert(line.styled(_.bold).style == red)
    assert(line.styled(_.bold).alignment.contains(Alignment.Center))
    assert(line.under(blue).style == red)
    assert(line.patchStyle(blue).alignment.contains(Alignment.Center))
    assert(line.appended(Span.raw("b")).style == red)
    assert(line.appendedAll(Line.raw("c")).alignment.contains(Alignment.Center))

  test("the base style changes nothing about how wide the line is, including for wide characters"):
    val line = Line.styled(Seq(Span.raw("你好"), Span.raw("é")), red)
    assert(line.width == 5)
    assert(line.withStyle(bold).width == line.width)
    assert(line.plainText == "你好é")
