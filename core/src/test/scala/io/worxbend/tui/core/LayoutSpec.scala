package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class LayoutSpec extends AnyFunSuite:

  private val area = Rect(0, 0, 10, 10)

  test("Length segments take exactly their cells"):
    val rects = Layout(Direction.Vertical, Seq(Constraint.Length(3), Constraint.Length(4))).split(area)
    assert(rects == Seq(Rect(0, 0, 10, 3), Rect(0, 3, 10, 4)))

  test("Percentage segments take their share of the axis"):
    val rects = Layout(Direction.Vertical, Seq(Constraint.Percentage(50), Constraint.Percentage(50))).split(area)
    assert(rects.map(_.height) == Seq(5, 5))

  test("Ratio segments divide the axis by the ratio"):
    val rects = Layout(Direction.Vertical, Seq(Constraint.Ratio(1, 3), Constraint.Ratio(2, 3))).split(Rect(0, 0, 10, 9))
    assert(rects.map(_.height) == Seq(3, 6))

  test("Min takes its floor plus all unclaimed space"):
    val rects = Layout(Direction.Vertical, Seq(Constraint.Min(3), Constraint.Length(2))).split(area)
    assert(rects.map(_.height) == Seq(8, 2))

  test("Max takes leftover space only up to its cap"):
    val rects = Layout(Direction.Vertical, Seq(Constraint.Max(4), Constraint.Length(2))).split(area)
    assert(rects.map(_.height) == Seq(4, 2))

  test("Fill divides leftover space by weight"):
    val rects = Layout(Direction.Vertical, Seq(Constraint.Fill(1), Constraint.Fill(3))).split(Rect(0, 0, 10, 8))
    assert(rects.map(_.height) == Seq(2, 6))

  test("equal fills that do not divide evenly give the extra cell to the earlier segment"):
    val rects = Layout(Direction.Vertical, Seq(Constraint.Fill(1), Constraint.Fill(1), Constraint.Fill(1))).split(area)
    assert(rects.map(_.height) == Seq(4, 3, 3))

  test("mixed constraints: fixed demands first, leftover to the fills"):
    val constraints = Seq(Constraint.Length(3), Constraint.Percentage(25), Constraint.Fill(1), Constraint.Fill(1))
    val rects       = Layout(Direction.Vertical, constraints).split(Rect(0, 0, 10, 20))
    assert(rects.map(_.height) == Seq(3, 5, 6, 6))

  test("spacing separates segments without joining their sizes"):
    val rects = Layout(Direction.Vertical, Seq(Constraint.Length(3), Constraint.Length(3)), spacing = 1).split(area)
    assert(rects == Seq(Rect(0, 0, 10, 3), Rect(0, 4, 10, 3)))

  test("over-constrained demands truncate trailing segments instead of failing"):
    val rects = Layout(Direction.Vertical, Seq(Constraint.Length(4), Constraint.Length(4))).split(Rect(0, 0, 10, 5))
    assert(rects.map(_.height) == Seq(4, 1))

  test("horizontal splits move along x and keep the full height"):
    val rects = Layout(Direction.Horizontal, Seq(Constraint.Length(4), Constraint.Fill(1))).split(area)
    assert(rects == Seq(Rect(0, 0, 4, 10), Rect(4, 0, 6, 10)))

  test("an empty constraint list yields no rects"):
    assert(Layout(Direction.Vertical, Seq.empty).split(area).isEmpty)

  test("percentage shorthand truncates fractions — 0.333 becomes 33%"):
    val layout = Layout(Direction.Vertical)(0.333)
    assert(layout.constraints == Seq(Constraint.Percentage(33)))

  test("the union-typed shorthand builds Length from Int, Percentage from Double, and passes Constraint through"):
    val layout = Layout(Direction.Vertical)(3, 0.5, Constraint.fill)
    assert(layout.constraints == Seq(Constraint.Length(3), Constraint.Percentage(50), Constraint.Fill(1)))

  test("offset areas position segments in absolute coordinates"):
    val rects = Layout(Direction.Vertical, Seq(Constraint.Length(2), Constraint.Fill(1))).split(Rect(5, 5, 4, 6))
    assert(rects == Seq(Rect(5, 5, 4, 2), Rect(5, 7, 4, 4)))

  // ---- Flex distribution of unclaimed space ----

  test("Flex.Start leaves the slack at the trailing edge (the default packing)"):
    val rects = Layout(Direction.Horizontal, Seq(Constraint.Length(2), Constraint.Length(2)), flex = Flex.Start)
      .split(Rect(0, 0, 10, 1))
    assert(rects.map(r => (r.x, r.width)) == Seq((0, 2), (2, 2)))

  test("Flex.End pushes segments to the trailing edge"):
    val rects = Layout(Direction.Horizontal, Seq(Constraint.Length(2), Constraint.Length(2)), flex = Flex.End)
      .split(Rect(0, 0, 10, 1))
    assert(rects.map(r => (r.x, r.width)) == Seq((6, 2), (8, 2)))

  test("Flex.Center splits the slack evenly before and after the group"):
    val rects = Layout(Direction.Horizontal, Seq(Constraint.Length(2), Constraint.Length(2)), flex = Flex.Center)
      .split(Rect(0, 0, 10, 1))
    assert(rects.map(r => (r.x, r.width)) == Seq((3, 2), (5, 2)))

  test("Flex.SpaceBetween puts the slack only in the gaps, none at the edges"):
    val rects = Layout(
      Direction.Horizontal,
      Seq(Constraint.Length(2), Constraint.Length(2), Constraint.Length(2)),
      flex = Flex.SpaceBetween,
    ).split(Rect(0, 0, 12, 1))
    // 6 slack over 2 gaps → 3 each: [0..2) gap [5..7) gap [10..12)
    assert(rects.map(r => (r.x, r.width)) == Seq((0, 2), (5, 2), (10, 2)))

  test("Flex.SpaceEvenly places equal gaps including both edges"):
    val rects = Layout(Direction.Horizontal, Seq(Constraint.Length(2), Constraint.Length(2)), flex = Flex.SpaceEvenly)
      .split(Rect(0, 0, 12, 1))
    // 8 slack over 3 gaps → ~2.67; earliest gaps get the remainder
    assert(rects.map(_.width) == Seq(2, 2))
    assert(rects.head.x >= 2 && rects(1).x > rects.head.right)

  test("Flex.SpaceAround pads each segment with equal surrounding space"):
    val rects = Layout(Direction.Horizontal, Seq(Constraint.Length(2), Constraint.Length(2)), flex = Flex.SpaceAround)
      .split(Rect(0, 0, 12, 1))
    // 8 slack, 2 segments → edge pad 2, middle gap 4
    assert(rects.map(r => (r.x, r.width)) == Seq((2, 2), (8, 2)))

  test("Flex.Legacy hands the slack to the last segment (opt-in; the default is Start)"):
    val rects   = Layout(Direction.Horizontal, Seq(Constraint.Length(2), Constraint.Length(2)), flex = Flex.Legacy)
      .split(Rect(0, 0, 10, 1))
    assert(rects.map(r => (r.x, r.width)) == Seq((0, 2), (2, 8)))
    val default = Layout(Direction.Horizontal, Seq(Constraint.Length(2), Constraint.Length(2)))
      .split(Rect(0, 0, 10, 1))
    assert(default.map(r => (r.x, r.width)) == Seq((0, 2), (2, 2))) // Start: sizes fixed, slack unused

  test("Fill absorbs the slack, so Flex has nothing to distribute"):
    val rects = Layout(Direction.Horizontal, Seq(Constraint.Length(2), Constraint.Fill(1)), flex = Flex.SpaceBetween)
      .split(Rect(0, 0, 10, 1))
    assert(rects.map(r => (r.x, r.width)) == Seq((0, 2), (2, 8)))

  // ---- Margin ----

  test("margin insets the area on both axes before splitting"):
    val rects = Layout(Direction.Vertical, Seq(Constraint.Fill(1)), margin = Margin(2, 1)).split(Rect(0, 0, 10, 10))
    assert(rects == Seq(Rect(2, 1, 6, 8)))

  test("uniform margin trims every side equally"):
    val rects =
      Layout(Direction.Horizontal, Seq(Constraint.Fill(1)), margin = Margin.uniform(2)).split(Rect(0, 0, 10, 10))
    assert(rects == Seq(Rect(2, 2, 6, 6)))
