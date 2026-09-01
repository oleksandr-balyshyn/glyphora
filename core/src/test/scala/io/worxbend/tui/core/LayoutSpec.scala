package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class LayoutSpec extends AnyFunSuite:

  private val area = Rect(0, 0, 10, 10)

  test("Length segments take exactly their cells"):
    val rects = Layout(Direction.Vertical, Seq(Constraint.Length(3), Constraint.Length(4))).split(area)
    assert(rects == Seq(Rect(0, 0, 10, 3), Rect(0, 3, 10, 4)))

  test("a negative spacing is treated as zero rather than overlapping the segments"):
    // `split` clamped the product `spacing * (n - 1)` but the placement steps used the raw value, so a negative
    // spacing pulled each segment back over the one before it: two 4-cell segments at x=0 and x=2
    val rects = Layout(Direction.Horizontal, Seq(Constraint.Length(4), Constraint.Length(4)), spacing = -2)
      .split(Rect(0, 0, 10, 1))
    assert(rects == Seq(Rect(0, 0, 4, 1), Rect(4, 0, 4, 1)))

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

  test("split2 destructures a two-way split into a checked tuple"):
    val (top, bottom) = Layout(Direction.Vertical)(3, Constraint.fill).split2(area)
    assert(top == Rect(0, 0, 10, 3))
    assert(bottom == Rect(0, 3, 10, 7))

  test("the tuple helpers pad a short result with empty rects instead of throwing"):
    // asking for more segments than the layout declares is a coding mistake, but crashing a terminal in raw mode
    // over it is worse than rendering one pane short
    val (first, second, third) = Layout(Direction.Vertical)(Constraint.fill).split3(area)
    assert(first == Rect(0, 0, 10, 10))
    assert(second == Rect(0, 0, 0, 0))
    assert(third == Rect(0, 0, 0, 0))

  test("split4 and split5 agree with split on the segments they share"):
    val layout             = Layout(Direction.Horizontal)(2, 2, 2, 2, Constraint.fill)
    val segments           = layout.split(area)
    val (a, b, c, d)       = layout.split4(area)
    val (v, wide, x, y, z) = layout.split5(area)
    assert(Seq(a, b, c, d) == segments.take(4))
    assert(Seq(v, wide, x, y, z) == segments)

  test("Spacing.Overlap makes adjacent segments share cells"):
    // two 4-wide segments in a 7-wide area: the second starts at 3, so column 3 belongs to both. That shared column is
    // where two bordered blocks put one border line instead of two.
    val rects = Layout(Direction.Horizontal, Seq(Constraint.Length(4), Constraint.Length(4)))
      .spaced(Spacing.Overlap(1))
      .split(Rect(0, 0, 7, 1))
    assert(rects == Seq(Rect(0, 0, 4, 1), Rect(3, 0, 4, 1)))

  test("Spacing.Overlap works the same way down a column"):
    val rects = Layout(Direction.Vertical, Seq(Constraint.Length(3), Constraint.Length(3)))
      .spaced(Spacing.Overlap(1))
      .split(Rect(2, 1, 6, 5))
    assert(rects == Seq(Rect(2, 1, 6, 3), Rect(2, 3, 6, 3)))

  test("Spacing.Overlap(0) and Spacing.Gap(0) are the same layout"):
    val constraints = Seq(Constraint.Length(3), Constraint.Length(3))
    val area        = Rect(0, 0, 8, 1)
    val overlap     = Layout(Direction.Horizontal, constraints).spaced(Spacing.Overlap(0))
    val gap         = Layout(Direction.Horizontal, constraints).spaced(Spacing.Gap(0))
    assert(overlap.split(area) == gap.split(area))
    assert(overlap.split(area) == Layout(Direction.Horizontal, constraints).split(area))

  test("Spacing.Gap behaves exactly like the spacing field"):
    val constraints = Seq(Constraint.Length(2), Constraint.Length(2))
    val area        = Rect(0, 0, 9, 1)
    val explicit    = Layout(Direction.Horizontal, constraints).spaced(Spacing.Gap(3))
    val field       = Layout(Direction.Horizontal, constraints, spacing = 3)
    assert(explicit.split(area) == field.split(area))

  test("a negative cell count in either Spacing case is clamped to zero"):
    // the sign lives in the case you chose, never in the number, so nobody gets an overlap from a stray minus
    assert(Spacing.Gap(-4).signed == 0)
    assert(Spacing.Overlap(-4).signed == 0)
    assert(Spacing.none.signed == 0)

  test("an overlap deeper than a segment never places one outside the area"):
    val rects = Layout(Direction.Horizontal, Seq(Constraint.Length(2), Constraint.Length(2), Constraint.Length(2)))
      .spaced(Spacing.Overlap(9))
      .split(Rect(4, 0, 6, 1))
    assert(rects.forall(rect => rect.x >= 4 && rect.right <= 10 && rect.width >= 0))

  test("an overlap stays inside the area under a centring flex mode"):
    for mode <- Seq(Flex.Center, Flex.End, Flex.SpaceBetween, Flex.SpaceAround, Flex.SpaceEvenly) do
      val rects = Layout(Direction.Horizontal, Seq(Constraint.Length(4), Constraint.Length(4)), flex = mode)
        .spaced(Spacing.Overlap(2))
        .split(Rect(0, 0, 10, 1))
      assert(rects.forall(rect => rect.x >= 0 && rect.right <= 10), s"$mode placed a segment outside the area")

  test("overlapping segments report no room between them"):
    val (_, spacers) = Layout(Direction.Horizontal, Seq(Constraint.Length(4), Constraint.Length(4)))
      .spaced(Spacing.Overlap(1))
      .splitWithSpacers(Rect(0, 0, 7, 1))
    // the shared column belongs to both neighbours; it is not a gap, so the spacer between them is empty
    assert(spacers.map(_.width) == Seq(0, 0, 0))
