package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Cell, Rect, Style}
import org.scalatest.funsuite.AnyFunSuite

final class ScratchEncoderInjSpec extends AnyFunSuite:
  private val esc = 27.toChar.toString

  test("encoder emits a cell symbol verbatim") {
    val buf = Buffer(Rect(0, 0, 3, 1))
    buf.set(0, 0, Cell(esc + "[2J", Style.Default))
    val out = FrameEncoder(ColorDepth.TrueColor).encodeAll(buf)
    println("SCRATCH encoded = " + out.map(c => if c.toInt < 32 then f"\\u${c.toInt}%04x" else c.toString).mkString)
    println("SCRATCH contains raw clear-screen = " + out.contains(esc + "[2J"))
  }
