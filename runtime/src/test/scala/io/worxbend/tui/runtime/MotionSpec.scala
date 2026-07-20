package io.worxbend.tui.runtime

import org.scalatest.funsuite.AnyFunSuite

final class MotionSpec extends AnyFunSuite:

  test("every easing starts at 0 and ends at 1"):
    Easing.values.foreach { easing =>
      assert(math.abs(easing(0.0)) < 1e-6, s"$easing at 0")
      assert(math.abs(easing(1.0) - 1.0) < 1e-6, s"$easing at 1")
    }

  test("easings clamp their input to [0, 1]"):
    Easing.values.foreach { easing =>
      assert(easing(-5.0) == easing(0.0))
      assert(easing(5.0) == easing(1.0))
    }

  test("ease-out curves are ahead of linear in the first half, ease-in behind"):
    assert(Easing.CubicOut(0.25) > Easing.Linear(0.25))
    assert(Easing.CubicIn(0.25) < Easing.Linear(0.25))
    assert(Easing.QuintOut(0.1) > Easing.QuadOut(0.1)) // steeper family pulls further ahead early

  test("overshoot easings leave [0,1] mid-flight but still land on the endpoints"):
    val backPeak = (1 to 99).map(i => Easing.BackOut(i / 100.0)).max
    assert(backPeak > 1.0) // BackOut overshoots past the target before settling
    assert(math.abs(Easing.BackOut(1.0) - 1.0) < 1e-6)

  test("a spring converges on its target and reports settled"):
    val spring     = Spring(frequency = 8.0, damping = 1.0)
    var (pos, vel) = (0.0, 0.0)
    var steps      = 0
    while !spring.settled(pos, vel, 1.0) && steps < 10000 do
      val next = spring.step(pos, vel, 1.0)
      pos = next._1
      vel = next._2
      steps += 1
    assert(steps < 10000, "spring did not settle")
    assert(math.abs(pos - 1.0) < 1e-2)

  test("an underdamped spring overshoots its target at least once"):
    val spring     = Spring(frequency = 12.0, damping = 0.2)
    var (pos, vel) = (0.0, 0.0)
    var maxPos     = 0.0
    for _ <- 0 until 200 do
      val next = spring.step(pos, vel, 1.0)
      pos = next._1
      vel = next._2
      maxPos = math.max(maxPos, pos)
    assert(maxPos > 1.0, "underdamped spring should overshoot")
