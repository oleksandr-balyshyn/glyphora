package io.worxbend.tui.widgets

import io.worxbend.tui.core.*
import org.scalatest.funsuite.AnyFunSuite

final class ScratchListViewPerfSpec extends AnyFunSuite:

  private def timeRenders(n: Int, reps: Int): Long =
    val items: Seq[String | Line | Text] = Vector.tabulate(n)(i => s"row $i")
    val view                             = ListView(items)
    val state                            = ListState()
    state.selected = Some(n / 2)
    val area                             = Rect(0, 0, 40, 20)
    // warm up
    for _ <- 1 to 3 do view.render(area, Buffer(area), state)
    val t0 = System.nanoTime()
    for _ <- 1 to reps do view.render(area, Buffer(area), state)
    (System.nanoTime() - t0) / reps

  test("render cost grows with item count") {
    val small = timeRenders(10_000, 50)
    val big   = timeRenders(1_000_000, 50)
    info(s"10k items: ${small / 1000} us/frame")
    info(s"1M items:  ${big / 1000} us/frame")
    info(s"ratio: ${big.toDouble / small}")
    val items: Seq[String | Line | Text] = Vector.tabulate(1_000_000)(i => s"row $i")
    val rt                               = Runtime.getRuntime
    System.gc()
    val before = rt.totalMemory() - rt.freeMemory()
    val h      = ListView(items).heightAt(40)
    val after  = rt.totalMemory() - rt.freeMemory()
    info(s"heightAt=$h allocated ~${(after - before) / 1024 / 1024} MiB")
    assert(h.contains(1_000_000))
  }
