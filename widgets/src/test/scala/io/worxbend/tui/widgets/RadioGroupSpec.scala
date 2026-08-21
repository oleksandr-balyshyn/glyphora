package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect}
import io.worxbend.tui.testsupport.BufferAssertions.trimmedLines

import org.scalatest.funsuite.AnyFunSuite

final class RadioGroupSpec extends AnyFunSuite:

  test("a radio group renders nothing into a zero-width area"):
    val buffer = Buffer(Rect(0, 0, 12, 2))
    RadioGroup(Seq("one", "two"), selected = 0).render(Rect(0, 0, 0, 2), buffer)
    assert(trimmedLines(buffer) == Seq("", ""))
