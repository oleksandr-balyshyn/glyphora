package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Color, Constraint, Flex, Line, Style}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}
import io.worxbend.tui.widgets.{TableCell, TableRow}
import io.worxbend.tui.widgets as w

import org.scalatest.funsuite.AnyFunSuite

/** Construction and rendering tests for `styledTable`, the element whose cells are `Line`s rather than `String`s.
  *
  * The construction half asserts the node is the plain data the DSL promises — a `StyledTableElement` a test can
  * pattern-match — and the rendering half asserts the widget it builds actually draws what the node describes, since a
  * node that carried the right fields to the wrong widget parameters would pass the first half on its own.
  */
final class StyledTableConstructionSpec extends AnyFunSuite:

  private val rows: Seq[TableRow.Source] =
    Seq(
      Seq(Line.raw("api"), Line.styled("ready", Style.Default.withFg(Color.Green))),
      Seq(Line.raw("cache"), Line.styled("failed", Style.Default.withFg(Color.Red))),
    )

  test("styledTable keeps its cells, and header/footer are absent until asked for"):
    val node = styledTable(rows, Constraint.Fill(1), Constraint.Length(8))
    assert(node.rows == rows)
    assert(node.widths == Seq(Constraint.Fill(1), Constraint.Length(8)))
    assert(node.header.isEmpty)
    assert(node.footer.isEmpty)

  test("header and footer take Line cells and keep the caller's order"):
    val node = styledTable(rows, Constraint.Fill(1))
      .header(Line.raw("service"), Line.raw("state"))
      .footer(Line.raw("total"), Line.raw("2"))
    assert(node.header.contains(Seq(Line.raw("service"), Line.raw("state"))))
    assert(node.footer.contains(Seq(Line.raw("total"), Line.raw("2"))))

  test("the flex-container builders rebuild the node instead of mutating it"):
    val base   = styledTable(rows, Constraint.Length(4))
    val spread = base.withFlex(Flex.Center).withSpacing(3)
    assert(spread.flex == Flex.Center && spread.columnSpacing == 3)
    assert(base.flex == Flex.Start && base.columnSpacing == 1, "the original node must be untouched")
    assert(base.withSpacing(-2).columnSpacing == 0, "a negative gap clamps to zero")

  test("the node builds a widgets.Table carrying the styled cells"):
    styledTable(rows, Constraint.Fill(1)).fg(Color.Blue).widget match
      case built: w.Table =>
        assert(built.rows == rows)
        assert(built.style.fg.contains(Color.Blue), "the element's own style reaches the widget")
      case other          => fail(s"expected a widgets.Table, got $other")

  test("a styled cell keeps its colour where the row around it stays plain"):
    val buffer = rendered(styledTable(rows, Constraint.Length(6), Constraint.Length(6)).widget, 13, 2)
    assert(trimmedLines(buffer) == Seq("api    ready", "cache  failed"))
    assert(buffer.get(0, 0).style.fg.isEmpty, "the plain cell carries no colour")
    assert(buffer.get(7, 0).style.fg.contains(Color.Green))
    assert(buffer.get(7, 1).style.fg.contains(Color.Red))

  test("a taller row and a spanning cell are both accepted in the same table"):
    val mixed: Seq[TableRow.Source] =
      Seq(
        Seq(TableCell(Line.raw("both columns"), columnSpan = 2)),
        TableRow(Seq(Line.raw("a"), Line.raw("b")), topMargin = 1),
      )
    val buffer = rendered(styledTable(mixed, Constraint.Length(6), Constraint.Length(6)).widget, 13, 3)
    assert(trimmedLines(buffer) == Seq("both columns", "", "a      b"))

  test("a wide cell is truncated on a cluster boundary, never mid-character"):
    val wide   = Seq(Seq(Line.raw("日本語です"), Line.raw("éx")))
    val buffer = rendered(styledTable(wide, Constraint.Length(5), Constraint.Length(2)).widget, 8, 1)
    // two ideographs fill four of the five columns; the third would need a fifth and a sixth, so it is dropped whole,
    // leaving one blank column plus the one-cell column gap before the accented cell
    assert(trimmedLines(buffer) == Seq("日本  éx"))

  test("an area with no room draws nothing and does not throw"):
    val buffer = rendered(styledTable(rows, Constraint.Fill(1)).header(Line.raw("service")).widget, 0, 0)
    assert(trimmedLines(buffer).forall(_.isEmpty))
