package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Alignment, Constraint, Line}
import io.worxbend.tui.testsupport.BufferAssertions.{lines, rendered}

import org.scalatest.funsuite.AnyFunSuite

/** Where a cell's text sits inside the column it was given.
  *
  * `lines` rather than `trimmedLines` throughout: trailing blanks are exactly what these tests are about, so trimming
  * them away would let a left-aligned cell pass a right-alignment assertion.
  */
final class CellAlignmentSpec extends AnyFunSuite:

  private val oneColumn: Seq[Constraint] = Seq(Constraint.Length(6))

  private def cell(content: String, alignment: Alignment): Seq[Line] =
    Seq(Line.raw(content).aligned(alignment))

  test("a table cell with no alignment of its own stays flush left"):
    val table = Table(rows = Seq(Seq(Line.raw("ab"))), widths = oneColumn)
    assert(lines(rendered(table, 6, 1)) == Seq("ab    "))

  test("a centred table cell sits in the middle of its column"):
    val table = Table(rows = Seq(cell("ab", Alignment.Center)), widths = oneColumn)
    assert(lines(rendered(table, 6, 1)) == Seq("  ab  "))

  test("a right-aligned table cell ends at its column's right edge"):
    val table = Table(rows = Seq(cell("ab", Alignment.Right)), widths = oneColumn)
    assert(lines(rendered(table, 6, 1)) == Seq("    ab"))

  test("alignment is per cell, so one column can be right-aligned while its neighbour is not"):
    val table = Table(
      rows = Seq(Seq(Line.raw("id"), Line.raw("42").rightAligned)),
      widths = Seq(Constraint.Length(4), Constraint.Length(4)),
      columnSpacing = 0,
    )
    assert(lines(rendered(table, 8, 1)) == Seq("id    42"))

  test("a right-aligned header caption sits over its right-aligned figures"):
    val table = Table(
      rows = Seq(Seq(Line.raw("7").rightAligned), Seq(Line.raw("100").rightAligned)),
      widths = oneColumn,
      header = Some(Seq(Line.raw("n").rightAligned)),
    )
    assert(lines(rendered(table, 6, 3)) == Seq("     n", "     7", "   100"))

  test("alignment measures display width, so a CJK cell lines up on columns and not on characters"):
    // "日本" is two characters and four terminal columns; right-aligning it in six leaves two blanks, not four
    val table = Table(rows = Seq(cell("日本", Alignment.Right)), widths = oneColumn)
    assert(lines(rendered(table, 6, 1)) == Seq("  日本"))

  test("an emoji cell with a combining mark aligns on the columns its clusters occupy"):
    // "e" followed by U+0301 (a combining acute accent) is one cluster in one column, "x" is a second, and the rocket
    // is one cluster two columns wide: four columns in all, so right-aligning it in six leaves two blank columns and
    // not the three a character count would predict
    val table = Table(rows = Seq(cell("e\u0301x\uD83D\uDE80", Alignment.Right)), widths = oneColumn)
    assert(lines(rendered(table, 6, 1)) == Seq("  e\u0301x\uD83D\uDE80"))

  test("a cell wider than its column still clips from the right, whatever its alignment"):
    val alignments = Seq(Alignment.Left, Alignment.Center, Alignment.Right)
    val rendered_  =
      alignments.map(alignment => lines(rendered(Table(Seq(cell("abcdefgh", alignment)), oneColumn), 6, 1)))
    assert(rendered_ == Seq.fill(3)(Seq("abcdef")))

  test("a DataTable column alignment places the body, the header and the footer alike"):
    val table = DataTable(
      columns = Seq("n"),
      rows = Seq(Seq("7"), Seq("100")),
      widths = oneColumn,
      footer = Some(Seq("sum")),
      alignments = Seq(Alignment.Right),
    )
    assert(lines(rendered(table, DataTableState(), 6, 4)) == Seq("     n", "     7", "   100", "   sum"))

  test("a DataTable alignment sequence shorter than the column list leaves the rest left-aligned"):
    val table = DataTable(
      columns = Seq("a", "b"),
      rows = Seq(Seq("1", "2")),
      widths = Seq(Constraint.Length(3), Constraint.Length(3)),
      columnSpacing = 0,
      alignments = Seq(Alignment.Right),
    )
    assert(lines(rendered(table, DataTableState(), 6, 2)) == Seq("  ab  ", "  12  "))

  test("DataTable alignment entries past the last column are ignored rather than throwing"):
    val table = DataTable(
      columns = Seq("a"),
      rows = Seq(Seq("1")),
      widths = oneColumn,
      alignments = Seq(Alignment.Right, Alignment.Center, Alignment.Left),
    )
    assert(lines(rendered(table, DataTableState(), 6, 2)) == Seq("     a", "     1"))

  test("a right-aligned DataTable header keeps its sort indicator against the right edge"):
    val state = DataTableState()
    state.sortBy(0)
    val table = DataTable(
      columns = Seq("n"),
      rows = Seq(Seq("1")),
      widths = oneColumn,
      alignments = Seq(Alignment.Right),
    )
    assert(lines(rendered(table, state, 6, 1)) == Seq("   n ▲"))

  test("with no alignments a DataTable renders exactly as it did before the parameter existed"):
    val plain   = DataTable(columns = Seq("a"), rows = Seq(Seq("1")), widths = oneColumn)
    val spelled = plain.copy(alignments = Seq(Alignment.Left))
    assert(lines(rendered(plain, DataTableState(), 6, 2)) == lines(rendered(spelled, DataTableState(), 6, 2)))
