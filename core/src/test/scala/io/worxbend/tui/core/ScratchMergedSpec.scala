package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class ScratchMergedSpec extends AnyFunSuite:
  test("merged growth"):
    println(s"maxHeap=${Runtime.getRuntime.maxMemory / (1024 * 1024)}MB")
    for offset <- Seq(1000, 4000, 12000) do
      val a     = Buffer(Rect(0, 0, 1, 1))
      val b     = Buffer(Rect(offset, offset, 1, 1))
      val start = System.nanoTime()
      val m     = a.merged(b)
      val ms    = (System.nanoTime() - start) / 1000000.0
      println(f"offset=$offset cells=${m.area.cellCount} time=$ms%.1fms")
    val big = Buffer(Rect(30000, 30000, 1, 1))
    val r =
      try
        val m = Buffer(Rect(0, 0, 1, 1)).merged(big)
        s"ok cells=${m.area.cellCount}"
      catch case t: Throwable => s"${t.getClass.getName}: ${t.getMessage}"
    println(s"30000 -> $r")
