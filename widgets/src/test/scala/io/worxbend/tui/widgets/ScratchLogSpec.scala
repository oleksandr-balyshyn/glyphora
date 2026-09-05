package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect}
import org.scalatest.funsuite.AnyFunSuite

final class ScratchLogSpec extends AnyFunSuite:
  test("multiline append"):
    val s = LogState(maxLines = 2)
    s.append("alpha\nbeta")
    s.append("gamma\ndelta")
    s.append("eps")
    println(s"size=${s.size}")
    val b = Buffer(Rect(0, 0, 20, 3))
    Log().render(Rect(0, 0, 20, 3), b, s)
    for r <- 0 until 3 do println(s"row$r=[${(0 until 20).map(c => b.get(c, r).symbol).mkString}]")
