package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Constraint, Modifiers, Rect}
import io.worxbend.tui.testsupport.BufferAssertions.trimmedLines

import org.scalatest.funsuite.AnyFunSuite

final class DataTableSpec extends AnyFunSuite:

  private val table = DataTable(
    columns = Seq("name", "size"),
    rows = Seq(Seq("beta", "20"), Seq("alpha", "100"), Seq("gamma", "3")),
    widths = Seq(Constraint.Length(8), Constraint.Length(6)),
  )

  private def renderedWith(state: DataTableState, height: Int = 4): Buffer =
    val buffer = Buffer(Rect(0, 0, 15, height))
    table.render(buffer.area, buffer, state)
    buffer

  test("renders the header row then the data rows"):
    val lines = trimmedLines(renderedWith(DataTableState()))
    assert(lines == Seq("name     size", "beta     20", "alpha    100", "gamma    3"))

  test("sorting by a text column orders rows and marks the header"):
    val state = DataTableState()
    state.sortBy(0)
    val lines = trimmedLines(renderedWith(state))
    assert(lines.head.startsWith("name ▲"))
    assert(lines.drop(1) == Seq("alpha    100", "beta     20", "gamma    3"))

  test("sorting the same column again flips the direction"):
    val state = DataTableState()
    state.sortBy(0)
    state.sortBy(0)
    val lines = trimmedLines(renderedWith(state))
    assert(lines.head.startsWith("name ▼"))
    assert(lines(1).startsWith("gamma"))

  test("numeric columns sort as numbers, not text"):
    val state = DataTableState()
    state.sortBy(1)
    val lines = trimmedLines(renderedWith(state))
    assert(lines.drop(1) == Seq("gamma    3", "beta     20", "alpha    100"))

  test("the filter keeps rows where any cell matches, case-insensitively"):
    val state   = DataTableState()
    state.setFilter("A")
    val visible = table.visibleRows(state)
    assert(visible.map(_.head) == Seq("beta", "alpha", "gamma")) // all contain 'a'
    state.setFilter("alph")
    assert(table.visibleRows(state).map(_.head) == Seq("alpha"))

  test("filter and sort compose"):
    val state = DataTableState()
    state.setFilter("a")
    state.sortBy(1)
    assert(table.visibleRows(state).map(_.head) == Seq("gamma", "beta", "alpha"))

  test("the selected view row is highlighted and selection scrolls the body"):
    val state = DataTableState()
    state.selectNext(3)
    state.selectNext(3)
    state.selectNext(3) // clamped at the last row
    val buffer = renderedWith(state, height = 3) // header + 2 body rows
    assert(state.selected.contains(2))
    assert(state.offset == 1)
    assert(trimmedLines(buffer)(2) == "gamma    3")
    assert(buffer.get(0, 2).style.modifiers.has(Modifiers.Reverse))

  test("an empty filter result renders only the header"):
    val state = DataTableState()
    state.setFilter("zzz")
    val lines = trimmedLines(renderedWith(state))
    assert(lines == Seq("name     size", "", "", ""))

  test("a page size windows the visible rows and paging clamps at the ends"):
    val state = DataTableState()
    state.pageSize = Some(2)
    assert(table.visibleRows(state).map(_.head) == Seq("beta", "alpha"))
    state.nextPage(table.filteredRows(state).size)
    assert(state.page == 1)
    assert(table.visibleRows(state).map(_.head) == Seq("gamma"))
    state.nextPage(table.filteredRows(state).size) // clamped: already the last page
    assert(state.page == 1)
    state.previousPage()
    assert(state.page == 0)

  test("filtering shrinks the page domain and visibleRows re-clamps the page"):
    val state = DataTableState()
    state.pageSize = Some(2)
    state.page = 1
    state.setFilter("alph")
    assert(table.visibleRows(state).map(_.head) == Seq("alpha")) // page snapped back into range
