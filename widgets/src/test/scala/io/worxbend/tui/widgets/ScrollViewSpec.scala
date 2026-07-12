package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Style, Widget}
import io.worxbend.tui.testsupport.BufferAssertions.trimmedLines

import org.scalatest.funsuite.AnyFunSuite

final class ScrollViewSpec extends AnyFunSuite:

  private val tallContent: Widget =
    (area, buffer) => (0 until area.height).foreach(y => buffer.setString(area.x, area.y + y, s"row $y", Style.Default))

  private def renderedWith(state: ScrollViewState, height: Int = 3): Buffer =
    val buffer = Buffer(Rect(0, 0, 10, height))
    ScrollView(tallContent, contentHeight = 8).render(buffer.area, buffer, state)
    buffer

  test("shows the top window initially with a scrollbar on the right"):
    val buffer = renderedWith(ScrollViewState())
    val lines = trimmedLines(buffer)
    assert(lines.head.startsWith("row 0"))
    assert(lines(2).startsWith("row 2"))
    assert(buffer.get(9, 0).symbol == "█") // thumb at top

  test("scrolling down shifts the window and clamps at the end"):
    val state = ScrollViewState()
    val _ = renderedWith(state) // establish viewport metrics
    state.scrollDown(3)
    assert(trimmedLines(renderedWith(state)).head.startsWith("row 3"))
    state.scrollDown(99)
    assert(state.offset == 5) // 8 rows - 3 viewport
    assert(trimmedLines(renderedWith(state)).head.startsWith("row 5"))

  test("content that fits renders without a scrollbar"):
    val buffer = Buffer(Rect(0, 0, 10, 8))
    ScrollView(tallContent, contentHeight = 8).render(buffer.area, buffer, ScrollViewState())
    assert(buffer.get(9, 0).symbol != "█")
