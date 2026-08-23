package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Style, Widget}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class ScrollViewSpec extends AnyFunSuite:

  private val tallContent: Widget =
    (area, buffer) => (0 until area.height).foreach(y => buffer.setString(area.x, area.y + y, s"row $y", Style.Default))

  private val view = ScrollView(tallContent, contentHeight = 8)

  test("shows the top window initially with a scrollbar on the right"):
    val buffer = rendered(view, ScrollViewState(), 10, 3)
    val lines  = trimmedLines(buffer)
    assert(lines.head.startsWith("row 0"))
    assert(lines(2).startsWith("row 2"))
    assert(buffer.get(9, 0).symbol == "█") // thumb at top

  test("scrolling down shifts the window and clamps at the end"):
    val state = ScrollViewState()
    val _     = rendered(view, state, 10, 3) // establish viewport metrics
    state.scrollDown(3)
    assert(trimmedLines(rendered(view, state, 10, 3)).head.startsWith("row 3"))
    state.scrollDown(99)
    assert(state.offset == 5) // 8 rows - 3 viewport
    assert(trimmedLines(rendered(view, state, 10, 3)).head.startsWith("row 5"))

  test("content that fits renders without a scrollbar"):
    val buffer = rendered(view, ScrollViewState(), 10, 8)
    assert(buffer.get(9, 0).symbol != "█")
