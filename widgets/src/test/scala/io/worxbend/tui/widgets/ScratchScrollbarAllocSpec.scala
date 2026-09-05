package io.worxbend.tui.widgets

import com.sun.management.ThreadMXBean
import io.worxbend.tui.core.{Buffer, Rect}
import org.scalatest.funsuite.AnyFunSuite

final class ScratchScrollbarAllocSpec extends AnyFunSuite:

  private val bean = java.lang.management.ManagementFactory.getThreadMXBean.asInstanceOf[ThreadMXBean]

  private def bytesFor(rows: Int, track: String, thumb: String, reps: Int): Long =
    val buf = Buffer(Rect(0, 0, 4, rows))
    val sb  = Scrollbar(contentLength = rows * 4, position = 3, trackSymbol = track, thumbSymbol = thumb)
    var i   = 0
    while i < 2000 do { sb.render(Rect(0, 0, 4, rows), buf); i += 1 }
    val id     = Thread.currentThread().threadId()
    val before = bean.getThreadAllocatedBytes(id)
    i = 0
    while i < reps do { sb.render(Rect(0, 0, 4, rows), buf); i += 1 }
    (bean.getThreadAllocatedBytes(id) - before) / reps

  test("allocation per render, unicode vs ascii, two sizes"):
    for rows <- Seq(50, 200) do
      val uni = bytesFor(rows, "│", "█", 20000)
      val asc = bytesFor(rows, "|", "#", 20000)
      info(s"rows=$rows unicode=${uni}B/render ascii=${asc}B/render  perCell=${uni.toDouble / rows}")
