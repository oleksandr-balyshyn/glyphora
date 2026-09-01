package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Color, Style}
import io.worxbend.tui.testsupport.BufferAssertions.rendered

import org.scalatest.funsuite.AnyFunSuite

/** The DSL's two column charts hand the per-datum style override through to their widgets. */
final class BarStylingElementSpec extends AnyFunSuite:

  private val Red = Style.Default.withFg(Color.Red)

  test("sparkline.styleFor colours a single column"):
    val element = sparkline(Seq(1L, 9L, 1L)).max(9).styleFor((_, value) => Option.when(value > 4)(Red))
    val buffer  = rendered(element.widget, 3, 2)

    assert(buffer.get(1, 0).style.fg.contains(Color.Red), "the column over the limit was not restyled")
    assert(buffer.get(0, 1).style.fg.isEmpty, "a column under the limit was restyled")

  test("a sparkline with no override is left exactly as it was"):
    val plain   = rendered(sparkline(Seq(1L, 9L)).max(9).widget, 2, 2)
    val default = rendered(sparkline(Seq(1L, 9L)).max(9).styleFor((_, _) => None).widget, 2, 2)

    assert(io.worxbend.tui.testsupport.BufferAssertions.cellDifferences(default, plain).isEmpty)

  test("the barChart overload restyles the bar over the limit"):
    val element = barChart(Seq(("a", 2L), ("b", 8L)), 1, (_, value) => Option.when(value > 4)(Red), false)
    val buffer  = rendered(element.widget, 3, 3)

    assert(buffer.get(2, 0).style.fg.contains(Color.Red), "the bar over the limit was not restyled")
    assert(buffer.get(0, 1).style.fg.isEmpty, "the bar under the limit was restyled")
