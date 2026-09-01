package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** The two layers a [[Text]] carries for the whole block: a base style and a default alignment.
  *
  * Both are *defaults*, not decisions — a [[Line]] that says something of its own still wins for that one row. These
  * tests pin that precedence at the value level; `TextBaseLayerRenderSpec` in `tui-widgets` pins it at the cell level.
  */
final class TextBaseLayerSpec extends AnyFunSuite:

  private val dim = Style.Default.dim
  private val red = Style.Default.withFg(Color.Red)

  test("a text built the ordinary way sets neither layer"):
    assert(Text.raw("a\nb").style == Style.Default)
    assert(Text.raw("a\nb").alignment.isEmpty)
    assert(Text(Seq(Line.raw("a"))).style == Style.Default)
    assert(Text.styled("a", dim).alignment.isEmpty)

  test("withStyle replaces the base layer and leaves every line alone"):
    val text   = Text(Seq(Line.raw("a"), Line.styled(Seq(Span.raw("b")), red)))
    val tinted = text.withStyle(dim)
    assert(tinted.style == dim)
    assert(tinted.lines == text.lines)

  test("withStyleOf edits the base layer rather than replacing it"):
    assert(Text.raw("a").withStyle(red).withStyleOf(_.dim).style == red.dim)

  test("Text.styled(lines, style) sets the base layer without touching the lines"):
    val lines = Seq(Line.raw("a"), Line.raw("b"))
    val text  = Text.styled(lines, dim)
    assert(text.style == dim)
    assert(text.lines == lines)
    assert(text.alignment.isEmpty)

  test("the placement builders set the matching case and leave the lines alone"):
    val text = Text.raw("a\nb")
    assert(text.leftAligned.alignment.contains(Alignment.Left))
    assert(text.centered.alignment.contains(Alignment.Center))
    assert(text.rightAligned.alignment.contains(Alignment.Right))
    assert(text.centered.inheritAlignment.alignment.isEmpty)
    assert(text.centered.lines == text.lines)

  test("aligning the block does not overwrite a line that aligned itself"):
    val text = Text(Seq(Line.raw("a").rightAligned, Line.raw("b"))).centered
    assert(text.lines.head.alignment.contains(Alignment.Right))
    assert(text.lines(1).alignment.isEmpty)
    assert(text.alignment.contains(Alignment.Center))

  test("the line-walking helpers carry both layers through"):
    val text = Text.styled(Seq(Line.raw("a")), dim).centered
    assert(text.styled(_.bold).style == dim)
    assert(text.styled(_.bold).alignment.contains(Alignment.Center))
    assert(text.under(red).style == dim)
    assert(text.patchStyle(red).alignment.contains(Alignment.Center))
    assert(text.appended(Line.raw("b")).style == dim)
    assert(text.appendedAll(Text.raw("c")).alignment.contains(Alignment.Center))
    assert(text.appendedToLast(Span.raw("!")).style == dim)
    assert(Text.Empty.withStyle(dim).appendedToLast(Span.raw("!")).style == dim)

  test("neither layer changes the measured size, including for wide characters"):
    val text = Text.raw("你好\nab").centered.withStyle(dim)
    assert(text.width == 4)
    assert(text.height == 2)
    assert(text.plainText == "你好\nab")
