package io.worxbend.tui.widgets

import io.worxbend.tui.core.*
import org.scalatest.funsuite.AnyFunSuite

final class ScratchListPerfSpec extends AnyFunSuite:

  private def timeRenders(n: Int, frames: Int): (Double, Long) =
    val items  = Seq.fill(n)("row"): Seq[String | Line | Text]
    val lv     = ListView(items)
    val state  = ListState()
    val area   = Rect(0, 0, 20, 10)
    val buffer = Buffer(Rect(0, 0, 20, 10))
    // warm up
    for _ <- 1 to 50 do lv.render(area, buffer, state)
    val rt     = Runtime.getRuntime
    System.gc()
    val before = rt.totalMemory() - rt.freeMemory()
    val t0     = System.nanoTime()
    for _ <- 1 to frames do lv.render(area, buffer, state)
    val t1     = System.nanoTime()
    val after  = rt.totalMemory() - rt.freeMemory()
    ((t1 - t0) / frames.toDouble / 1000.0, (after - before) / frames.toLong)

  test("render cost scales with item count, not viewport") {
    for n <- Seq(1000, 10000, 100000) do
      val (us, bytes) = timeRenders(n, 200)
      info(f"items=$n%7d  per-frame: $us%9.1f us   heap delta/frame: $bytes bytes")
    val h0 = System.nanoTime()
    val big = ListView(Seq.fill(100000)("row"): Seq[String | Line | Text])
    val _ = (1 to 200).foreach(_ => big.heightAt(20))
    info(f"heightAt(100k) per call: ${(System.nanoTime() - h0) / 200 / 1000.0}%.1f us")
  }
