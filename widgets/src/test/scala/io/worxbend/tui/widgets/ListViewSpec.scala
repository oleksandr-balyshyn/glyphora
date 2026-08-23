package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Line, Modifiers, Style}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class ListViewSpec extends AnyFunSuite:

  private val widget = ListView(Seq("alpha", "beta", "gamma", "delta"))

  test("items render in order with the unselected indent"):
    val buffer = rendered(widget, ListState(), 10, 3)
    assert(trimmedLines(buffer) == Seq("  alpha", "  beta", "  gamma"))

  test("the selected item gets the highlight symbol"):
    val buffer = rendered(widget, ListState(selected = Some(1)), 10, 3)
    assert(trimmedLines(buffer) == Seq("  alpha", "> beta", "  gamma"))

  test("selecting below the viewport scrolls the offset down"):
    val state  = ListState(selected = Some(3))
    val buffer = rendered(widget, state, 10, 3)
    assert(trimmedLines(buffer) == Seq("  beta", "  gamma", "> delta"))
    assert(state.offset == 1)

  test("selecting above the current offset scrolls back up"):
    val state  = ListState(selected = Some(0), offset = 2)
    val buffer = rendered(widget, state, 10, 3)
    assert(trimmedLines(buffer).head == "> alpha")
    assert(state.offset == 0)

  test("a selection past the end is clamped to the last item"):
    val state  = ListState(selected = Some(99))
    val buffer = rendered(widget, state, 10, 3)
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

  test("plain strings and styled lines can be mixed in one list"):
    val mixed  = ListView(Seq("alpha", Line.styled("beta", Style.Default.bold)))
    val buffer = rendered(mixed, ListState(), 10, 2)
    assert(trimmedLines(buffer) == Seq("  alpha", "  beta"))
    assert(buffer.get(2, 1).style.modifiers.hasAny(Modifiers.Bold))
    assert(!buffer.get(2, 0).style.modifiers.hasAny(Modifiers.Bold))
