package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** Constraints whose numbers are far larger than any terminal.
  *
  * Nobody writes `Percentage(25000000)` on purpose, but a percentage computed from data can land there, and the
  * arithmetic behind it used to be plain `Int` multiplication: `available * pct` overflowed and came back negative
  * before the divide by 100 could bring it into range. `Percentage(25000000).sizeIn(100)` answered `-17949672`, which
  * breaks the promise the whole layout is built on — every solved size is a cell count inside the area.
  */
final class LayoutOverflowSpec extends AnyFunSuite with Matchers:

  private val hugeConstraints: Seq[Constraint] =
    Seq(
      Constraint.Percentage(25000000),
      Constraint.Percentage(Int.MaxValue),
      Constraint.Ratio(Int.MaxValue, 1),
      Constraint.Ratio(Int.MaxValue, 3),
      Constraint.Length(Int.MaxValue),
      Constraint.Max(Int.MaxValue),
      Constraint.Fill(Int.MaxValue),
    )

  test("a constraint far larger than the axis never answers a negative size") {
    hugeConstraints.foreach { constraint =>
      withClue(s"$constraint: ") { constraint.sizeIn(100) shouldBe 100 }
    }
  }

  test("Min far larger than the axis is still the floor it promises, not a wrapped-around size") {
    Constraint.Min(Int.MaxValue).sizeIn(100) shouldBe Int.MaxValue
  }

  test("every solved segment stays inside the area, whatever the constraints ask for") {
    val area = Rect(0, 0, 100, 10)
    for
      direction  <- Direction.values.toSeq
      flex       <- Flex.values.toSeq
      constraint <- hugeConstraints :+ Constraint.Min(Int.MaxValue)
    do
      val layout   = Layout(direction, Seq(constraint, Constraint.Fill(1)), flex = flex)
      val segments = layout.split(area)
      withClue(s"$direction $flex $constraint -> $segments: ") {
        segments.foreach { segment =>
          segment.width should be >= 0
          segment.height should be >= 0
          segment.x should be >= area.x
          segment.y should be >= area.y
          segment.right should be <= area.right
          segment.bottom should be <= area.bottom
        }
      }
  }

  test("an oversized percentage takes the whole axis instead of none of it") {
    val (left, right) =
      Layout.horizontal(Constraint.Percentage(25000000), Constraint.Fill(1)).split2(Rect(0, 0, 100, 1))
    left shouldBe Rect(0, 0, 100, 1)
    right.width shouldBe 0
  }

  test("two floors bigger than an Int between them do not wrap into free space") {
    val segments =
      Layout.horizontal(Constraint.Min(Int.MaxValue), Constraint.Min(Int.MaxValue)).split(Rect(0, 0, 80, 1))
    segments.map(_.width) shouldBe Seq(80, 0)
  }
