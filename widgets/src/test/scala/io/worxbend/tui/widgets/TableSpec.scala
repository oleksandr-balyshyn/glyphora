package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Constraint, Flex, Line, Modifiers, Style}
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

  test("the footer is pinned to the bottom of the area, not appended to the rows"):
    val table  = Table(
      rows = Seq(row("a", "1")),
      widths = Seq(Constraint.Length(3), Constraint.Length(3)),
      header = Some(row("k", "n")),
      footer = Some(row("sum", "1")),
      columnSpacing = 0,
    )
    val buffer = rendered(table, 6, 5)
    // two blank rows sit between the single data row and the totals row that owns the bottom line
    assert(trimmedLines(buffer) == Seq("k  n", "a  1", "", "", "sum1"))
    assert(buffer.get(0, 4).style.modifiers.hasAny(Modifiers.Bold))

  test("the footer costs a row of body height, so rows clip one line earlier"):
    val data      = (1 to 10).map(n => row(n.toString))
    val widths    = Seq(Constraint.Length(2))
    val plain     = Table(rows = data, widths = widths)
    val withTotal = plain.copy(footer = Some(row("=")))
    assert(trimmedLines(rendered(plain, 4, 3)) == Seq("1", "2", "3"))
    assert(trimmedLines(rendered(withTotal, 4, 3)) == Seq("1", "2", "="))

  test("a one-row area with both a header and a footer keeps the header"):
    val table = Table(
      rows = Seq(row("a")),
      widths = Seq(Constraint.Length(3)),
      header = Some(row("k")),
      footer = Some(row("z")),
    )
    assert(trimmedLines(rendered(table, 4, 1)) == Seq("k"))

  test("a footer-only table with no widths still derives its column count"):
    val table  = Table(rows = Seq.empty, widths = Seq.empty, footer = Some(row("a", "b")), columnSpacing = 0)
    val buffer = rendered(table, 8, 2)
    assert(buffer.get(0, 1).symbol == "a")
    assert(buffer.get(4, 1).symbol == "b")

  test("a bare Seq[Line] row and the TableRow it stands for draw the same thing"):
    val widths = Seq(Constraint.Length(2), Constraint.Length(3))
    val bare   = Table(rows = Seq(row("a", "one")), widths = widths, columnSpacing = 0)
    val spelt  = Table(rows = Seq(TableRow(row("a", "one"))), widths = widths, columnSpacing = 0)
    assert(trimmedLines(rendered(bare, 8, 2)) == trimmedLines(rendered(spelt, 8, 2)))

  test("a bottom margin puts a blank line under a row"):
    val table = Table(
      rows = Seq(TableRow(row("a"), bottomMargin = 1), TableRow(row("b"))),
      widths = Seq(Constraint.Length(2)),
    )
    assert(trimmedLines(rendered(table, 4, 3)) == Seq("a", "", "b"))

  test("a top margin opens a gap above a row and the cells stay on the row's first content line"):
    val table = Table(
      rows = Seq(TableRow(row("a")), TableRow(row("b"), topMargin = 2)),
      widths = Seq(Constraint.Length(2)),
    )
    assert(trimmedLines(rendered(table, 4, 4)) == Seq("a", "", "", "b"))

  test("a taller row reserves the extra lines and draws its cells at the top of them"):
    val table = Table(
      rows = Seq(TableRow(row("a"), height = 3), TableRow(row("b"))),
      widths = Seq(Constraint.Length(2)),
    )
    assert(trimmedLines(rendered(table, 4, 4)) == Seq("a", "", "", "b"))

  test("a per-row style layers over the table style"):
    val table  = Table(
      rows = Seq(TableRow(row("a")), TableRow(row("b"), style = Some(Style.Default.bold))),
      widths = Seq(Constraint.Length(2)),
    )
    val buffer = rendered(table, 4, 2)
    assert(!buffer.get(0, 0).style.modifiers.hasAny(Modifiers.Bold))
    assert(buffer.get(0, 1).style.modifiers.hasAny(Modifiers.Bold))

  test("row height and margins clamp, so a zero-height row still occupies a line"):
    val zero  = TableRow(row("a"), height = 0, topMargin = -4, bottomMargin = -1)
    assert(zero.totalHeight == 1)
    val table = Table(rows = Seq(zero, TableRow(row("b"))), widths = Seq(Constraint.Length(2)))
    assert(trimmedLines(rendered(table, 4, 2)) == Seq("a", "b"))

  test("a tall row that overruns the bottom edge is clipped rather than wrapping"):
    val table = Table(rows = Seq(TableRow(row("a"), height = 9)), widths = Seq(Constraint.Length(2)))
    assert(trimmedLines(rendered(table, 4, 2)) == Seq("a", ""))

  test("a row starting past the bottom edge is never drawn"):
    val table = Table(
      rows = Seq(TableRow(row("a"), bottomMargin = 5), TableRow(row("b"))),
      widths = Seq(Constraint.Length(2)),
    )
    assert(trimmedLines(rendered(table, 4, 3)) == Seq("a", "", ""))

  test("tall rows still bound the walk by the area rather than the data"):
    val many = (1 to 10000).map(n => TableRow(row(n.toString), height = 4))
    assert(trimmedLines(rendered(Table(rows = many, widths = Seq(Constraint.Length(4))), 6, 6)).head == "1")

  test("wide cells in a padded row are still measured in terminal columns"):
    // "選択" is two characters and four terminal columns, so a three-column cell keeps only the first of them
    val table = Table(
      rows = Seq(TableRow(row("選択"), bottomMargin = 1)),
      widths = Seq(Constraint.Length(3)),
    )
    assert(trimmedLines(rendered(table, 4, 2)) == Seq("選", ""))

  test("a spanning header cell covers several columns and the gaps between them"):
    val table  = Table(
      rows = Seq(row("1", "2", "3", "4")),
      widths = Seq.fill(4)(Constraint.Length(3)),
      header = Some(Seq(TableCell(Line.raw("inbound"), 2), TableCell(Line.raw("egress"), 2))),
      columnSpacing = 1,
    )
    val buffer = rendered(table, 16, 2)
    // two columns of three plus the one-cell gap between them is seven columns for each caption
    assert(trimmedLines(buffer) == Seq("inbound egress", "1   2   3   4"))

  test("a spanning cell pushes the cells after it along rather than overwriting them"):
    val table  = Table(
      rows = Seq(Seq(TableCell(Line.raw("ab"), 2), Line.raw("c"))),
      widths = Seq.fill(3)(Constraint.Length(2)),
      columnSpacing = 0,
    )
    val buffer = rendered(table, 6, 1)
    assert(buffer.get(0, 0).symbol == "a")
    assert(buffer.get(4, 0).symbol == "c") // the third column, not the second

  test("a bare Line cell and the one-column TableCell it stands for draw the same thing"):
    val widths = Seq(Constraint.Length(3), Constraint.Length(3))
    val bare   = Table(rows = Seq(row("a", "b")), widths = widths)
    val spelt  = Table(rows = Seq(Seq(TableCell(Line.raw("a")), TableCell(Line.raw("b")))), widths = widths)
    assert(trimmedLines(rendered(bare, 8, 1)) == trimmedLines(rendered(spelt, 8, 1)))

  test("a span is clamped to the columns that remain, so it cannot draw outside the table"):
    val table  = Table(
      rows = Seq(Seq(Line.raw("a"), TableCell(Line.raw("bbbbbbbb"), 9))),
      widths = Seq(Constraint.Length(2), Constraint.Length(2)),
      columnSpacing = 0,
    )
    val buffer = rendered(table, 6, 1)
    assert(trimmedLines(buffer) == Seq("a bb"))

  test("a span below one is read as one column"):
    val widths = Seq(Constraint.Length(2), Constraint.Length(2))
    val zero = Table(rows = Seq(Seq(TableCell(Line.raw("ab"), 0), Line.raw("cd"))), widths = widths, columnSpacing = 0)
    val one  = Table(rows = Seq(row("ab", "cd")), widths = widths, columnSpacing = 0)
    assert(trimmedLines(rendered(zero, 4, 1)) == trimmedLines(rendered(one, 4, 1)))

  test("the equal-columns fallback counts a span at its full width"):
    val table  = Table(
      rows = Seq(row("a", "b")),
      widths = Seq.empty,
      header = Some(Seq(TableCell(Line.raw("both"), 2))),
      columnSpacing = 0,
    )
    val buffer = rendered(table, 8, 2)
    // two columns derived from the span, not one: the second data cell lands halfway across
    assert(buffer.get(4, 1).symbol == "b")

  test("a spanning cell is clipped by display width, not character count"):
    // "選択肢" is three characters and six terminal columns; a five-column span keeps two of them
    val table = Table(
      rows = Seq(Seq(TableCell(Line.raw("選択肢"), 2))),
      widths = Seq(Constraint.Length(2), Constraint.Length(2)),
      columnSpacing = 1,
    )
    assert(trimmedLines(rendered(table, 5, 1)) == Seq("選択"))
