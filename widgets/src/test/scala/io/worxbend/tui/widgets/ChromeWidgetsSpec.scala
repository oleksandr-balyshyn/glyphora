package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Line, Rect}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class ChromeWidgetsSpec extends AnyFunSuite:

  test("a button renders its bracketed label centered"):
    assert(trimmedLines(rendered(Button("OK"), 10, 1)) == Seq("  [ OK ]"))

  test("a long button label clips at the area"):
    assert(trimmedLines(rendered(Button("Continue"), 6, 1)) == Seq("[ Cont"))

  test("a horizontal rule spans the width with an inline label"):
    assert(trimmedLines(rendered(Rule(), 6, 1)) == Seq("──────"))
    assert(trimmedLines(rendered(Rule(label = Some("cfg")), 10, 1)) == Seq("── cfg ───"))

  test("a vertical rule spans the height"):
    assert(
      trimmedLines(rendered(Rule(orientation = io.worxbend.tui.core.Direction.Vertical), 1, 3)) == Seq("│", "│", "│")
    )

  test("big text renders the 3x5 glyphs"):
    val buffer = rendered(BigText("HI"), 8, 5)
    assert(
      trimmedLines(buffer) == Seq(
        "█ █ ███",
        "█ █  █",
        "███  █",
        "█ █  █",
        "█ █ ███",
      )
    )

  test("big text maps lowercase to uppercase and skips unknown glyphs"):
    val lower = rendered(BigText("hi"), 8, 5)
    val upper = rendered(BigText("HI"), 8, 5)
    assert(upper.diff(lower).isEmpty)
    assert(trimmedLines(rendered(BigText("~"), 4, 5)).forall(_.isEmpty))

  test("BigText.widthOf matches the rendered footprint"):
    assert(BigText.widthOf("HI") == 7)
    assert(BigText.widthOf("") == 0)

  test("the log follows the tail by default"):
    val state = LogState()
    (1 to 5).foreach(n => state.append(s"line $n"))
    val buffer = Buffer(Rect(0, 0, 10, 3))
    Log().render(buffer.area, buffer, state)
    assert(trimmedLines(buffer) == Seq("line 3", "line 4", "line 5"))

  test("scrolling up detaches follow; scrolling back to the bottom re-attaches"):
    val state = LogState()
    (1 to 5).foreach(n => state.append(s"line $n"))
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
    val state = LogState(maxLines = 3)
    (1 to 5).foreach(n => state.append(s"line $n"))
    assert(state.size == 3)
    val buffer = Buffer(Rect(0, 0, 10, 3))
    Log().render(buffer.area, buffer, state)
    assert(trimmedLines(buffer) == Seq("line 3", "line 4", "line 5"))

  test("styled lines keep their span styles in the log"):
    val state = LogState()
    state.append(Line.styled("err", io.worxbend.tui.core.Style.Default.bold))
    val buffer = Buffer(Rect(0, 0, 5, 1))
    Log().render(buffer.area, buffer, state)
    assert(buffer.get(0, 0).style.modifiers.has(io.worxbend.tui.core.Modifiers.Bold))
