package io.worxbend.tui.widgets

import io.worxbend.tui.core.*
import org.scalatest.funsuite.AnyFunSuite

final class ScratchParaPerfSpec extends AnyFunSuite:
  private val bel = 7.toChar.toString

  private def timeMs(n: Int): Long =
    val text = (bel * n) + "hello world this is a long sentence"
    val line = Line(Vector(Span(text, Style.Default)), None, Style.Default)
    val t0   = System.nanoTime()
    val rows = Paragraph.wrapLine(line, 10)
    val t1   = System.nanoTime()
    info(s"n=$n rows=${rows.size} ms=${(t1 - t0) / 1000000}")
    (t1 - t0) / 1000000

  test("growth") {
    info(s"width of BEL = ${CharWidth.of(bel)}")
    timeMs(2000)
    timeMs(20000)
    timeMs(40000)
    timeMs(80000)
    val text = (bel * 60000) + "hello world this is a long sentence"
    val buf  = Buffer(Rect(0, 0, 10, 5))
    val p    = Paragraph(Text.raw(text), overflow = Overflow.Wrap)
    val t0   = System.nanoTime()
    p.render(Rect(0, 0, 10, 5), buf)
    val t1 = System.nanoTime()
    p.heightAt(10)
    val t2 = System.nanoTime()
    info(s"render60k=${(t1 - t0) / 1000000}ms heightAt=${(t2 - t1) / 1000000}ms")
  }
