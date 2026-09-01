package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Color, Line, Modifiers, Rect, Span, Style, Text}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

/** What a [[io.worxbend.tui.core.Line]]'s own base style does once something actually draws it.
  *
  * The core spec pins the values; this one pins the pixels — that the widget's style, the line's style and the span's
  * style reach the terminal cell in that order, and that the layer survives wrapping and horizontal clipping.
  */
final class LineBaseStyleRenderSpec extends AnyFunSuite:

  private val bold  = Style.Default.bold
  private val red   = Style.Default.withFg(Color.Red)
  private val blue  = Style.Default.withFg(Color.Blue)
  private val green = Style.Default.withFg(Color.Green)

  private def cell(buffer: Buffer, x: Int, y: Int): Style = buffer.get(x, y).style

  test("the paragraph style, the line style and the span style all reach the cell, innermost winning"):
    // "ab" takes its colour from the line (red), "cd" chose blue and keeps it; both are bold because the paragraph
    // said bold and neither inner layer says anything about bold.
    val line      = Line.styled(Seq(Span.raw("ab"), Span("cd", blue)), red)
    val paragraph = Paragraph(Text(Seq(line)), style = bold)
    val buffer    = rendered(paragraph, 4, 1)
    assert(trimmedLines(buffer) == Seq("abcd"))
    assert(cell(buffer, 0, 0).fg.contains(Color.Red))
    assert(cell(buffer, 0, 0).modifiers.hasAny(Modifiers.Bold))
    assert(cell(buffer, 2, 0).fg.contains(Color.Blue))
    assert(cell(buffer, 2, 0).modifiers.hasAny(Modifiers.Bold))

  test("a line style survives wrapping, so both rows of a broken line keep it"):
    val line      = Line.styled(Seq(Span.raw("alpha beta")), green)
    val paragraph = Paragraph(Text(Seq(line)), overflow = Overflow.Wrap)
    val buffer    = rendered(paragraph, 5, 2)
    assert(trimmedLines(buffer) == Seq("alpha", "beta"))
    assert(cell(buffer, 0, 0).fg.contains(Color.Green))
    assert(cell(buffer, 0, 1).fg.contains(Color.Green))

  test("a right-aligned line too wide for the area keeps its base style on the surviving end"):
    val line   = Line.styled(Seq(Span.raw("abcdefgh")), red).rightAligned
    val buffer = rendered(Paragraph(Text(Seq(line))), 4, 1)
    assert(trimmedLines(buffer) == Seq("efgh"))
    assert(cell(buffer, 0, 0).fg.contains(Color.Red))

  test("a wide character carries the line style across both of the columns it occupies"):
    val line   = Line.styled(Seq(Span.raw("你a")), red)
    val buffer = rendered(Paragraph(Text(Seq(line))), 3, 1)
    assert(cell(buffer, 0, 0).fg.contains(Color.Red))
    assert(cell(buffer, 2, 0).fg.contains(Color.Red))

  test("a line with no base style renders exactly as it did before the field existed"):
    val styled  = Paragraph(Text(Seq(Line(Seq(Span("hi", blue))))), style = bold)
    val withOut = rendered(styled, 4, 1)
    assert(cell(withOut, 0, 0) == bold.patch(blue))

  test("Buffer.withLines lays the line style under each span, so expected frames can use it"):
    val buffer = Buffer.withLines(Line.styled(Seq(Span.raw("ab"), Span("c", blue)), red))
    assert(buffer.area == Rect(0, 0, 3, 1))
    assert(cell(buffer, 0, 0).fg.contains(Color.Red))
    assert(cell(buffer, 2, 0).fg.contains(Color.Blue))
