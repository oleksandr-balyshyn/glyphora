package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** Walking a span or a line one terminal cell-unit at a time. */
final class StyledGraphemeSpec extends AnyFunSuite:

  private val bold = Style.Default.bold

  test("an ASCII span yields one element per character, each carrying the resolved style"):
    assert(Span("ab", bold).styledGraphemes(Style.Default).toList == List(StyledGrapheme("a", bold), StyledGrapheme("b", bold)))

  test("the base style is resolved under the span's own"):
    val base    = Style.Default.withFg(Color.Blue)
    val cluster = Span("x", bold).styledGraphemes(base).next()
    assert(cluster.style.fg.contains(Color.Blue))
    assert(cluster.style.modifiers.hasAny(Modifiers.Bold))

  test("the span's own style overrules the base where both speak"):
    val cluster = Span("x", Style.Default.withFg(Color.Red)).styledGraphemes(Style.Default.withFg(Color.Blue)).next()
    assert(cluster.style.fg.contains(Color.Red))

  test("a multi-code-point emoji is one element two columns wide"):
    val clusters = Span.raw("👨‍👩‍👧").styledGraphemes(Style.Default).toList
    assert(clusters.size == 1)
    assert(clusters.head.width == 2)

  test("a letter and its combining accent are one element one column wide"):
    val clusters = Span.raw("e" + 0x0301.toChar).styledGraphemes(Style.Default).toList
    assert(clusters.size == 1)
    assert(clusters.head.width == 1)

  test("a CJK character is one element two columns wide"):
    val clusters = Span.raw("漢").styledGraphemes(Style.Default).toList
    assert(clusters.map(_.width) == List(2))

  test("an empty span yields nothing"):
    assert(Span.raw("").styledGraphemes(Style.Default).isEmpty)

  test("a line flattens its spans in order, each cluster keeping its own span's style"):
    val line     = Line(Seq(Span("a", bold), Span("b", Style.Default.italic), Span.raw("c")))
    val clusters = line.styledGraphemes(Style.Default).toList
    assert(clusters.map(_.cluster) == List("a", "b", "c"))
    assert(clusters.head.style.modifiers.hasAny(Modifiers.Bold))
    assert(clusters(1).style.modifiers.hasAny(Modifiers.Italic))
    assert(clusters(2).style == Style.Default)

  test("an empty line yields nothing"):
    assert(Line.Empty.styledGraphemes(Style.Default).isEmpty)

  test("the cluster widths of a line sum to the line's width"):
    val line = Line(Seq(Span.raw("ab漢"), Span.raw("👍")))
    assert(line.styledGraphemes(Style.Default).map(_.width).sum == line.width)

  test("the spans are walked lazily, so a prefix never touches the tail"):
    val line = Line(Seq(Span.raw("a"), Span.raw("b" * 100_000)))
    assert(line.styledGraphemes(Style.Default).take(1).toList.map(_.cluster) == List("a"))
