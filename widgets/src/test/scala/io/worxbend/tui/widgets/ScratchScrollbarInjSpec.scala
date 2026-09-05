package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Style}
import org.scalatest.funsuite.AnyFunSuite

final class ScratchScrollbarInjSpec extends AnyFunSuite:
  private val esc = 27.toChar.toString

  private def show(s: String): String =
    s.map(c => if c.toInt < 32 then f"\\u${c.toInt}%04x" else c.toString).mkString

  test("scrollbar stores glyph parameters unmeasured") {
    val buf = Buffer(Rect(0, 0, 4, 3))
    Scrollbar(contentLength = 100, trackSymbol = esc + "[2J").render(buf.area, buf)
    println("SCRATCH cell(3,0) symbol = " + show(buf.get(3, 0).symbol))

    val buf2 = Buffer(Rect(0, 0, 6, 1))
    buf2.setString(0, 0, esc + "[2Jx", Style.Default)
    println("SCRATCH setString row = " + (0 until 6).map(x => show(buf2.get(x, 0).symbol)).mkString("|"))

    val buf3 = Buffer(Rect(0, 0, 4, 2))
    Scrollbar(contentLength = 100, trackSymbol = "ab").render(buf3.area, buf3)
    println("SCRATCH two-cluster cell = '" + buf3.get(3, 0).symbol + "'")
  }
