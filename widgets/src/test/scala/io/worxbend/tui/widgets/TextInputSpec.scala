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
    val state  = TextInputState("abc")
    state.moveHome()
    state.moveRight()
    val buffer = renderedWith(state)
    assert(trimmedLines(buffer).head == "abc")
    assert(buffer.get(1, 0).style.modifiers.has(Modifiers.Reverse))
    assert(!buffer.get(0, 0).style.modifiers.has(Modifiers.Reverse))

  test("the cursor at the end highlights the trailing space"):
    val state  = TextInputState("ab")
    val buffer = renderedWith(state)
    assert(buffer.get(2, 0).style.modifiers.has(Modifiers.Reverse))

  test("an unfocused input renders no cursor"):
    val state  = TextInputState("ab")
    val buffer = renderedWith(state, TextInput(showCursor = false))
    assert((0 until 10).forall(x => !buffer.get(x, 0).style.modifiers.has(Modifiers.Reverse)))

  test("the placeholder shows while empty"):
    val buffer = renderedWith(TextInputState(), TextInput(placeholder = "name..."))
    assert(trimmedLines(buffer).head == "name...")

  test("long content scrolls horizontally to keep the cursor visible"):
    val state  = TextInputState("abcdefghij")
    val buffer = renderedWith(state, width = 5)
    // cursor at end: the visible window is the tail of the text plus the cursor cell
    assert(trimmedLines(buffer).head == "ghij")
    assert(buffer.get(4, 0).style.modifiers.has(Modifiers.Reverse))

  test("clear empties value, cursor, and scroll"):
    val state = TextInputState("abc")
    state.clear()
    assert(state.value == "")
    assert(state.cursor == 0)

  test("control characters never enter the model"):
    val state = TextInputState("a\tb") // the constructor seed is filtered, not just insert
    assert(state.value == "ab")
    assert(state.cursor == 2)
    state.insert(Escape + "[31mX")
    assert(state.value == "ab[31mX")
    assert(!state.value.exists(c => Character.isISOControl(c)))
    state.insert(NextLine) // an 8-bit C1 control is dropped too
    assert(state.value == "ab[31mX")

  test("no control character reaches a cell"):
    val buffer = renderedWith(TextInputState("a\tb"))
    assert((0 until 10).forall(x => !buffer.get(x, 0).symbol.exists(c => Character.isISOControl(c))))
    assert(trimmedLines(buffer).head == "ab")

  test("a zero-width cluster is drawn as a blank cell"):
    // a leading combining mark is legitimate content, so it keeps its cell — but the cell may not hold a zero-width
    // symbol, or the backend advances one column further than the terminal did
    val state  = TextInputState(CombiningAcute + "a")
    state.moveHome()
    val buffer = renderedWith(state)
    assert(buffer.get(0, 0).symbol == " ")
    assert(buffer.get(1, 0).symbol == "a")
    assert(buffer.get(0, 0).style.modifiers.has(Modifiers.Reverse))

  test("the cursor on a wide grapheme stays visible at an odd inner width"):
    // the solver must reserve two columns for the cursor cluster, or the render loop drops it and the field looks dead
    val state = TextInputState("你好")
    state.moveLeft() // cursor on the second cluster
    val buffer = renderedWith(state, width = 3)
    assert(state.scrollCluster == 1)
    assert(buffer.get(0, 0).symbol == "好")
    assert(buffer.get(0, 0).style.modifiers.has(Modifiers.Reverse))
    assert(trimmedLines(buffer).head == "好")

  test("a wide-grapheme cursor renders unscrolled when the whole value fits"):
    val state  = TextInputState("你好")
    state.moveLeft()
    val buffer = renderedWith(state, width = 4)
    assert(state.scrollCluster == 0)
    assert(trimmedLines(buffer).head == "你好")
    assert(buffer.get(2, 0).style.modifiers.has(Modifiers.Reverse))

  test("the end-of-text cursor after a wide grapheme still costs one column"):
    val state  = TextInputState("你好") // the cursor defaults past the last cluster
    val buffer = renderedWith(state, width = 3)
    assert(state.scrollCluster == 1)
    assert(buffer.get(0, 0).symbol == "好")
    assert(buffer.get(2, 0).style.modifiers.has(Modifiers.Reverse))

  test("a cluster wider than the whole area terminates the scroll solver"):
    val state  = TextInputState("你")
    state.moveHome()
    val buffer = renderedWith(state, width = 1)
    assert(state.scrollCluster == 0)
    assert(buffer.get(0, 0).symbol == " ", "nothing fits, so the field is blank rather than hung")

  /** Controls and marks spelled by codepoint: a literal one in a source file is invisible. */
  private val Escape: String         = 0x1b.toChar.toString
  private val NextLine: String       = 0x85.toChar.toString // C1
  private val CombiningAcute: String = 0x0301.toChar.toString
