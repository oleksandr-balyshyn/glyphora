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

  test("a lone percentage takes its share and leaves the rest free"):
    // absorbing any leftover whenever every constraint was proportional turned a half-width sidebar into a
    // full-width one, and left flex with no free space to position
    assert(widths(Seq(Constraint.Percentage(50)), 100) == Seq(50))
    assert(widths(Seq(Constraint.Percentage(0)), 100) == Seq(0))

  test("a lone ratio takes its share and leaves the rest free"):
    assert(widths(Seq(Constraint.Ratio(1, 4)), 100) == Seq(25))
    assert(widths(Seq(Constraint.Ratio(1, 0)), 100) == Seq(0)) // a zero denominator claims nothing

  test("percentages that leave real free space keep their relative sizes"):
    // 10% and 30% is a 1:3 split with 60% free; round-robin absorption used to hand out the free space evenly and
    // turn it into 40 and 60, a 2:3 split
    assert(widths(Seq(Constraint.Percentage(10), Constraint.Percentage(30)), 100) == Seq(10, 30))
    assert(widths(Seq(Constraint.Percentage(20), Constraint.Percentage(60)), 100) == Seq(20, 60))

  test("flex still has free space to position when the percentages do not fill the axis"):
    val layout = Layout(Direction.Horizontal, Seq(Constraint.Percentage(50)), flex = Flex.Center)
    assert(layout.split(Rect(0, 0, 100, 1)).map(_.x) == Seq(25))

  test("remainder cells go to the largest fractional shares, not to the earliest segments"):
    // Ratio(1,2) wants 5.0 cells and Ratio(1,4) wants 2.5 twice: the two quarters are the ones short-changed by
    // integer division, so they get the spare cells rather than the half that was already exact
    val thirds = Seq(Constraint.Ratio(1, 2), Constraint.Ratio(1, 4), Constraint.Ratio(1, 4))
    assert(widths(thirds, 10) == Seq(5, 3, 2))

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
