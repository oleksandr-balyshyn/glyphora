package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class ToggleSpec extends AnyFunSuite:

  test("a toggle renders on and off symbols"):
    assert(trimmedLines(rendered(Toggle("dark mode", on = true), 12, 1)) == Seq("◉ dark mode"))
    assert(trimmedLines(rendered(Toggle("dark mode", on = false), 12, 1)) == Seq("○ dark mode"))
