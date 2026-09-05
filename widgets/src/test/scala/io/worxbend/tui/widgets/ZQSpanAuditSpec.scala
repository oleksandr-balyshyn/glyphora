package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Line, Rect}
import org.scalatest.funsuite.AnyFunSuite

final class ZQSpanAuditSpec extends AnyFunSuite:

  private def timeAt(n: Int): Long =
    val area   = Rect(0, 0, 40, 5)
    val buffer = Buffer(area)
    val table  = Table(rows = Seq(Seq(TableCell(Line.raw("total"), n))), widths = Seq.empty)
    val start  = System.nanoTime()
    table.render(area, buffer)
    (System.nanoTime() - start) / 1000000

  test("growth"):
    println(s"count(1e6)=${TableCell.columnCount(Seq(TableCell(Line.raw("x"), 1000000)))}")
    println(s"1e5 -> ${timeAt(100000)} ms")
    println(s"1e6 -> ${timeAt(1000000)} ms")
    println(s"1e7 -> ${timeAt(10000000)} ms")

  test("overflow blanks"):
    val area   = Rect(0, 0, 10, 2)
    val buffer = Buffer(area)
    val cells  = Seq(TableCell(Line.raw("a"), Int.MaxValue), TableCell(Line.raw("b"), 10))
    println(s"count=${TableCell.columnCount(cells)}")
    Table(rows = Seq(cells), widths = Seq.empty).render(area, buffer)
    println("row0=[" + (0 until 10).map(x => buffer.get(x, 0).symbol).mkString + "]")

  test("maxvalue"):
    val area   = Rect(0, 0, 10, 2)
    val buffer = Buffer(area)
    try
      Table(rows = Seq(Seq(TableCell(Line.raw("a"), Int.MaxValue))), widths = Seq.empty).render(area, buffer)
      println("no throw")
    catch case t: Throwable => println("threw: " + t.getClass.getName + ": " + t.getMessage)
