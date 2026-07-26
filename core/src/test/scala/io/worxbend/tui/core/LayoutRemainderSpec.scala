package io.worxbend.tui.core

import org.scalacheck.Gen
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

/** Integer division leaves proportional constraints short of the container; that remainder used to surface as a stray
  * unpainted column at the right edge.
  */
final class LayoutRemainderSpec extends AnyFunSuite, ScalaCheckPropertyChecks:

  private def widths(constraints: Seq[Constraint], total: Int): Seq[Int] =
    Layout(Direction.Horizontal, constraints).split(Rect(0, 0, total, 1)).map(_.width)

  test("three equal percentages fill the width exactly"):
    assert(widths(Seq.fill(3)(Constraint.Percentage(33)), 100) == Seq(34, 33, 33))
    assert(widths(Seq.fill(3)(Constraint.Percentage(33)), 100).sum == 100)

  test("thirds expressed as ratios fill the width exactly"):
    assert(widths(Seq.fill(3)(Constraint.Ratio(1, 3)), 100).sum == 100)

  test("proportional layouts always fill the container, at every width"):
    val proportional = Gen.oneOf(
      Seq.fill(3)(Constraint.Percentage(33)),
      Seq.fill(2)(Constraint.Percentage(50)),
      Seq(Constraint.Percentage(25), Constraint.Percentage(25), Constraint.Percentage(50)),
      Seq.fill(3)(Constraint.Ratio(1, 3)),
      Seq(Constraint.Ratio(1, 6), Constraint.Ratio(5, 6)),
    )
    forAll(proportional, Gen.choose(0, 300)) { (constraints, total) =>
      assert(widths(constraints, total).sum == total)
    }

  test("a fixed length still leaves the rest of the axis free"):
    // the remainder rule must not turn Length into a greedy Fill
    assert(widths(Seq(Constraint.Length(10)), 100) == Seq(10))
    assert(widths(Seq(Constraint.Length(10), Constraint.Length(20)), 100) == Seq(10, 20))

  test("a Fill absorbs the leftover as before"):
    assert(widths(Seq(Constraint.Length(10), Constraint.Fill(1)), 100) == Seq(10, 90))

  test("mixing a percentage with a fill leaves the fill in charge of the remainder"):
    assert(widths(Seq(Constraint.Percentage(33), Constraint.Fill(1)), 100).sum == 100)

  test("segments never overlap or leave the container, at any width"):
    val anyConstraints = Gen.oneOf(
      Seq.fill(3)(Constraint.Percentage(33)),
      Seq(Constraint.Length(10), Constraint.Fill(1)),
      Seq(Constraint.Min(5), Constraint.Max(3), Constraint.Fill(2)),
      Seq.fill(3)(Constraint.Ratio(1, 3)),
    )
    forAll(anyConstraints, Gen.choose(0, 300)) { (constraints, total) =>
      val area  = Rect(0, 0, total, 1)
      val parts = Layout(Direction.Horizontal, constraints).split(area)
      parts.zip(parts.drop(1)).foreach((left, right) => assert(left.right <= right.x))
      parts.foreach(part => assert(part.x >= area.x && part.right <= area.right))
    }
