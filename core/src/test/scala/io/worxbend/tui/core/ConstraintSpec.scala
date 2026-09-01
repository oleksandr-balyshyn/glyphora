package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class ConstraintSpec extends AnyFunSuite:

  test("the bulk constructors build one constraint per argument, in order"):
    assert(Constraint.lengths(3, 10, 3) == Seq(Constraint.Length(3), Constraint.Length(10), Constraint.Length(3)))
    assert(Constraint.percentages(25, 75) == Seq(Constraint.Percentage(25), Constraint.Percentage(75)))
    assert(Constraint.mins(10, 4) == Seq(Constraint.Min(10), Constraint.Min(4)))
    assert(Constraint.maxes(20, 8) == Seq(Constraint.Max(20), Constraint.Max(8)))
    assert(Constraint.fills(1, 2, 1) == Seq(Constraint.Fill(1), Constraint.Fill(2), Constraint.Fill(1)))
    assert(Constraint.ratios(1 -> 3, 2 -> 3) == Seq(Constraint.Ratio(1, 3), Constraint.Ratio(2, 3)))

  test("a bulk constructor with no arguments is an empty sequence"):
    assert(Constraint.fills().isEmpty)
    assert(Constraint.lengths().isEmpty)
    assert(Constraint.ratios().isEmpty)

  test("a bulk constructor splits exactly like the cases spelled out by hand"):
    val area   = Rect(0, 0, 12, 1)
    val bulk   = Layout.horizontal(Constraint.fills(1, 2, 1)*).split(area)
    val manual = Layout.horizontal(Constraint.Fill(1), Constraint.Fill(2), Constraint.Fill(1)).split(area)
    assert(bulk == manual)

  test("sizeIn caps, floors and scales a single constraint"):
    assert(Constraint.Length(30).sizeIn(20) == 20)
    assert(Constraint.Length(8).sizeIn(20) == 8)
    assert(Constraint.Max(10).sizeIn(20) == 10)
    assert(Constraint.Max(10).sizeIn(5) == 5)
    assert(Constraint.Min(10).sizeIn(5) == 10)
    assert(Constraint.Min(10).sizeIn(20) == 20)
    assert(Constraint.Percentage(50).sizeIn(20) == 10)
    assert(Constraint.Percentage(150).sizeIn(20) == 20)
    assert(Constraint.Ratio(1, 3).sizeIn(10) == 3)
    assert(Constraint.Fill(3).sizeIn(20) == 20)

  test("sizeIn treats a degenerate axis or a nonsense constraint as zero"):
    assert(Constraint.Ratio(1, 0).sizeIn(10) == 0)
    assert(Constraint.Percentage(-10).sizeIn(20) == 0)
    assert(Constraint.Length(-4).sizeIn(20) == 0)
    for c <- Seq(Constraint.Length(4), Constraint.Percentage(50), Constraint.Ratio(1, 2), Constraint.Fill(2))
    do
      assert(c.sizeIn(0) == 0)
      assert(c.sizeIn(-5) == 0)
    // Min is the one case that answers more than the axis: it is a floor, not a share
    assert(Constraint.Min(4).sizeIn(0) == 4)
    assert(Constraint.Min(4).sizeIn(-5) == 4)

  test("sizeIn agrees with the solver on a single-segment axis"):
    val cases = Seq(
      Constraint.Length(8),
      Constraint.Percentage(40),
      Constraint.Ratio(2, 5),
      Constraint.Max(9),
      Constraint.Fill(2),
    )
    // the solver deliberately lets a fixed demand overrun the axis and leaves the clamping to `Layout.split`, which
    // cuts a segment off at the far edge; `sizeIn` answers what a caller can actually render into, so the comparison
    // is against the solver's answer already clamped the way `Layout` would clamp it
    for c <- cases; axis <- Seq(0, 1, 7, 20, 33)
    do assert(c.sizeIn(axis) == math.min(axis, LayoutSolver.solve(Seq(c), axis).head))
