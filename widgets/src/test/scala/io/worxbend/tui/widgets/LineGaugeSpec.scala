package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class LineGaugeSpec extends AnyFunSuite:

  test("renders the label then filled and unfilled line segments"):
    val buffer = rendered(LineGauge(0.5), 12, 1)
    assert(trimmedLines(buffer) == Seq("50% ━━━━────"))

  test("zero and full ratios fill nothing and everything"):
    assert(trimmedLines(rendered(LineGauge(0.0), 8, 1)) == Seq("0% ─────"))
    assert(trimmedLines(rendered(LineGauge(1.0), 9, 1)) == Seq("100% ━━━━"))
