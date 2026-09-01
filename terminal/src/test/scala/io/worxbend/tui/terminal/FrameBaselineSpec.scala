package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Cell, Rect, Style}

import org.scalatest.funsuite.AnyFunSuite

/** The diff baseline's own rules, which used to be two `var`s inside `JLine3Backend` and reachable only by driving a
  * whole JLine terminal over a pair of streams.
  */
final class FrameBaselineSpec extends AnyFunSuite:

  private def filled(area: Rect, symbol: String): Buffer =
    val buffer = Buffer(area)
    var y      = area.y
    while y < area.bottom do
      var x = area.x
      while x < area.right do
        buffer.set(x, y, Cell(symbol, Style.Default))
        x += 1
      y += 1
    buffer

  /** One whole `draw`: prepare the grid for the frame's area, then record the frame as flushed. */
  private def flush(baseline: FrameBaseline, area: Rect, symbol: String): Unit =
    val frame = filled(area, symbol)
    val _     = baseline.prepareFor(area, blank = false)
    baseline.commit(frame)

  test("nothing has been flushed yet, so there is no area to compare a resize against"):
    assert(FrameBaseline().area.isEmpty)

  test("a flushed frame is the area the next diff is measured against"):
    val baseline = FrameBaseline()
    flush(baseline, Rect(0, 0, 4, 2), "a")
    assert(baseline.area.contains(Rect(0, 0, 4, 2)))

  test("a resize reallocates the grid and invalidates the baseline"):
    val baseline = FrameBaseline()
    flush(baseline, Rect(0, 0, 4, 2), "a")
    val grid     = baseline.prepareFor(Rect(0, 0, 6, 2), blank = false)
    assert(grid.area == Rect(0, 0, 6, 2))
    assert(baseline.area.isEmpty)

  test("blanking an unchanged area resets in place rather than reallocating"):
    val baseline = FrameBaseline()
    flush(baseline, Rect(0, 0, 4, 2), "a")
    val before   = baseline.prepareFor(Rect(0, 0, 4, 2), blank = false)
    assert(before.get(0, 0).symbol == "a")
    val after    = baseline.prepareFor(Rect(0, 0, 4, 2), blank = true)
    assert(after eq before) // the same grid, recycled — no per-frame allocation
    assert(after.get(0, 0).symbol == " ")
    assert(baseline.area.isEmpty)

  test("a scroll before the first frame shifts nothing, because the baseline describes nothing"):
    val baseline = FrameBaseline()
    baseline.shift(RowRange(0, 1), 1, ScrollDirection.Up)
    assert(baseline.area.isEmpty)
