package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Constraint, Text}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class ContainersSpec extends AnyFunSuite:

  test("a row renders items side by side in constraint-sized segments"):
    val row = Row(
      Seq(
        LayoutItem(Constraint.Length(3), Paragraph(Text.raw("ab"))),
        LayoutItem(Constraint.Fill(1), Paragraph(Text.raw("cd"))),
      )
    )
    assert(trimmedLines(rendered(row, 7, 1)) == Seq("ab cd"))

  test("a column stacks items vertically"):
    val column = Column(
      Seq(
        LayoutItem(Constraint.Length(1), Paragraph(Text.raw("top"))),
        LayoutItem(Constraint.Fill(1), Paragraph(Text.raw("bottom"))),
      )
    )
    assert(trimmedLines(rendered(column, 6, 3)) == Seq("top", "bottom", ""))

  test("layout composition: a row of two Tier 1 widgets renders both"):
    val dashboardRow = Row(
      Seq(
        LayoutItem(Constraint.Percentage(50), Gauge(0.5)),
        LayoutItem(Constraint.Percentage(50), Sparkline(Seq(1, 2, 4, 8), max = Some(8))),
      )
    )
    val buffer       = rendered(dashboardRow, 8, 1)
    assert(trimmedLines(buffer) == Seq("50% ▁▂▄█"))

  test("a spacer claims space but draws nothing"):
    val row = Row(
      Seq(
        LayoutItem(Constraint.Length(1), Paragraph(Text.raw("a"))),
        LayoutItem(Constraint.Length(2), Spacer),
        LayoutItem(Constraint.Length(1), Paragraph(Text.raw("b"))),
      )
    )
    assert(trimmedLines(rendered(row, 4, 1)) == Seq("a  b"))

  test("nested containers compose"):
    val nested = Column(
      Seq(
        LayoutItem(Constraint.Length(1), Row(Seq(LayoutItem(Constraint.Fill(1), Paragraph(Text.raw("x")))))),
        LayoutItem(Constraint.Length(1), Paragraph(Text.raw("y"))),
      )
    )
    assert(trimmedLines(rendered(nested, 2, 2)) == Seq("x", "y"))
