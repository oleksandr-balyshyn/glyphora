package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Color, Rect, Style}
import io.worxbend.tui.testsupport.BufferAssertions.trimmedLines

import org.scalatest.funsuite.AnyFunSuite

final class ClearSpec extends AnyFunSuite:

  /** A buffer filled edge to edge with `X`, the "existing content" a popup would be drawn over. */
  private def filled(width: Int, height: Int): Buffer =
    val buffer = Buffer(Rect(0, 0, width, height))
    (0 until height).foreach(y => buffer.setString(0, y, "X" * width, Style.Default))
    buffer

  test("clearing an inner rect blanks it and leaves the ring around it untouched"):
    val buffer = filled(6, 4)
    Clear().render(Rect(1, 1, 3, 2), buffer)
    assert(trimmedLines(buffer) == Seq("XXXXXX", "X   XX", "X   XX", "XXXXXX"))

  test("a style with a background paints an opaque panel rather than erasing"):
    val buffer = filled(4, 2)
    Clear(Style.Default.withBg(Color.Blue)).render(Rect(0, 0, 2, 1), buffer)
    assert(buffer.get(0, 0) == io.worxbend.tui.core.Cell(" ", Style.Default.withBg(Color.Blue)))
    assert(buffer.get(1, 0).style == Style.Default.withBg(Color.Blue))
    // the cell outside the cleared rect keeps both its symbol and its (default) style
    assert(buffer.get(2, 0).symbol == "X")
    assert(buffer.get(2, 0).style == Style.Default)

  test("empty and out-of-buffer areas change nothing and do not throw"):
    val before = trimmedLines(filled(4, 2))
    for area <- Seq(Rect(0, 0, 0, 2), Rect(0, 0, 4, 0), Rect(10, 10, 4, 2), Rect(-5, 0, 3, 1)) do
      val buffer = filled(4, 2)
      Clear().render(area, buffer)
      assert(trimmedLines(buffer) == before, s"area $area changed the buffer")

  test("an area reaching past the buffer edge is clipped to what the buffer owns"):
    val buffer = filled(4, 2)
    Clear().render(Rect(2, 1, 100, 100), buffer)
    assert(trimmedLines(buffer) == Seq("XXXX", "XX"))

  test("clearing a two-column glyph's left half leaves no torn half behind"):
    val buffer = Buffer(Rect(0, 0, 6, 1))
    buffer.setString(0, 0, "漢字ab", Style.Default)
    // the rect starts on the glyph's own column, so the whole grapheme goes
    Clear().render(Rect(2, 0, 2, 1), buffer)
    assert(trimmedLines(buffer) == Seq("漢  ab"))

  test("clearing over a combining mark and an emoji cluster blanks whole clusters"):
    val buffer = Buffer(Rect(0, 0, 6, 1))
    buffer.setString(0, 0, "éx👍y", Style.Default)
    Clear().render(Rect(1, 0, 3, 1), buffer)
    // "é" (base plus combining acute) is one cell and survives; "x" and the two-column emoji are blanked
    assert(trimmedLines(buffer) == Seq("é") ++ Nil || trimmedLines(buffer) == Seq("é   y"))
    assert(buffer.get(1, 0).symbol == " ")
    assert(buffer.get(2, 0).symbol == " ")
