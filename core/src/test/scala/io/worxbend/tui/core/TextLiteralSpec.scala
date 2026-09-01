package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** `Line.of` and `Text.of`: building a row, or a block of rows, from a mixture of plain strings and already-built
  * pieces without writing the wrapper around each plain one.
  */
final class TextLiteralSpec extends AnyFunSuite:

  private val bold = Style.Default.bold

  test("Line.of promotes a plain string to an unstyled span and passes a span through untouched"):
    val highlighted = Span("Remy", bold)
    val line        = Line.of("Name: ", highlighted)
    assert(line.spans == Seq(Span.raw("Name: "), highlighted))
    assert(line.plainText == "Name: Remy")

  test("Line.of with no arguments is the empty line, not a line holding one empty span"):
    assert(Line.of() == Line.Empty)
    assert(Line.of().width == 0)

  test("a line built by Line.of starts with no alignment and no base style, like any other"):
    val line = Line.of("x")
    assert(line.alignment.isEmpty)
    assert(line.style == Style.Default)

  test("Line.of measures its width in display columns, so wide characters count double"):
    assert(Line.of("日本", Span("a", bold)).width == 5)
    // a combining accent occupies no column of its own: "e" plus U+0301 is one column, not two
    assert(Line.of("é").width == 1)

  test("Text.of makes one row per argument and does not split on newlines"):
    val block = Text.of("first", Line.of("second ", Span("!", bold)))
    assert(block.height == 2)
    assert(block.lines.head == Line.raw("first"))
    assert(block.lines(1).spans.size == 2)

  test("Text.of and Text.raw differ on an embedded newline, which is the whole reason both exist"):
    assert(Text.of("a\nb").height == 1)
    assert(Text.raw("a\nb").height == 2)

  test("Text.of with no arguments is the empty text"):
    assert(Text.of() == Text.Empty)
    assert(Text.of().width == 0)

  test("Text.of keeps whatever alignment and style the lines handed to it already carried"):
    val block = Text.of("plain", Line.raw("odd").centered.withStyle(bold))
    assert(block.lines.head.alignment.isEmpty)
    assert(block.lines(1).alignment.contains(Alignment.Center))
    assert(block.lines(1).style == bold)
    assert(block.alignment.isEmpty)
