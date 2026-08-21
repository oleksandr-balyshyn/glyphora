package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class CheckboxSpec extends AnyFunSuite:

  test("a checkbox renders its box state and label"):
    assert(trimmedLines(rendered(Checkbox("ship it", checked = false), 12, 1)) == Seq("[ ] ship it"))
    assert(trimmedLines(rendered(Checkbox("ship it", checked = true), 12, 1)) == Seq("[x] ship it"))
