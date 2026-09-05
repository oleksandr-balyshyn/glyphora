package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Cell, Rect, Style}
import org.scalatest.funsuite.AnyFunSuite

final class ZzScratchShiftAllocSpec extends AnyFunSuite:

  private def allocated(): Long =
    java.lang.management.ManagementFactory.getThreadMXBean
      .asInstanceOf[com.sun.management.ThreadMXBean]
      .getThreadAllocatedBytes(Thread.currentThread().threadId)

  private def measure(w: Int, h: Int, iterations: Int): Long =
    val frame = Buffer(Rect(0, 0, w, h))
    var y     = 0
    while y < h do
      frame.set(0, y, Cell("x", Style.Default))
      y += 1
    val baseline = FrameBaseline()
    baseline.prepareFor(frame.area, blank = false)
    baseline.commit(frame)
    var i = 0
    while i < 200 do // warm up
      baseline.shift(RowRange(0, h - 1), 1, ScrollDirection.Up)
      i += 1
    val before = allocated()
    i = 0
    while i < iterations do
      baseline.shift(RowRange(0, h - 1), 1, ScrollDirection.Up)
      i += 1
    (allocated() - before) / iterations

  test("bytes allocated per shift, at two frame sizes") {
    val small = measure(80, 24, 500)
    val big   = measure(200, 50, 500)
    info(s"80x24 (1920 cells): $small bytes/shift")
    info(s"200x50 (10000 cells): $big bytes/shift")
    info(s"ratio: ${big.toDouble / math.max(1, small)}")
  }
