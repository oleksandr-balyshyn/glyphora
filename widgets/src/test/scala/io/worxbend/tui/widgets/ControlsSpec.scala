package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Widget}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class ControlsSpec extends AnyFunSuite:

  /** Renders `widget` into the left half of a 20x`height` buffer and returns the right half, which must stay blank. */
  private def spillToTheRight(widget: Widget, width: Int, height: Int): Seq[String] =
    val buffer = Buffer(Rect(0, 0, 20, height))
    widget.render(Rect(0, 0, width, height), buffer)
    trimmedLines(buffer).map(_.drop(width))

  test("a checkbox renders its box state and label"):
    assert(trimmedLines(rendered(Checkbox("ship it", checked = false), 12, 1)) == Seq("[ ] ship it"))
    assert(trimmedLines(rendered(Checkbox("ship it", checked = true), 12, 1)) == Seq("[x] ship it"))

  test("a toggle renders on and off symbols"):
    assert(trimmedLines(rendered(Toggle("dark mode", on = true), 12, 1)) == Seq("◉ dark mode"))
    assert(trimmedLines(rendered(Toggle("dark mode", on = false), 12, 1)) == Seq("○ dark mode"))

  test("a select shows the current option between cycle arrows"):
    assert(trimmedLines(rendered(Select(Seq("red", "green"), selected = 1), 12, 1)) == Seq("◀ green ▶"))

  test("a select clamps an out-of-range index"):
    assert(trimmedLines(rendered(Select(Seq("red", "green"), selected = 9), 12, 1)) == Seq("◀ green ▶"))

  test("single-line controls truncate at their own right edge, not the buffer's"):
    assert(spillToTheRight(Checkbox("ship it now please", checked = true), 6, 1) == Seq(""))
    assert(spillToTheRight(Toggle("dark mode please", on = true), 6, 1) == Seq(""))
    assert(spillToTheRight(Select(Seq("a-long-option"), selected = 0), 6, 1) == Seq(""))
    assert(spillToTheRight(Spinner(0, "loading the world"), 6, 1) == Seq(""))
    assert(spillToTheRight(Link("a-long-label", "https://example.com"), 6, 1) == Seq(""))
    assert(spillToTheRight(RadioGroup(Seq("first option", "second option"), selected = 0), 6, 2) == Seq("", ""))

  test("a radio group renders nothing into a zero-width area"):
    val buffer = Buffer(Rect(0, 0, 12, 2))
    RadioGroup(Seq("one", "two"), selected = 0).render(Rect(0, 0, 0, 2), buffer)
    assert(trimmedLines(buffer) == Seq("", ""))
