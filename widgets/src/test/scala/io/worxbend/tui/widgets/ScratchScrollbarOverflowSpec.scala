package io.worxbend.tui.widgets

import io.worxbend.tui.core.*
import org.scalatest.funsuite.AnyFunSuite

final class ScratchScrollbarOverflowSpec extends AnyFunSuite:
  test("thumb length with huge viewport"):
    val buf = Buffer(Rect(0, 0, 1, 50))
    Scrollbar(contentLength = 60000000, position = 0, viewportLength = Some(50000000)).render(Rect(0, 0, 1, 50), buf)
    val cells = (0 until 50).map(y => buf.get(0, y).symbol).mkString
    info(s"raw product = ${50 * 50000000}")
    info(s"column = $cells")
    info(s"thumb cells = ${cells.count(_ == '█')}")
