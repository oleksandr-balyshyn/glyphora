package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

class ScratchInsetSpec extends AnyFunSuite:
  test("inset overflow") {
    val r = Rect(0, 0, 10, 10)
    info(s"inset(Int.MaxValue,1) = ${r.inset(Int.MaxValue, 1)}")
    info(s"inset(1073741824) = ${r.inset(1073741824)}")
    assert(r.inset(Int.MaxValue, 1).width <= 10)
  }
