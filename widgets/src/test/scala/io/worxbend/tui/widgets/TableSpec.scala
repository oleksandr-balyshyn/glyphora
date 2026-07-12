package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Constraint, Line, Modifiers}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class TableSpec extends AnyFunSuite:

  private def row(cells: String*): Seq[Line] = cells.map(Line.raw)

  test("rows lay out in constraint-sized columns"):
    val table = Table(
      rows = Seq(row("a", "one"), row("b", "two")),
      widths = Seq(Constraint.Length(2), Constraint.Fill(1)),
      columnSpacing = 0,
    )
    val buffer = rendered(table, 8, 2)
    assert(trimmedLines(buffer) == Seq("a one", "b two"))

  test("the header renders first with the header style"):
    val table = Table(
      rows = Seq(row("1", "x")),
      widths = Seq(Constraint.Length(3), Constraint.Length(3)),
      header = Some(row("id", "val")),
      columnSpacing = 0,
    )
    val buffer = rendered(table, 8, 3)
    assert(trimmedLines(buffer) == Seq("id val", "1  x", ""))
    assert(buffer.get(0, 0).style.modifiers.has(Modifiers.Bold))
    assert(!buffer.get(0, 1).style.modifiers.has(Modifiers.Bold))

  test("cell content is clipped to its column width"):
    val table = Table(
      rows = Seq(row("abcdef", "z")),
      widths = Seq(Constraint.Length(3), Constraint.Length(1)),
      columnSpacing = 1,
    )
    val buffer = rendered(table, 6, 1)
    assert(trimmedLines(buffer) == Seq("abc z"))

  test("rows past the bottom edge are clipped"):
    val table = Table(
      rows = Seq(row("1"), row("2"), row("3")),
      widths = Seq(Constraint.Fill(1)),
    )
    val buffer = rendered(table, 3, 2)
    assert(trimmedLines(buffer) == Seq("1", "2"))
