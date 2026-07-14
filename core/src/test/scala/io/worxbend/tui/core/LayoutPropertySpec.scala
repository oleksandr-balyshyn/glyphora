package io.worxbend.tui.core

import org.scalacheck.Gen
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

/** Property-based invariants for the constraint solver — the case space hand-picked examples cannot cover. */
final class LayoutPropertySpec extends AnyFunSuite with ScalaCheckPropertyChecks:

  private val genConstraint: Gen[Constraint] = Gen.oneOf(
    Gen.chooseNum(0, 30).map(Constraint.Length.apply),
    Gen.chooseNum(0, 100).map(Constraint.Percentage.apply),
    Gen.chooseNum(1, 4).flatMap(d => Gen.chooseNum(0, d).map(n => Constraint.Ratio(n, d))),
    Gen.chooseNum(0, 20).map(Constraint.Min.apply),
    Gen.chooseNum(0, 20).map(Constraint.Max.apply),
    Gen.chooseNum(1, 5).map(Constraint.Fill.apply),
  )

  private val genCase: Gen[(Seq[Constraint], Int, Int)] =
    for
      constraints <- Gen.nonEmptyListOf(genConstraint).map(_.take(8))
      total       <- Gen.chooseNum(0, 120)
      spacing     <- Gen.chooseNum(0, 3)
    yield (constraints, total, spacing)

  test("every segment stays inside the area and segments never overlap"):
    forAll(genCase) { case (constraints, total, spacing) =>
      val area  = Rect(3, 5, total, 4)
      val rects = Layout(Direction.Horizontal, constraints, spacing).split(area)
      assert(rects.size == constraints.size)
      rects.foreach { r =>
        assert(r.x >= area.x && r.right <= area.right, s"$r outside $area")
        assert(r.width >= 0)
      }
      rects.zip(rects.drop(1)).foreach { (a, b) => assert(b.x >= a.right, s"$b overlaps $a") }
    }

  test("solving is deterministic"):
    forAll(genCase) { case (constraints, total, spacing) =>
      val area = Rect(0, 0, total, 1)
      assert(
        Layout(Direction.Horizontal, constraints, spacing).split(area) ==
          Layout(Direction.Horizontal, constraints, spacing).split(area)
      )
    }

  test("pure fills use the whole area exactly"):
    forAll(Gen.chooseNum(1, 6), Gen.chooseNum(0, 100)) { (fills, total) =>
      val rects = Layout(Direction.Horizontal, Seq.fill(fills)(Constraint.Fill(1))).split(Rect(0, 0, total, 1))
      assert(rects.map(_.width).sum == total)
    }
