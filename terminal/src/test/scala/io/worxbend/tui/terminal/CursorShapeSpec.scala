package io.worxbend.tui.terminal

import io.worxbend.tui.core.Size

import org.scalatest.funsuite.AnyFunSuite

/** DECSCUSR: the parameter numbers, the sequence built from them, and the two backends' halves of the contract. */
final class CursorShapeSpec extends AnyFunSuite:

  test("every shape maps to its DECSCUSR parameter, and the numbers are the ones the standard defines"):
    // Written out here as well as in the source so that renumbering the enum fails a test rather than quietly changing
    // what the terminal is asked for.
    assert(CursorShape.parameter(CursorShape.Default) == 0)
    assert(CursorShape.parameter(CursorShape.BlinkingBlock) == 1)
    assert(CursorShape.parameter(CursorShape.SteadyBlock) == 2)
    assert(CursorShape.parameter(CursorShape.BlinkingUnderline) == 3)
    assert(CursorShape.parameter(CursorShape.SteadyUnderline) == 4)
    assert(CursorShape.parameter(CursorShape.BlinkingBar) == 5)
    assert(CursorShape.parameter(CursorShape.SteadyBar) == 6)

  test("no two shapes share a parameter, so none is unreachable"):
    val parameters = CursorShape.values.toSeq.map(CursorShape.parameter)
    assert(parameters.distinct.size == CursorShape.values.length)

  test("the sequence is CSI n SP q, with the space before the q"):
    // The space is part of the sequence, not formatting: `CSI 5 q` without it is a different, unrelated command.
    assert(AnsiSequences.cursorShape(CursorShape.BlinkingBar) == "[5 q")
    assert(AnsiSequences.cursorShape(CursorShape.Default) == "[0 q")
    assert(AnsiSequences.ResetCursorShape == AnsiSequences.cursorShape(CursorShape.Default))

  test("RestoreAll resets the shape, so a killed process leaves no bar cursor in the user's shell"):
    assert(AnsiSequences.RestoreAll.contains(AnsiSequences.ResetCursorShape))

  test("the reset comes before the show, so the cursor reappears in the shape that was just restored"):
    val restore = AnsiSequences.RestoreAll
    assert(restore.indexOf(AnsiSequences.ResetCursorShape) < restore.indexOf(AnsiSequences.ShowCursor))

  test("a headless backend records the shape and hands it back on close"):
    val backend = HeadlessBackend(Size(10, 3))
    assert(backend.currentCursorShape == CursorShape.Default)
    assert(backend.setCursorShape(CursorShape.SteadyBar).isRight)
    assert(backend.currentCursorShape == CursorShape.SteadyBar)
    assert(backend.close().isRight)
    assert(backend.currentCursorShape == CursorShape.Default)
