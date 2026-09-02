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

  test("a resize invalidates the baseline, so the next frame is a full repaint"):
    // The bug this pins: a *widening* asks for no screen erase, and the frame was then diffed against the freshly
    // allocated, all-blank grid. A cell that is blank in the new frame differs from blankness in nothing, so it was
    // never written, and the glyph the old, narrower frame left in that column stayed on screen for the rest of the
    // run. There is no picture to diff against here, and the baseline now says so instead of offering a blank one.
    val baseline = FrameBaseline()
    flush(baseline, Rect(0, 0, 4, 2), "a")
    assert(baseline.prepareFor(Rect(0, 0, 6, 2), blank = false) == FrameSource.RepaintAll)
    assert(baseline.area.isEmpty)

  test("blanking an unchanged area resets in place rather than reallocating"):
    val baseline = FrameBaseline()
    flush(baseline, Rect(0, 0, 4, 2), "a")
    val grid     = gridOf(baseline.prepareFor(Rect(0, 0, 4, 2), blank = false))
    assert(grid.get(0, 0).symbol == "a")
    assert(baseline.prepareFor(Rect(0, 0, 4, 2), blank = true) == FrameSource.RepaintAll)
    assert(grid.get(0, 0).symbol == " ") // the same grid, emptied in place — no per-frame allocation
    assert(baseline.area.isEmpty)

  test("a scroll before the first frame shifts nothing, because the baseline describes nothing"):
    val baseline = FrameBaseline()
    baseline.shift(RowRange(0, 1), 1, ScrollDirection.Up)
    assert(baseline.area.isEmpty)

  test("a frame drawn after a widening repaints the columns the old frame had drawn into"):
    // The two halves of the fix, composed exactly as `JLine3Backend.draw` composes them.
    val encoder  = FrameEncoder(ColorDepth.TrueColor)
    val baseline = FrameBaseline()
    flush(baseline, Rect(0, 0, 4, 1), "|")
    val widened  = Buffer(Rect(0, 0, 6, 1))
    widened.set(0, 0, Cell("|", Style.Default))
    val body     = baseline.prepareFor(widened.area, blank = false) match
      case FrameSource.DiffAgainst(previous) => encoder.encode(previous, widened)
      case FrameSource.RepaintAll            => encoder.encodeAll(widened)
    // five blanks: the four columns the old frame filled with "|" minus the one this frame keeps, plus the two the
    // widening added. Without them the old glyphs stay on screen.
    assert(body.count(_ == ' ') == 5)

  /** The grid a [[FrameSource.DiffAgainst]] carries; a [[FrameSource.RepaintAll]] fails the test that asked for one. */
  private def gridOf(source: FrameSource): Buffer =
    source match
      case FrameSource.DiffAgainst(previous) => previous
      case FrameSource.RepaintAll            => fail("expected a baseline that still describes the screen")
