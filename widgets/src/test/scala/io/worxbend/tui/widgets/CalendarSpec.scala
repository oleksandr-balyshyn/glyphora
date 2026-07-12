package io.worxbend.tui.widgets

import io.worxbend.tui.core.Modifiers
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class CalendarSpec extends AnyFunSuite:

  test("renders the title, weekday header, and day grid for July 2026"):
    val lines = trimmedLines(rendered(Calendar(2026, 7), 20, 8))
    assert(lines(0).contains("July 2026"))
    assert(lines(1) == "Mo Tu We Th Fr Sa Su")
    // 2026-07-01 is a Wednesday: first row starts at the We column
    assert(lines(2) == "       1  2  3  4  5")
    assert(lines(3) == " 6  7  8  9 10 11 12")
    assert(lines(6).startsWith("27 28 29 30 31"))

  test("a month starting on Monday fills the first row"):
    val lines = trimmedLines(rendered(Calendar(2026, 6), 20, 8))
    assert(lines(2) == " 1  2  3  4  5  6  7")

  test("the selected day is highlighted"):
    val buffer = rendered(Calendar(2026, 7, selected = Some(1)), 20, 8)
    // day 1 sits in the We column (x = 6..7) on the first grid row (y = 2)
    assert(buffer.get(7, 2).style.modifiers.has(Modifiers.Reverse))
    assert(!buffer.get(10, 2).style.modifiers.has(Modifiers.Reverse))
