package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Modifiers, Rect}
import io.worxbend.tui.testsupport.BufferAssertions.trimmedLines

import org.scalatest.funsuite.AnyFunSuite

final class TextAreaSpec extends AnyFunSuite:

  private def renderedWith(state: TextAreaState, width: Int = 10, height: Int = 4): Buffer =
    val buffer = Buffer(Rect(0, 0, width, height))
    TextArea().render(buffer.area, buffer, state)
    buffer

  test("inserting text with newlines splits into lines"):
    val state = TextAreaState()
    state.insert("one\ntwo")
    assert(state.value == "one\ntwo")
    assert(state.lineCount == 2)
    assert(state.cursor == (1, 3))

  test("newline splits the current line at the cursor"):
    val state = TextAreaState("abcd")
    state.moveHome()
    state.moveRight()
    state.moveRight()
    state.newline()
    assert(state.value == "ab\ncd")
    assert(state.cursor == (1, 0))

  test("backspace at column zero joins with the previous line"):
    val state = TextAreaState("ab\ncd")
    state.moveHome()
    state.backspace()
    assert(state.value == "abcd")
    assert(state.cursor == (0, 2))

  test("delete at line end joins with the next line"):
    val state = TextAreaState("ab\ncd")
    state.moveUp()
    state.moveEnd()
    state.delete()
    assert(state.value == "abcd")

  test("vertical movement clamps the column to the target line length"):
    val state = TextAreaState("long line\nab")
    assert(state.cursor == (1, 2))
    state.moveUp()
    assert(state.cursor == (0, 2))
    state.moveEnd()
    state.moveDown()
    assert(state.cursor == (1, 2))

  test("left and right wrap across line boundaries"):
    val state = TextAreaState("ab\ncd")
    state.moveUp()
    state.moveEnd()
    state.moveRight()
    assert(state.cursor == (1, 0))
    state.moveLeft()
    assert(state.cursor == (0, 2))

  test("editing is cluster-safe for emoji"):
    val state = TextAreaState("a👍🏽b")
    state.moveEnd()
    state.backspace()
    state.backspace()
    assert(state.value == "a")

  test("undo restores text and cursor across several edits"):
    val state = TextAreaState("start")
    state.insert("!")
    state.newline()
    state.insert("more")
    assert(state.value == "start!\nmore")
    state.undo()
    assert(state.value == "start!\n")
    state.undo()
    assert(state.value == "start!")
    state.undo()
    assert(state.value == "start")
    assert(state.cursor == (0, 5))
    state.undo() // empty history: no-op
    assert(state.value == "start")

  test("renders lines with the cursor highlighted on its cell"):
    val state = TextAreaState("ab\ncd")
    state.moveUp() // cursor to (0, 2): end of first line
    val buffer = renderedWith(state)
    assert(trimmedLines(buffer).take(2) == Seq("ab", "cd"))
    assert(buffer.get(2, 0).style.modifiers.has(Modifiers.Reverse))

  test("vertical scroll follows the cursor"):
    val state  = TextAreaState("1\n2\n3\n4\n5\n6")
    val buffer = renderedWith(state, height = 3) // cursor on line 5 (index 5)
    assert(state.scrollRow == 3)
    assert(trimmedLines(buffer).head == "4")

  test("horizontal scroll follows the cursor on long lines"):
    val state  = TextAreaState("abcdefghij")
    val buffer = renderedWith(state, width = 5, height = 1)
    assert(state.scrollColumn > 0)
    assert(buffer.get(4, 0).style.modifiers.has(Modifiers.Reverse))

  test("redo re-applies undone edits and a fresh edit clears the redo history"):
    val state = TextAreaState("a")
    state.insert("b")
    state.insert("c")
    assert(state.value == "abc")
    state.undo()
    state.undo()
    assert(state.value == "a")
    state.redo()
    assert(state.value == "ab")
    state.redo()
    assert(state.value == "abc")
    state.undo()
    state.insert("X") // new edit invalidates the redo branch
    state.redo()
    assert(state.value == "abX")
