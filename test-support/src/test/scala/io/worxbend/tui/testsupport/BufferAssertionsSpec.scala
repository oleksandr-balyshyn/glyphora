package io.worxbend.tui.testsupport

import io.worxbend.tui.core.{Buffer, Cell, Rect, StatefulWidget, Style, Widget}

import org.scalatest.funsuite.AnyFunSuite

/** Tests for the harness the rest of the toolkit asserts through.
  *
  * Around fifty widget suites and every golden frame read their expected values out of [[BufferAssertions]]. If its
  * row-reading step were wrong — one column too many over a wide grapheme, say — hundreds of tests would be wrong in
  * the same direction and every one of them would still be green, because they compare the harness against itself.
  * Nothing else in the repository pins this down, so it is pinned down here against literal source strings.
  */
final class BufferAssertionsSpec extends AnyFunSuite:

  test("a row of wide graphemes reads back as the text that was written"):
    val buffer = Buffer(Rect(0, 0, 10, 1))
    buffer.setString(0, 0, "日本語", Style.Default)
    // three two-column ideographs fill six columns; the continuation cell of each must be stepped over, not doubled
    assert(BufferAssertions.line(buffer, 0) == "日本語    ")
    assert(BufferAssertions.trimmedLines(buffer) == Seq("日本語"))

  test("an emoji cluster reads back whole"):
    val buffer = Buffer(Rect(0, 0, 6, 1))
    buffer.setString(0, 0, "👍ok", Style.Default)
    assert(BufferAssertions.trimmedLines(buffer) == Seq("👍ok"))

  test("a lone combining mark terminates instead of looping"):
    val buffer = Buffer(Rect(0, 0, 3, 1))
    // written as a cell directly: `setString` skips a base-less combining mark, and it is exactly the symbol whose
    // reported width of 0 would leave the reader's column unchanged forever without the lower bound of 1 on the step
    buffer.set(0, 0, Cell("́", Style.Default))
    assert(BufferAssertions.line(buffer, 0) == "́  ")

  test("lines keeps trailing blanks and trimmedLines strips them"):
    val buffer = Buffer(Rect(0, 0, 6, 2))
    buffer.setString(0, 0, "hi", Style.Default)
    assert(BufferAssertions.lines(buffer) == Seq("hi    ", "      "))
    assert(BufferAssertions.trimmedLines(buffer) == Seq("hi", ""))
    assert(BufferAssertions.text(buffer) == "hi\n")

  test("rendered gives the widget a buffer whose area is exactly the requested size"):
    var seen           = Rect(0, 0, 0, 0)
    val widget: Widget = (area, _) => seen = area
    val buffer         = BufferAssertions.rendered(widget, 7, 3)
    assert(buffer.area == Rect(0, 0, 7, 3))
    assert(seen == Rect(0, 0, 7, 3))

  test("the stateful rendered overload passes the caller's state through and lets the widget write it"):
    val widget = new StatefulWidget[StringBuilder]:
      def render(area: Rect, buffer: Buffer, state: StringBuilder): Unit =
        val _ = state.append("rendered")
        buffer.setString(area.x, area.y, state.result(), Style.Default)
    val state  = StringBuilder()
    val buffer = BufferAssertions.rendered(widget, state, 10, 1)
    assert(BufferAssertions.trimmedLines(buffer) == Seq("rendered"))
    assert(state.result() == "rendered")

  test("renderedInto hands the widget the sub-rect, and a widget that honours it leaves the rest blank"):
    // the buffer clips only at its *own* edge, so confining a write to `area` is the widget's job; that is exactly the
    // property this overload exists to test, and a widget that got it wrong would show up in the surrounding cells
    val widget: Widget = (area, buffer) => buffer.setString(area.x, area.y, "xxxx".take(area.width), Style.Default)
    val buffer         = BufferAssertions.renderedInto(widget, Rect(2, 1, 2, 1), 6, 2)
    assert(BufferAssertions.lines(buffer) == Seq("      ", "  xx  "))

  test("a buffer whose area does not start at the origin is read from its own bounds"):
    val buffer = Buffer(Rect(3, 2, 4, 2))
    buffer.setString(3, 2, "ab", Style.Default)
    buffer.setString(3, 3, "cd", Style.Default)
    assert(BufferAssertions.lines(buffer) == Seq("ab  ", "cd  "))
    assert(BufferAssertions.line(buffer, 3) == "cd  ")
