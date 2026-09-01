package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Constraint, Rect}

import org.scalatest.funsuite.AnyFunSuite

final class LegendFitSpec extends AnyFunSuite:

  private val area = Rect(0, 0, 40, 20)

  private def fits(width: Int, height: Int, constraint: Constraint): Boolean =
    LegendFit.fits(area, width, height, (constraint, constraint))

  test("width measures the widest entry in terminal columns and adds the padding"):
    assert(LegendFit.width(Seq("■ cpu", "■ memory"), padding = 0) == 8)
    assert(LegendFit.width(Seq("■ cpu"), padding = 2) == 7)
    // 負荷 is two ideographs occupying two columns each, so the entry is six columns, not four characters
    assert(LegendFit.width(Seq("■ 負荷"), padding = 0) == 6)
    assert(LegendFit.width(Seq.empty, padding = 2) == 2)

  test("an empty legend never fits, whatever the constraint allows"):
    assert(!fits(0, 3, Constraint.Percentage(100)))
    assert(!fits(6, 0, Constraint.Percentage(100)))

  test("Length and Max cap the legend at a number of cells"):
    assert(fits(10, 10, Constraint.Length(10)))
    assert(!fits(11, 10, Constraint.Length(10)))
    assert(fits(10, 10, Constraint.Max(10)))
    assert(!fits(11, 10, Constraint.Max(10)))

  test("Percentage and Ratio cap the legend at a share of the area"):
    assert(fits(10, 5, Constraint.Percentage(25))) // a quarter of 40 columns and of 20 rows
    assert(!fits(11, 5, Constraint.Percentage(25)))
    assert(fits(10, 5, Constraint.Ratio(1, 4)))
    assert(!fits(10, 6, Constraint.Ratio(1, 4)))

  test("a zero denominator permits nothing rather than dividing by zero"):
    assert(!fits(1, 1, Constraint.Ratio(1, 0)))

  test("Min and Fill describe a floor, not a ceiling, so they never hide a legend"):
    assert(fits(40, 20, Constraint.Min(2)))
    assert(fits(40, 20, Constraint.Fill(1)))

  test("a constraint can never permit more cells than the area actually has"):
    assert(!fits(41, 1, Constraint.Length(100)))
    assert(!fits(1, 21, Constraint.Max(100)))
