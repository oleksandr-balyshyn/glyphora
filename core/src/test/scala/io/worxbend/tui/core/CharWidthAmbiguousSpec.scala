package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** East Asian Ambiguous measurement: the characters Unicode gives no single width to.
  *
  * The invariant these tests exist to defend is that `WidthMode.Narrow` is byte-for-byte the old behaviour. Everything
  * this library draws is measured with `Narrow`, so if the default answer moved even for one codepoint, every widget
  * would keep laying out one way while the buffer clipped another.
  */
final class CharWidthAmbiguousSpec extends AnyFunSuite:

  // one representative of each family the ambiguous set covers, so a regenerated table that lost a family is caught
  private val ambiguous = Seq(
    "─" -> "box drawing horizontal",
    "│" -> "box drawing vertical",
    "α" -> "Greek small alpha",
    "А" -> "Cyrillic capital A",
    "±" -> "plus-minus sign",
    "×" -> "multiplication sign",
    "←" -> "leftwards arrow",
    "○" -> "white circle",
  )

  test("every ambiguous representative is one column narrow and two columns wide"):
    ambiguous.foreach { (text, name) =>
      assert(CharWidth.of(text) == 1, s"$name should be one column by default")
      assert(CharWidth.of(text, WidthMode.Narrow) == 1, s"$name under Narrow")
      assert(CharWidth.of(text, WidthMode.Wide) == 2, s"$name under Wide")
      assert(CharWidth.isAmbiguousCodePoint(text.codePointAt(0)), s"$name should be classified ambiguous")
    }

  test("the mode-free width functions still answer exactly what they answered before"):
    ambiguous.foreach { (text, name) =>
      assert(CharWidth.of(text) == CharWidth.of(text, WidthMode.Narrow), name)
    }
    assert(CharWidth.of("a─b") == 3)
    assert(CharWidth.of("a─b", WidthMode.Wide) == 4)

  test("a codepoint that is unambiguously wide stays two columns under both modes"):
    Seq("一", "Ａ", "😀").foreach { text =>
      assert(CharWidth.of(text, WidthMode.Narrow) == 2, text)
      assert(CharWidth.of(text, WidthMode.Wide) == 2, text)
      assert(!CharWidth.isAmbiguousCodePoint(text.codePointAt(0)), text)
    }

  test("plain ASCII is one column under both modes, which is why the fast path may skip the check"):
    assert(CharWidth.of("hello world!", WidthMode.Wide) == 12)
    assert(!CharWidth.isAmbiguousCodePoint('a'.toInt))
    assert(!CharWidth.isAmbiguousCodePoint('~'.toInt))

  test("a zero-width mark stays zero under Wide even though the ambiguous set contains combining marks"):
    // U+0301 COMBINING ACUTE ACCENT is East Asian Ambiguous *and* a combining mark. Zero has to win, or "é" written
    // as e + U+0301 would measure three columns in a CJK locale and every following character would land wrong.
    assert(CharWidth.of("é", WidthMode.Wide) == 1)
    assert(CharWidth.of("é", WidthMode.Narrow) == 1)

  test("substringByWidth cuts at the columns the chosen mode counts"):
    // three box-drawing characters: three columns narrow, six wide, so a four-column budget keeps three or two
    assert(CharWidth.substringByWidth("───", 4) == "───")
    assert(CharWidth.substringByWidth("───", 4, WidthMode.Narrow) == "───")
    assert(CharWidth.substringByWidth("───", 4, WidthMode.Wide) == "──")
    assert(CharWidth.substringByWidth("───", 1, WidthMode.Wide) == "")

  test("a span, a line and a text all answer the same question one level up"):
    val span = Span.raw("─α")
    assert(span.width == 2)
    assert(span.widthIn(WidthMode.Narrow) == 2)
    assert(span.widthIn(WidthMode.Wide) == 4)

    val line = Line(Seq(span, Span.raw("ab")))
    assert(line.width == 4)
    assert(line.widthIn(WidthMode.Wide) == 6)

    // the widest line is not the same line under the two policies: "abcde" wins narrow, "──────" wins wide
    val text = Text(Seq(Line.raw("abcde"), Line.raw("───")))
    assert(text.width == 5)
    assert(text.widthIn(WidthMode.Narrow) == 5)
    assert(text.widthIn(WidthMode.Wide) == 6)

  test("empty values measure zero under both modes"):
    assert(CharWidth.of("", WidthMode.Wide) == 0)
    assert(Line.Empty.widthIn(WidthMode.Wide) == 0)
    assert(Text.Empty.widthIn(WidthMode.Wide) == 0)

  test("the border glyphs this library draws with are the ambiguous set, which is why the mode matters"):
    val corners = Seq("┌", "┐", "└", "┘", "├", "┤", "┬", "┴", "┼")
    corners.foreach(glyph => assert(CharWidth.isAmbiguousCodePoint(glyph.codePointAt(0)), glyph))
