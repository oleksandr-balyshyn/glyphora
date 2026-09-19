package io.worxbend.tui.core

import org.scalacheck.Gen
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.concurrent.duration.*

/** [[Progress]] is CLAUDE.md's named single owner of "where is this animation at `elapsed`" for both the animated
  * widgets and the timed [[Effect]]s — a widget and an effect can never disagree about where a moment falls in a cycle
  * only if this file's contract actually holds. It is pure, holds no clock, and had zero direct tests before this file:
  * every degenerate case below was previously reachable only by accident, through whatever widget or effect happened to
  * exercise it.
  */
final class ProgressSpec extends AnyFunSuite with ScalaCheckPropertyChecks:

  // ---------------------------------------------------------------- normalized

  test("normalized reads a zero-length animation as finished rather than dividing by zero"):
    assert(Progress.normalized(500.millis, Duration.Zero) == 1.0)
    assert(Progress.normalized(Duration.Zero, Duration.Zero) == 1.0)

  test("normalized is unclamped past 1 once elapsed exceeds total"):
    assert(Progress.normalized(2000.millis, 1000.millis) == 2.0)

  test("normalized at exactly the total is exactly 1.0"):
    assert(Progress.normalized(1000.millis, 1000.millis) == 1.0)

  test("normalized of no time at all is exactly 0.0"):
    assert(Progress.normalized(Duration.Zero, 1000.millis) == 0.0)

  // ---------------------------------------------------------------- stepped: degenerate inputs

  test("stepped is 0 for a non-positive step count"):
    assert(Progress.stepped(500.millis, 100.millis, 0) == 0)
    assert(Progress.stepped(500.millis, 100.millis, -1) == 0)

  test("stepped is 0 for a non-positive period"):
    assert(Progress.stepped(500.millis, Duration.Zero, 10) == 0)
    assert(Progress.stepped(500.millis, (-100).millis, 10) == 0)

  test("a negative elapsed wraps backwards through the cycle rather than going negative"):
    // the Scaladoc's own example: -1ms into a 100ms/10-step cycle lands on the same step as 99ms, not step -1 and
    // not a double-width step around time zero
    assert(Progress.stepped((-1).millis, 100.millis, 10) == Progress.stepped(99.millis, 100.millis, 10))

  // ---------------------------------------------------------------- stepped: properties

  private val genDuration: Gen[FiniteDuration]       = Gen.chooseNum(-1000000L, 1000000L).map(_.nanos)
  private val genPositivePeriod: Gen[FiniteDuration] = Gen.chooseNum(1L, 3600000000000L).map(_.nanos) // up to an hour
  private val genSteps: Gen[Int]                     = Gen.chooseNum(1, 1000000)

  test("stepped always answers a position inside 0 until steps"):
    forAll(genDuration, genPositivePeriod, genSteps) { (elapsed, period, steps) =>
      val step = Progress.stepped(elapsed, period, steps)
      assert(step >= 0 && step < steps)
    }

  test("stepped is exactly cyclic: elapsed and elapsed + period name the same step"):
    forAll(genDuration, genPositivePeriod, genSteps) { (elapsed, period, steps) =>
      assert(Progress.stepped(elapsed, period, steps) == Progress.stepped(elapsed + period, period, steps))
    }

  test("stepped is exactly floor(normalized * steps) folded into one cycle — the sentence the Scaladoc makes"):
    // period up to 1 second and steps up to 1000: `normalized` computes in `Double`, so this keeps
    // `withinCycleNanos * steps` well inside the 2^53 range a `Double` represents integers exactly in — beyond
    // that, floating-point rounding could disagree with `stepped`'s exact integer arithmetic by one step for
    // reasons that have nothing to do with either implementation being wrong, which would make this a flaky test
    // rather than a meaningful one.
    forAll(Gen.chooseNum(1L, 1000000000L).map(_.nanos), Gen.chooseNum(1, 1000)) { (period, steps) =>
      forAll(Gen.chooseNum(0L, period.toNanos - 1)) { withinCycleNanos =>
        val elapsed  = withinCycleNanos.nanos
        val fromNorm = math.floor(Progress.normalized(elapsed, period) * steps).toInt
        assert(Progress.stepped(elapsed, period, steps) == fromNorm)
      }
    }

  // ---------------------------------------------------------------- steppedAtRate

  test("steppedAtRate is 0 for a non-positive step count"):
    assert(Progress.steppedAtRate(500.millis, 5.0, 0) == 0)

  test("steppedAtRate parks at 0 for a non-positive or NaN rate"):
    assert(Progress.steppedAtRate(500.millis, 0.0, 10) == 0)
    assert(Progress.steppedAtRate(500.millis, -1.0, 10) == 0)
    assert(Progress.steppedAtRate(500.millis, Double.NaN, 10) == 0)

  test("steppedAtRate always answers a position inside 0 until steps"):
    forAll(genDuration, Gen.chooseNum(0.01, 1000000.0), genSteps) { (elapsed, perSecond, steps) =>
      val step = Progress.steppedAtRate(elapsed, perSecond, steps)
      assert(step >= 0 && step < steps)
    }
