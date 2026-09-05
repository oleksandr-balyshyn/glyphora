package io.worxbend.tui.widgets

import com.sun.management.ThreadMXBean
import io.worxbend.tui.core.{Buffer, Rect}
import org.scalatest.funsuite.AnyFunSuite

final class ScratchSbAlloc2Spec extends AnyFunSuite:

  private val bean = java.lang.management.ManagementFactory.getThreadMXBean.asInstanceOf[ThreadMXBean]

  private def bytesFor(rows: Int, track: String, thumb: String, reps: Int): Long =
    val buf = Buffer(Rect(0, 0, 4, rows))
    val sb  = Scrollbar(contentLength = rows * 4, position = 3, trackSymbol = track, thumbSymbol = thumb)
    var i   = 0
    while i < 3000 do { sb.render(Rect(0, 0, 4, rows), buf); i += 1 }
    val id     = Thread.currentThread().threadId()
    val before = bean.getThreadAllocatedBytes(id)
    i = 0
    while i < reps do { sb.render(Rect(0, 0, 4, rows), buf); i += 1 }
    (bean.getThreadAllocatedBytes(id) - before) / reps

  private def nanosFor(rows: Int, track: String, thumb: String, reps: Int): Long =
    val buf = Buffer(Rect(0, 0, 4, rows))
    val sb  = Scrollbar(contentLength = rows * 4, position = 3, trackSymbol = track, thumbSymbol = thumb)
    var i   = 0
    while i < 20000 do { sb.render(Rect(0, 0, 4, rows), buf); i += 1 }
    val t0 = System.nanoTime()
    i = 0
    while i < reps do { sb.render(Rect(0, 0, 4, rows), buf); i += 1 }
    (System.nanoTime() - t0) / reps

  test("time per render, unicode vs ascii") {
    for rows <- Seq(50, 200) do
      val uni = nanosFor(rows, "\u2502", "\u2588", 200000)
      val asc = nanosFor(rows, "|", "#", 200000)
      info(s"rows=$rows unicode=${uni}ns ascii=${asc}ns")
  }

  test("allocation per render, unicode vs ascii, two sizes") {
    for rows <- Seq(50, 200) do
      val uni = bytesFor(rows, "│", "█", 20000)
      val asc = bytesFor(rows, "|", "#", 20000)
      info(s"rows=$rows unicode=${uni}B/render (${uni.toDouble / rows}B/cell) ascii=${asc}B/render (${asc.toDouble / rows}B/cell)")
  }
