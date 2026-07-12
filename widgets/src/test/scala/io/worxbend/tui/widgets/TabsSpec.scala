package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Line, Modifiers}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class TabsSpec extends AnyFunSuite:

  private val tabs = Tabs(Seq("one", "two", "three").map(Line.raw))

  test("titles render on one row separated by the divider"):
    val buffer = rendered(tabs, 20, 1)
    assert(trimmedLines(buffer) == Seq("one │ two │ three"))

  test("the selected title carries the highlight style"):
    val buffer = rendered(tabs.copy(selected = 1), 20, 1)
    // 'two' starts after "one │ " (6 columns)
    assert(buffer.get(6, 0).style.modifiers.has(Modifiers.Reverse))
    assert(!buffer.get(0, 0).style.modifiers.has(Modifiers.Reverse))

  test("overflowing titles are clipped at the area edge"):
    val buffer = rendered(tabs, 8, 1)
    assert(trimmedLines(buffer) == Seq("one │ tw"))
