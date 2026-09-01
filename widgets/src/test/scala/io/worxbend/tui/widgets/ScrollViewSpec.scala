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

  test("last jumps to the bottom of the content and first comes back to the top"):
    val state = ScrollViewState()
    val _     = rendered(view, state, 10, 3) // establish viewport metrics
    state.last()
    assert(state.offset == 5)
    assert(trimmedLines(rendered(view, state, 10, 3)).head.startsWith("row 5"))
    state.first()
    assert(state.offset == 0)
    assert(trimmedLines(rendered(view, state, 10, 3)).head.startsWith("row 0"))

  test("a page moves by the measured viewport, not by a fixed step"):
    val state = ScrollViewState()
    val _     = rendered(view, state, 10, 3)
    state.pageDown()
    assert(state.offset == 3) // one viewport of three rows, not the DSL's ten-row default
    state.pageDown()
    assert(state.offset == 5) // clamped at the bottom rather than running past the content
    state.pageUp()
    assert(state.offset == 2)

  test("scrollTo and scrollBy clamp into the scrollable range"):
    val state = ScrollViewState()
    val _     = rendered(view, state, 10, 3)
    state.scrollTo(999)
    assert(state.offset == 5)
    state.scrollTo(-4)
    assert(state.offset == 0)
    state.scrollBy(2)
    assert(state.offset == 2)
    state.scrollBy(-99)
    assert(state.offset == 0)

  test("the moves are no-ops on a state that has never been rendered"):
    // the content and viewport heights are recorded during render, so before the first frame there is nothing to
    // measure a move against and every move must leave the offset alone rather than guess
    val fresh = ScrollViewState()
    fresh.last()
    assert(fresh.offset == 0)
    fresh.pageDown()
    assert(fresh.offset == 0)
    fresh.scrollTo(50)
    assert(fresh.offset == 0)
