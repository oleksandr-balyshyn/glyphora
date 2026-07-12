package io.worxbend.tui.widgets

import io.worxbend.tui.core.Modifiers
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class GaugeSpec extends AnyFunSuite:

  test("the default label is the percentage, centered"):
    val buffer = rendered(Gauge(0.5), 10, 1)
    assert(trimmedLines(buffer) == Seq("   50%"))

  test("the filled region carries the filled style up to the ratio"):
    val buffer = rendered(Gauge(0.5), 10, 1)
    assert(buffer.get(0, 0).style.modifiers.has(Modifiers.Reverse))
    assert(buffer.get(4, 0).style.modifiers.has(Modifiers.Reverse))
    assert(!buffer.get(5, 0).style.modifiers.has(Modifiers.Reverse))

  test("ratio is clamped to the unit interval"):
    assert(trimmedLines(rendered(Gauge(2.5), 10, 1)) == Seq("   100%"))
    assert(trimmedLines(rendered(Gauge(-1.0), 10, 1)) == Seq("    0%"))

  test("a custom label replaces the percentage"):
    val buffer = rendered(Gauge(0.3, label = Some("3/10")), 10, 1)
    assert(trimmedLines(buffer) == Seq("   3/10"))

  test("Gauge.of computes the ratio from counts"):
    assert(Gauge.of(3, 10).ratio == 0.3)
    assert(Gauge.of(1, 0).ratio == 0.0)

  test("the label lands on the middle row of a taller gauge"):
    val buffer = rendered(Gauge(0.0), 6, 3)
    assert(trimmedLines(buffer) == Seq("", "  0%", ""))
