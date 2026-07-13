package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Line, Rect}
import io.worxbend.tui.testsupport.BufferAssertions.trimmedLines

import org.scalatest.funsuite.AnyFunSuite

final class ListViewSpec extends AnyFunSuite:

  private val widget = ListView(Seq("alpha", "beta", "gamma", "delta").map(Line.raw))

  private def renderedWith(state: ListState, width: Int = 10, height: Int = 3): Buffer =
    val buffer = Buffer(Rect(0, 0, width, height))
    widget.render(buffer.area, buffer, state)
    buffer

  test("items render in order with the unselected indent"):
    val buffer = renderedWith(ListState())
    assert(trimmedLines(buffer) == Seq("  alpha", "  beta", "  gamma"))

  test("the selected item gets the highlight symbol"):
    val buffer = renderedWith(ListState(selected = Some(1)))
    assert(trimmedLines(buffer) == Seq("  alpha", "> beta", "  gamma"))

  test("selecting below the viewport scrolls the offset down"):
    val state  = ListState(selected = Some(3))
    val buffer = renderedWith(state)
    assert(trimmedLines(buffer) == Seq("  beta", "  gamma", "> delta"))
    assert(state.offset == 1)

  test("selecting above the current offset scrolls back up"):
    val state  = ListState(selected = Some(0), offset = 2)
    val buffer = renderedWith(state)
    assert(trimmedLines(buffer).head == "> alpha")
    assert(state.offset == 0)

  test("a selection past the end is clamped to the last item"):
    val state  = ListState(selected = Some(99))
    val buffer = renderedWith(state)
    assert(state.selected.contains(3))
    assert(trimmedLines(buffer).last == "> delta")

  test("selectNext and selectPrevious move within bounds"):
    val state = ListState()
    state.selectNext(4)
    assert(state.selected.contains(0))
    state.selectNext(4)
    assert(state.selected.contains(1))
    state.selectPrevious(4)
    state.selectPrevious(4)
    assert(state.selected.contains(0))
