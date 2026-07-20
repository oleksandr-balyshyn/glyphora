package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** Flex distributes the space that fixed-size segments leave over. Cases use a 10-cell vertical axis with `Length`
  * segments (which never grow), so `free` is non-zero and the mode actually bites.
  */
final class FlexSpec extends AnyFunSuite:

  private val area = Rect(0, 0, 10, 10)

  private def offsets(flex: Flex, sizes: Int*): Seq[Int] =
    Layout(Direction.Vertical, sizes.map(Constraint.Length(_)), spacing = 0, flex = flex).split(area).map(_.y)

  test("Start packs at the origin, leftover trails (the default)"):
    assert(offsets(Flex.Start, 2, 2) == Seq(0, 2))

  test("End pushes the block to the far edge"):
    assert(offsets(Flex.End, 2, 2) == Seq(6, 8))

  test("Center splits the leftover evenly before and after"):
    assert(offsets(Flex.Center, 2, 2) == Seq(3, 5))

  test("SpaceBetween pins first and last to the edges"):
    assert(offsets(Flex.SpaceBetween, 2, 2) == Seq(0, 8))

  test("SpaceBetween with three segments spreads the gaps"):
    assert(offsets(Flex.SpaceBetween, 2, 2, 2) == Seq(0, 4, 8))

  test("SpaceEvenly gives every gap — including the edges — equal size"):
    assert(offsets(Flex.SpaceEvenly, 2, 2, 2) == Seq(1, 4, 7))

  test("SpaceAround gives each segment an equal surround (half-gap at the edges)"):
    assert(offsets(Flex.SpaceAround, 2, 2, 2) == Seq(1, 5, 8))

  test("sizes are preserved regardless of flex mode"):
    val heights = Layout(Direction.Vertical, Seq(Constraint.Length(2), Constraint.Length(2)), flex = Flex.Center)
      .split(area)
      .map(_.height)
    assert(heights == Seq(2, 2))

  test("a growing Fill leaves no leftover, so every mode collapses to Start"):
    val constraints = Seq(Constraint.Fill(1), Constraint.Length(2))
    val start       = Layout(Direction.Vertical, constraints, flex = Flex.Start).split(area).map(_.y)
    val center      = Layout(Direction.Vertical, constraints, flex = Flex.Center).split(area).map(_.y)
    assert(start == center)

  test("flex composes with base spacing"):
    // two Length(2) with spacing 1: free = 10 - 4 - 1 = 5, Center lead = 2
    val rects =
      Layout(Direction.Vertical, Seq(Constraint.Length(2), Constraint.Length(2)), spacing = 1, flex = Flex.Center)
        .split(area)
    assert(rects.map(_.y) == Seq(2, 5))
