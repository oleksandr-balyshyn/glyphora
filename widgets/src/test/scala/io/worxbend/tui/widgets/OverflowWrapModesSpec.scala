package io.worxbend.tui.widgets

import io.worxbend.tui.core.Text
import io.worxbend.tui.testsupport.BufferAssertions.rendered

import org.scalatest.funsuite.AnyFunSuite

/** The one decision the three wrapping modes differ by: what becomes of the blanks at the head of a row. */
final class OverflowWrapModesSpec extends AnyFunSuite:

  private def rows(content: String, overflow: Overflow, width: Int, height: Int): Seq[String] =
    val buffer = rendered(Paragraph(Text.raw(content), overflow = overflow), width, height)
    (0 until height).map(row => (0 until width).map(column => buffer.get(column, row).symbol).mkString)

  test("Wrap keeps the line's own indent and drops the blank a break landed on"):
    assert(rows("  * a long bullet", Overflow.Wrap, 10, 2) == Seq("  * a long", "bullet    "))

  test("WrapTrimmed drops the indent as well, so every row is flush left"):
    assert(rows("  * a long bullet", Overflow.WrapTrimmed, 10, 2) == Seq("* a long  ", "bullet    "))

  test("WrapPreserved carries the break's blank onto the next row"):
    assert(rows("  * a long bullet", Overflow.WrapPreserved, 10, 2) == Seq("  * a long", " bullet   "))

  test("WrapTrimmed still trims a line short enough not to need breaking"):
    assert(rows("   short", Overflow.WrapTrimmed, 10, 1) == Seq("short     "))

  test("WrapPreserved drops a blank run that would leave the word no room"):
    // five blanks and a five-column word cannot share a six-column row; keeping the blanks would cost a row that
    // shows nothing, so they go and the word keeps the row.
    assert(rows("ab     cdefg", Overflow.WrapPreserved, 6, 2) == Seq("ab    ", "cdefg "))

  test("a whitespace-only line is one blank row in every mode"):
    Seq(Overflow.Wrap, Overflow.WrapTrimmed, Overflow.WrapPreserved).foreach { overflow =>
      assert(rows("    ", overflow, 3, 1) == Seq("   "), overflow.toString)
      assert(Paragraph(Text.raw("    "), overflow = overflow).heightAt(3).contains(1), overflow.toString)
    }

  test("trimming never splits a grapheme cluster"):
    // a wide character owns two columns, the second of which reads back as an empty continuation cell
    assert(rows(" 你 好", Overflow.WrapTrimmed, 3, 2) == Seq("你  ", "好  "))

  test("heightAt agrees with what each mode renders"):
    val content = "  indented text that has to wrap more than once here"
    Seq(Overflow.Wrap, Overflow.WrapTrimmed, Overflow.WrapPreserved).foreach { overflow =>
      val paragraph = Paragraph(Text.raw(content), overflow = overflow)
      Seq(1, 3, 7, 12, 40).foreach { width =>
        val drawn = rows(content, overflow, width, 20).count(_.trim.nonEmpty)
        assert(paragraph.heightAt(width).exists(_ >= drawn), s"$overflow at $width")
      }
    }

  test("every wrapping mode reports that it wraps and Clip does not"):
    assert(Overflow.Wrap.wraps && Overflow.WrapTrimmed.wraps && Overflow.WrapPreserved.wraps)
    assert(!Overflow.Clip.wraps)
