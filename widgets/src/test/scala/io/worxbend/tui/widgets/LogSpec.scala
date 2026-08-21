package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Line, Modifiers, Rect, Style}
import io.worxbend.tui.testsupport.BufferAssertions.trimmedLines

import org.scalatest.funsuite.AnyFunSuite

final class LogSpec extends AnyFunSuite:

  private def filled(lines: Int): LogState =
    val state = LogState()
    (1 to lines).foreach(n => state.append(s"line $n"))
    state

  test("the log follows the tail by default"):
    val buffer = Buffer(Rect(0, 0, 10, 3))
    Log().render(buffer.area, buffer, filled(5))
    assert(trimmedLines(buffer) == Seq("line 3", "line 4", "line 5"))

  test("scrolling up detaches follow; scrolling back to the bottom re-attaches"):
    val state  = filled(5)
    val buffer = Buffer(Rect(0, 0, 10, 3))
    Log().render(buffer.area, buffer, state) // establishes offset = 2
    state.scrollUp(2)
    assert(!state.follow)
    val detached = Buffer(Rect(0, 0, 10, 3))
    Log().render(detached.area, detached, state)
    assert(trimmedLines(detached) == Seq("line 1", "line 2", "line 3"))
    state.append("line 6") // arrives while detached: view must not move
    val stillDetached = Buffer(Rect(0, 0, 10, 3))
    Log().render(stillDetached.area, stillDetached, state)
    assert(trimmedLines(stillDetached) == Seq("line 1", "line 2", "line 3"))
    state.scrollDown(9, viewportHeight = 3)
    assert(state.follow)

  test("the ring drops the oldest lines past the cap"):
    val state  = LogState(maxLines = 3)
    (1 to 5).foreach(n => state.append(s"line $n"))
    assert(state.size == 3)
    val buffer = Buffer(Rect(0, 0, 10, 3))
    Log().render(buffer.area, buffer, state)
    assert(trimmedLines(buffer) == Seq("line 3", "line 4", "line 5"))

  test("styled lines keep their span styles in the log"):
    val state  = LogState()
    state.append(Line.styled("err", Style.Default.bold))
    val buffer = Buffer(Rect(0, 0, 5, 1))
    Log().render(buffer.area, buffer, state)
    assert(buffer.get(0, 0).style.modifiers.has(Modifiers.Bold))
