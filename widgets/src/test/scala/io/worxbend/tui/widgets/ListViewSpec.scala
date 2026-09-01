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

  test("selectLast scrolls to the bottom without the state ever touching the offset"):
    val fifty = ListView((0 until 50).map(index => s"item-$index"))
    val state = ListState()
    state.selectLast(50)
    assert(state.selected.contains(49))
    assert(state.offset == 0) // the state does not know the viewport height, so it leaves the offset to the render
    val buffer = rendered(fifty, state, 12, 5)
    assert(trimmedLines(buffer) == Seq("  item-45", "  item-46", "  item-47", "  item-48", "> item-49"))
    assert(state.offset == 45)

  test("selectFirst and selectBy make the Home and PageUp moves"):
    val state = ListState()
    state.selectLast(50)
    state.selectBy(50, -10)
    assert(state.selected.contains(39))
    state.selectBy(50, -100) // a page jump past the start stops at the first item rather than wrapping
    assert(state.selected.contains(0))
    state.selectBy(50, +10)
    assert(state.selected.contains(10))
    state.selectFirst(50)
    assert(state.selected.contains(0))

  test("the jump moves are no-ops on an empty list"):
    val state = ListState(selected = Some(2))
    state.selectFirst(0)
    state.selectLast(0)
    state.selectBy(0, +5)
    assert(state.selected.contains(2)) // untouched, exactly as selectNext/selectPrevious leave it

  test("scrollPadding scrolls the list before the highlight reaches the bottom row"):
    val twenty  = ListView((0 until 20).map(index => s"item-$index"))
    val atSeven = ListState(selected = Some(7), scrollPadding = 2)
    val atEight = ListState(selected = Some(8), scrollPadding = 2)
    // rows 0..9 are on screen; with two rows of padding the highlight may rest on row 7 without scrolling
    assert(trimmedLines(rendered(twenty, atSeven, 12, 10)).head == "  item-0")
    assert(atSeven.offset == 0)
    // one further step scrolls the list under the highlight rather than pinning it lower
    assert(trimmedLines(rendered(twenty, atEight, 12, 10)).head == "  item-1")
    assert(atEight.offset == 1)

  test("the default scrollPadding of zero leaves the existing scroll behaviour untouched"):
    val twenty   = ListView((0 until 20).map(index => s"item-$index"))
    val unpadded = ListState(selected = Some(9))
    assert(trimmedLines(rendered(twenty, unpadded, 12, 10)).head == "  item-0")
    assert(unpadded.offset == 0)

  test("a bottom-to-top list sits against the floor of a taller area"):
    val threeItems = ListView(Seq("alpha", "beta", "gamma"), direction = ListDirection.BottomToTop)
    val buffer     = rendered(threeItems, ListState(), 10, 5)
    // the first item is drawn on the *last* row and later items climb, so the empty rows end up above the list
    assert(trimmedLines(buffer) == Seq("", "", "  gamma", "  beta", "  alpha"))

  test("a top-to-bottom list of the same items leaves the empty rows underneath"):
    val threeItems = ListView(Seq("alpha", "beta", "gamma"))
    val buffer     = rendered(threeItems, ListState(), 10, 5)
    assert(trimmedLines(buffer) == Seq("  alpha", "  beta", "  gamma", "", ""))

  test("the direction does not change which items are visible or where the offset lands"):
    val downwards = ListView(Seq("a", "b", "c", "d", "e", "f", "g"))
    val upwards   = downwards.copy(direction = ListDirection.BottomToTop)
    val downState = ListState(selected = Some(6))
    val upState   = ListState(selected = Some(6))
    val down      = rendered(downwards, downState, 10, 3)
    val up        = rendered(upwards, upState, 10, 3)
    assert(downState.offset == upState.offset)
    // the same three rows, mirrored: the selected item is on the bottom row one way and the top row the other
    assert(trimmedLines(down) == Seq("  e", "  f", "> g"))
    assert(trimmedLines(up) == Seq("> g", "  f", "  e"))

  test("a bottom-to-top list in a one-row area shows only the first visible item"):
    val widget = ListView(Seq("alpha", "beta"), direction = ListDirection.BottomToTop)
    val buffer = rendered(widget, ListState(), 10, 1)
    assert(trimmedLines(buffer) == Seq("  alpha"))

  test("a bottom-to-top list truncates wide characters by display width, not by character count"):
    // each CJK ideograph is two columns wide, so four of them need eight columns; a six-column area fits two of them
    // after the two-column unselected indent
    val widget = ListView(Seq("日本語版"), direction = ListDirection.BottomToTop)
    val buffer = rendered(widget, ListState(), 6, 3)
    assert(trimmedLines(buffer) == Seq("", "", "  日本"))

  test("plain strings and styled lines can be mixed in one list"):
    val mixed  = ListView(Seq("alpha", Line.styled("beta", Style.Default.bold)))
    val buffer = rendered(mixed, ListState(), 10, 2)
    assert(trimmedLines(buffer) == Seq("  alpha", "  beta"))
    assert(buffer.get(2, 1).style.modifiers.hasAny(Modifiers.Bold))
    assert(!buffer.get(2, 0).style.modifiers.hasAny(Modifiers.Bold))
