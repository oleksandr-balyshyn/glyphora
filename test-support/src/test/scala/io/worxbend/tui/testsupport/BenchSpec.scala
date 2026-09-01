package io.worxbend.tui.testsupport

import org.scalatest.funsuite.AnyFunSuite

/** The one thing worth asserting about a benchmark harness: that it actually runs what it claims to time. A harness
  * that quietly stopped calling the body would print excellent numbers forever.
  */
final class BenchSpec extends AnyFunSuite:

  test("the body runs once per iteration, for every warmup and every sample"):
    var calls = 0
    val _     = Bench.measure("counted", iterations = 5, warmups = 2, samples = 3)(() => calls += 1)
    assert(calls == 5 * (2 + 3))

  test("a benchmark reports a positive rate and a positive cost"):
    val result = Bench.measure("busy", iterations = 100, warmups = 1, samples = 3)(() => Bench.consume(1L))
    assert(result.name == "busy")
    assert(result.nanosPerOp > 0.0)
    assert(result.opsPerSecond > 0.0)

  test("zero iterations or zero samples is rejected rather than measured"):
    assertThrows[IllegalArgumentException](Bench.measure("none", iterations = 0)(() => ()))
    assertThrows[IllegalArgumentException](Bench.measure("none", iterations = 1, samples = 0)(() => ()))
