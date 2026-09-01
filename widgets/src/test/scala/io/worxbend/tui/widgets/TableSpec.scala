package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Constraint, Flex, Line, Modifiers}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class TableSpec extends AnyFunSuite:

  private def row(cells: String*): Seq[Line] = cells.map(Line.raw)

  test("rows lay out in constraint-sized columns"):
    val table  = Table(
      rows = Seq(row("a", "one"), row("b", "two")),
      widths = Seq(Constraint.Length(2), Constraint.Fill(1)),
      columnSpacing = 0,
    )
    val buffer = rendered(table, 8, 2)
    assert(trimmedLines(buffer) == Seq("a one", "b two"))

  test("the header renders first with the header style"):
    val table  = Table(
      rows = Seq(row("1", "x")),
      widths = Seq(Constraint.Length(3), Constraint.Length(3)),
      header = Some(row("id", "val")),
      columnSpacing = 0,
    )
    val buffer = rendered(table, 8, 3)
    assert(trimmedLines(buffer) == Seq("id val", "1  x", ""))
    assert(buffer.get(0, 0).style.modifiers.hasAny(Modifiers.Bold))
    assert(!buffer.get(0, 1).style.modifiers.hasAny(Modifiers.Bold))

  test("cell content is clipped to its column width"):
    val table  = Table(
      rows = Seq(row("abcdef", "z")),
      widths = Seq(Constraint.Length(3), Constraint.Length(1)),
      columnSpacing = 1,
    )
    val buffer = rendered(table, 6, 1)
    assert(trimmedLines(buffer) == Seq("abc z"))

  test("rows past the bottom edge are clipped"):
    val table  = Table(
      rows = Seq(row("1"), row("2"), row("3")),
      widths = Seq(Constraint.Fill(1)),
    )
    val buffer = rendered(table, 3, 2)
    assert(trimmedLines(buffer) == Seq("1", "2"))

  test("ofStrings builds the same table as wrapping every cell in Line.raw"):
    val widths     = Seq(Constraint.Length(2), Constraint.Fill(1))
    val fromLines  = Table(Seq(row("a", "one")), widths, header = Some(row("k", "v")), columnSpacing = 0)
    val fromString = Table.ofStrings(Seq(Seq("a", "one")), widths, header = Some(Seq("k", "v")), columnSpacing = 0)
    assert(fromString == fromLines)

  test("no widths at all divides the area equally between the columns the rows have"):
    val table  = Table(rows = Seq(row("a", "b", "c")), widths = Seq.empty, columnSpacing = 0)
    val buffer = rendered(table, 9, 1)
    // three equal three-column cells: each cell starts on a multiple of three
    assert(buffer.get(0, 0).symbol == "a")
    assert(buffer.get(3, 0).symbol == "b")
    assert(buffer.get(6, 0).symbol == "c")

  test("the derived column count covers the header as well as the rows"):
    val table  = Table(
      rows = Seq(row("a")),
      widths = Seq.empty,
      header = Some(row("one", "two")),
      columnSpacing = 0,
    )
    val buffer = rendered(table, 8, 2)
    assert(buffer.get(4, 0).symbol == "t") // two columns, so the second header cell starts at column four
    assert(buffer.get(0, 1).symbol == "a")

  test("no widths and no cells renders nothing rather than failing"):
    val buffer = rendered(Table(rows = Seq(Seq.empty), widths = Seq.empty), 6, 2)
    assert(trimmedLines(buffer) == Seq("", ""))

  test("fixed-width columns pack at the left by default and the leftover trails"):
    val table = Table(
      rows = Seq(row("ab", "cd")),
      widths = Seq(Constraint.Length(2), Constraint.Length(2)),
      columnSpacing = 0,
    )
    assert(rendered(table, 8, 1).get(0, 0).symbol == "a")

  test("flex centres and right-aligns the block of fixed-width columns"):
    def cells(mode: Flex): Table =
      Table(
        rows = Seq(row("ab", "cd")),
        widths = Seq(Constraint.Length(2), Constraint.Length(2)),
        columnSpacing = 0,
        flex = mode,
      )
    // four columns of content in an eight-column area leaves four cells over
    assert(rendered(cells(Flex.Center), 8, 1).get(2, 0).symbol == "a")
    assert(rendered(cells(Flex.End), 8, 1).get(4, 0).symbol == "a")

  test("flex has nothing to place once a Fill column absorbs the leftover"):
    def cells(mode: Flex): Table =
      Table(
        rows = Seq(row("ab", "cd")),
        widths = Seq(Constraint.Length(2), Constraint.Fill(1)),
        columnSpacing = 0,
        flex = mode,
      )
    assert(trimmedLines(rendered(cells(Flex.Center), 8, 1)) == trimmedLines(rendered(cells(Flex.Start), 8, 1)))
