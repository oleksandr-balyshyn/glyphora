package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class ControlsSpec extends AnyFunSuite:

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
