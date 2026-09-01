package io.worxbend.tui.testsupport

import io.worxbend.tui.core.{Buffer, Cell, Color, Line, Position, Rect, Span, Style}

import org.scalatest.funsuite.AnyFunSuite

/** Tests for the whole-frame comparison in [[BufferAssertions]].
  *
  * The point of `assertEquals` is that it sees what the string helpers cannot: the `Style` of every cell, and which
  * column a wide grapheme actually landed in. Several cases below therefore assert *both* that `assertEquals` fails and
  * that `text` would have passed, because the second half is what says why the helper exists at all.
  */
final class BufferEqualitySpec extends AnyFunSuite:

  private def buffer(width: Int, height: Int)(fill: Buffer => Unit): Buffer =
    val target = Buffer(Rect(0, 0, width, height))
    fill(target)
    target

  test("two identically filled buffers are equal"):
    val actual   = buffer(6, 2)(_.setString(0, 0, "hi", Style.Default))
    val expected = buffer(6, 2)(_.setString(0, 0, "hi", Style.Default))
    BufferAssertions.assertEquals(actual, expected)
    assert(BufferAssertions.cellDifferences(actual, expected).isEmpty)

  test("a style-only difference fails, where the text helpers pass"):
    val actual   = buffer(4, 1)(_.setString(0, 0, "ok", Style.Default.withFg(Color.Red)))
    val expected = buffer(4, 1)(_.setString(0, 0, "ok", Style.Default))
    // the glyphs agree, so every string-level assertion in the repository would call these two frames the same frame
    assert(BufferAssertions.text(actual) == BufferAssertions.text(expected))
    val failure  = intercept[AssertionError](BufferAssertions.assertEquals(actual, expected))
    assert(failure.getMessage.contains("2 of 4 cells differ"))
    assert(failure.getMessage.contains("(0,0)"))
    assert(failure.getMessage.contains("Red"))

  test("a label is written in front of the failure"):
    val actual   = buffer(2, 1)(_.setString(0, 0, "a", Style.Default))
    val expected = buffer(2, 1)(_.setString(0, 0, "b", Style.Default))
    val failure  = intercept[AssertionError](BufferAssertions.assertEquals(actual, expected, "after the second press"))
    assert(failure.getMessage.startsWith("after the second press: "))

  test("differing areas are reported as areas, with no per-cell listing"):
    val actual   = buffer(4, 2)(_ => ())
    val expected = buffer(4, 3)(_ => ())
    val failure  = intercept[AssertionError](BufferAssertions.assertEquals(actual, expected))
    assert(failure.getMessage.contains("does not match expected"))
    // "cell 1 differs" would be misleading here: the two frames do not share a coordinate space to compare in
    assert(!failure.getMessage.contains("1. ("))

  test("differences are listed in row-major order"):
    val actual    = buffer(3, 2) { target =>
      target.setString(0, 0, "abc", Style.Default)
      target.setString(0, 1, "abc", Style.Default)
    }
    val expected  = buffer(3, 2) { target =>
      target.setString(0, 0, "aXc", Style.Default)
      target.setString(0, 1, "Ybc", Style.Default)
    }
    val positions = BufferAssertions.cellDifferences(actual, expected).map((position, _, _) => position)
    assert(positions == Seq(Position(1, 0), Position(0, 1)))
    val failure   = intercept[AssertionError](BufferAssertions.assertEquals(actual, expected))
    assert(failure.getMessage.contains("2 of 6 cells differ"))
    assert(failure.getMessage.indexOf("(1,0)") < failure.getMessage.indexOf("(0,1)"))

  test("more differences than the cap are summarised rather than printed"):
    val actual   = buffer(30, 1)(_.setString(0, 0, "a" * 30, Style.Default))
    val expected = buffer(30, 1)(_.setString(0, 0, "b" * 30, Style.Default))
    val failure  = intercept[AssertionError](BufferAssertions.assertEquals(actual, expected))
    assert(failure.getMessage.contains("30 of 30 cells differ"))
    assert(failure.getMessage.contains("and 10 more"))
    assert(failure.getMessage.contains("20. (19,0)"))
    assert(!failure.getMessage.contains("21. "))

  test("the same wide grapheme in a different column is a difference"):
    val actual   = buffer(6, 1)(_.setString(0, 0, "日", Style.Default))
    val expected = buffer(6, 1)(_.setString(1, 0, "日", Style.Default))
    // both frames read as one ideograph; only the column it occupies differs, and the trimmed rows cannot see that
    assert(BufferAssertions.trimmedLines(actual) == Seq("日"))
    assert(BufferAssertions.trimmedLines(expected) == Seq(" 日"))
    val failure  = intercept[AssertionError](BufferAssertions.assertEquals(actual, expected))
    // columns 0 and 1 swap the ideograph for a blank and back; a continuation cell holds a blank either way, so the
    // pair of columns that carries the glyph is what differs
    assert(failure.getMessage.contains("2 of 6 cells differ"))
    assert(failure.getMessage.contains("(0,0)") && failure.getMessage.contains("(1,0)"))

  test("a combining mark is compared as the cell it occupies"):
    val actual   = buffer(3, 1)(_.set(0, 0, Cell("é", Style.Default)))
    val expected = buffer(3, 1)(_.set(0, 0, Cell("e", Style.Default)))
    assert(BufferAssertions.cellDifferences(actual, expected).size == 1)
    intercept[AssertionError](BufferAssertions.assertEquals(actual, expected))

  test("cellDifferences refuses two buffers of different shapes"):
    val failure = intercept[IllegalArgumentException](
      BufferAssertions.cellDifferences(buffer(2, 1)(_ => ()), buffer(2, 2)(_ => ()))
    )
    assert(failure.getMessage.contains("same area"))

  test("buffered sizes an expected frame to the widest row and the number of rows"):
    val expected = BufferAssertions.buffered("hello", "hi")
    assert(expected.area == Rect(0, 0, 5, 2))
    assert(BufferAssertions.lines(expected) == Seq("hello", "hi   "))

  test("buffered measures a row in columns, not in characters"):
    // three ideographs are three `Char`s and six columns; a buffer sized by character count would be three wide and
    // would drop the third glyph at its edge instead of showing it
    val expected = BufferAssertions.buffered("日本語")
    assert(expected.area.width == 6)
    assert(BufferAssertions.trimmedLines(expected) == Seq("日本語"))

  test("buffered with no rows is an empty frame"):
    assert(BufferAssertions.buffered().area == Rect(0, 0, 0, 0))

  test("buffered from styled lines keeps each span's own style over the base"):
    val expected = BufferAssertions.buffered(
      Seq(Line(Seq(Span("ok", Style.Default.withFg(Color.Green)), Span("!", Style.Default)))),
      Style.Default.withBg(Color.Black),
    )
    assert(expected.get(0, 0).style.fg.contains(Color.Green))
    assert(expected.get(0, 0).style.bg.contains(Color.Black))
    // the second span sets no foreground of its own, so the base's is all that is left
    assert(expected.get(2, 0).style.fg.isEmpty)
    assert(expected.get(2, 0).style.bg.contains(Color.Black))

  test("a rendered frame compares equal to the buffered frame that describes it"):
    val actual = BufferAssertions.rendered(
      (area, target) => target.setString(area.x, area.y, "ab", Style.Default),
      width = 4,
      height = 1,
    )
    BufferAssertions.assertEquals(actual, BufferAssertions.buffered("ab  "))

  test("an empty area is equal to itself and has nothing to report"):
    val actual   = Buffer(Rect(0, 0, 0, 0))
    val expected = Buffer(Rect(0, 0, 0, 0))
    BufferAssertions.assertEquals(actual, expected)
    assert(BufferAssertions.cellDifferences(actual, expected).isEmpty)
