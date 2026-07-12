package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Modifiers, Rect}
import io.worxbend.tui.testsupport.BufferAssertions.trimmedLines

import org.scalatest.funsuite.AnyFunSuite

final class TextInputSpec extends AnyFunSuite:

  private def renderedWith(state: TextInputState, input: TextInput = TextInput(), width: Int = 10): Buffer =
    val buffer = Buffer(Rect(0, 0, width, 1))
    input.render(buffer.area, buffer, state)
    buffer

  test("typing inserts at the cursor"):
    val state = TextInputState()
    state.insert("ab")
    state.moveLeft()
    state.insert("X")
    assert(state.value == "aXb")
    assert(state.cursor == 2)

  test("backspace removes the cluster before the cursor; delete the one under it"):
    val state = TextInputState("abc")
    state.moveLeft()
    state.backspace()
    assert(state.value == "ac")
    state.moveHome()
    state.delete()
    assert(state.value == "c")

  test("cursor movement is cluster-safe for emoji and combining marks"):
    val state = TextInputState("a👍🏽é")
    state.moveEnd()
    assert(state.cursor == 3) // three clusters, not codepoints or chars
    state.backspace()
    assert(state.value == "a👍🏽")
    state.backspace()
    assert(state.value == "a")

  test("the value renders with the cursor highlighted at its cluster"):
    val state = TextInputState("abc")
    state.moveHome()
    state.moveRight()
    val buffer = renderedWith(state)
    assert(trimmedLines(buffer).head == "abc")
    assert(buffer.get(1, 0).style.modifiers.has(Modifiers.Reverse))
    assert(!buffer.get(0, 0).style.modifiers.has(Modifiers.Reverse))

  test("the cursor at the end highlights the trailing space"):
    val state = TextInputState("ab")
    val buffer = renderedWith(state)
    assert(buffer.get(2, 0).style.modifiers.has(Modifiers.Reverse))

  test("an unfocused input renders no cursor"):
    val state = TextInputState("ab")
    val buffer = renderedWith(state, TextInput(showCursor = false))
    assert((0 until 10).forall(x => !buffer.get(x, 0).style.modifiers.has(Modifiers.Reverse)))

  test("the placeholder shows while empty"):
    val buffer = renderedWith(TextInputState(), TextInput(placeholder = "name..."))
    assert(trimmedLines(buffer).head == "name...")

  test("long content scrolls horizontally to keep the cursor visible"):
    val state = TextInputState("abcdefghij")
    val buffer = renderedWith(state, width = 5)
    // cursor at end: the visible window is the tail of the text plus the cursor cell
    assert(trimmedLines(buffer).head == "ghij")
    assert(buffer.get(4, 0).style.modifiers.has(Modifiers.Reverse))

  test("clear empties value, cursor, and scroll"):
    val state = TextInputState("abc")
    state.clear()
    assert(state.value == "")
    assert(state.cursor == 0)
