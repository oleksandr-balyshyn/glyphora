package io.worxbend.tui.widgets

import io.worxbend.tui.core.Modifiers
import io.worxbend.tui.testsupport.BufferAssertions.{line, rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class PaginatorSpec extends AnyFunSuite:

  test("a small total draws one dot per page with the current page filled"):
    assert(trimmedLines(rendered(Paginator(0, 3), 10, 1)) == Seq("● ○ ○"))
    assert(trimmedLines(rendered(Paginator(1, 3), 10, 1)) == Seq("○ ● ○"))
    assert(trimmedLines(rendered(Paginator(2, 3), 10, 1)) == Seq("○ ○ ●"))

  test("a page index outside the range is clamped to an end rather than losing the marker"):
    assert(trimmedLines(rendered(Paginator(99, 3), 10, 1)) == Seq("○ ○ ●"))
    assert(trimmedLines(rendered(Paginator(-5, 3), 10, 1)) == Seq("● ○ ○"))

  test("dots give way to `page/total` when there is not room for them"):
    // three dots need five columns (dot, gap, dot, gap, dot); at four the counter is drawn instead, and pages read
    // 1-based there while `current` is 0-based
    assert(trimmedLines(rendered(Paginator(0, 3), 4, 1)) == Seq("1/3"))
    assert(trimmedLines(rendered(Paginator(2, 3), 4, 1)) == Seq("3/3"))

  test("more than ten pages always uses the counter, however wide the area"):
    assert(trimmedLines(rendered(Paginator(4, 12), 40, 1)) == Seq("5/12"))

  test("the counter is clipped to the area rather than overrunning it"):
    assert(line(rendered(Paginator(4, 12), 2, 1), 0) == "5/")

  test("no pages draws nothing at all"):
    assert(line(rendered(Paginator(0, 0), 6, 1), 0) == "      ")
    assert(line(rendered(Paginator(0, -3), 6, 1), 0) == "      ")

  test("an empty area draws nothing"):
    assert(trimmedLines(rendered(Paginator(0, 3), 0, 1)) == Seq(""))

  test("only the current page's dot carries the active style"):
    val buffer = rendered(Paginator(1, 3), 10, 1)
    assert(buffer.get(2, 0).style.modifiers.hasAny(Modifiers.Bold))
    assert(!buffer.get(0, 0).style.modifiers.hasAny(Modifiers.Bold))
    assert(!buffer.get(4, 0).style.modifiers.hasAny(Modifiers.Bold))
