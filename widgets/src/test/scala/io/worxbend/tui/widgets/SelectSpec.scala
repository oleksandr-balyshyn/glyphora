package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class SelectSpec extends AnyFunSuite:

  test("a select shows the current option between cycle arrows"):
    assert(trimmedLines(rendered(Select(Seq("red", "green"), selected = 1), 12, 1)) == Seq("◀ green ▶"))

  test("a select clamps an out-of-range index"):
    assert(trimmedLines(rendered(Select(Seq("red", "green"), selected = 9), 12, 1)) == Seq("◀ green ▶"))
