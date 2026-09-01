package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class LineSpec extends AnyFunSuite:

  test("a line built the ordinary way carries no alignment of its own"):
    assert(Line.raw("x").alignment.isEmpty)
    assert(Line("x").alignment.isEmpty)
    assert(Line.styled("x", Style.Default.bold).alignment.isEmpty)
    assert(Line(Seq(Span.raw("x"))).alignment.isEmpty)

  test("the placement builders set the matching case and leave the spans alone"):
    val line = Line(Seq(Span.raw("a"), Span.raw("b")))
    assert(line.leftAligned.alignment.contains(Alignment.Left))
    assert(line.centered.alignment.contains(Alignment.Center))
    assert(line.rightAligned.alignment.contains(Alignment.Right))
    assert(line.centered.spans == line.spans)

  test("aligning twice keeps the last placement rather than accumulating"):
    assert(Line.raw("x").centered.rightAligned.alignment.contains(Alignment.Right))

  test("width is measured in display columns and is unaffected by alignment"):
    val line = Line.raw("你好").centered
    assert(line.width == 4)
