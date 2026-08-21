package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class BigTextSpec extends AnyFunSuite:

  test("big text renders the 3x5 glyphs"):
    assert(
      trimmedLines(rendered(BigText("HI"), 8, 5)) == Seq(
        "█ █ ███",
        "█ █  █",
        "███  █",
        "█ █  █",
        "█ █ ███",
      )
    )

  test("big text maps lowercase to uppercase and skips unknown glyphs"):
    val lower = rendered(BigText("hi"), 8, 5)
    val upper = rendered(BigText("HI"), 8, 5)
    assert(upper.diff(lower).isEmpty)
    assert(trimmedLines(rendered(BigText("~"), 4, 5)).forall(_.isEmpty))

  test("BigText.widthOf matches the rendered footprint"):
    assert(BigText.widthOf("HI") == 7)
    assert(BigText.widthOf("") == 0)
