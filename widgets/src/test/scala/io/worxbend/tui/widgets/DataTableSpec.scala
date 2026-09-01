package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Constraint, Flex, Modifiers}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class DataTableSpec extends AnyFunSuite:

  private val table = DataTable(
    columns = Seq("name", "size"),
    rows = Seq(Seq("beta", "20"), Seq("alpha", "100"), Seq("gamma", "3")),
    widths = Seq(Constraint.Length(8), Constraint.Length(6)),
  )

  test("renders the header row then the data rows"):
    val lines = trimmedLines(rendered(table, DataTableState(), 15, 4))
    assert(lines == Seq("name     size", "beta     20", "alpha    100", "gamma    3"))

  test("sorting by a text column orders rows and marks the header"):
    val state = DataTableState()
    state.sortBy(0)
    val lines = trimmedLines(rendered(table, state, 15, 4))
    assert(lines.head.startsWith("name ▲"))
    assert(lines.drop(1) == Seq("alpha    100", "beta     20", "gamma    3"))

  test("sorting the same column again flips the direction"):
    val state = DataTableState()
    state.sortBy(0)
    state.sortBy(0)
    val lines = trimmedLines(rendered(table, state, 15, 4))
    assert(lines.head.startsWith("name ▼"))
    assert(lines(1).startsWith("gamma"))

  test("numeric columns sort as numbers, not text"):
    val state = DataTableState()
    state.sortBy(1)
    val lines = trimmedLines(rendered(table, state, 15, 4))
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
    val buffer = rendered(table, state, 15, 3) // header + 2 body rows
    assert(state.selected.contains(2))
    assert(state.offset == 1)
    assert(trimmedLines(buffer)(2) == "gamma    3")
    assert(buffer.get(0, 2).style.modifiers.hasAny(Modifiers.Reverse))

  test("an empty filter result renders only the header"):
    val state = DataTableState()
    state.setFilter("zzz")
    val lines = trimmedLines(rendered(table, state, 15, 4))
    assert(lines == Seq("name     size", "", "", ""))

  test("a page size windows the visible rows and paging clamps at the ends"):
    val state = DataTableState()
    state.paging = Some(Paging(size = 2, page = 0))
    assert(table.visibleRows(state).map(_.head) == Seq("beta", "alpha"))
    state.nextPage(table.filteredRows(state).size)
    assert(state.paging.map(_.page).contains(1))
    assert(table.visibleRows(state).map(_.head) == Seq("gamma"))
    state.nextPage(table.filteredRows(state).size) // clamped: already the last page
    assert(state.paging.map(_.page).contains(1))
    state.previousPage()
    assert(state.paging.map(_.page).contains(0))

  test("paging is a no-op while no page size is set"):
    val state = DataTableState()
    state.nextPage(3)
    state.previousPage()
    assert(state.paging.isEmpty)
    assert(table.visibleRows(state).sizeIs == 3)

  test("filtering shrinks the page domain and the visible page snaps back into range"):
    val state = DataTableState()
    state.paging = Some(Paging(size = 2, page = 1))
    state.setFilter("alph")
    assert(table.visibleRows(state).map(_.head) == Seq("alpha")) // page snapped back into range

  test("reading the visible rows leaves the state alone; rendering repairs the out-of-range page"):
    val state = DataTableState()
    state.paging = Some(Paging(size = 2, page = 4))
    val _     = table.visibleRows(state)
    assert(state.paging.map(_.page).contains(4)) // a read is only a read
    val _ = rendered(table, state, 15, 4)
    assert(state.paging.map(_.page).contains(1)) // three rows over two-row pages: page 1 is the last

  test("no highlight symbol reserves no gutter, so the columns start at the area's left edge"):
    val state = DataTableState()
    state.selected = Some(1)
    val lines = trimmedLines(rendered(table, state, 15, 4))
    assert(lines == Seq("name     size", "beta     20", "alpha    100", "gamma    3"))

  test("a highlight symbol marks the selected row and pads every other one"):
    val state    = DataTableState()
    state.selected = Some(1)
    val withMark = table.copy(highlightSymbol = "> ")
    val buffer   = rendered(withMark, state, 17, 4)
    // the gutter is two columns wide on every row, so the header and the body stay in one grid
    assert(trimmedLines(buffer) == Seq("  name     size", "  beta     20", "> alpha    100", "  gamma    3"))

  test("a wide highlight symbol reserves its display width, not its character count"):
    // "選" is a single character but occupies two terminal columns, which is what a naive `length` gets wrong
    val narrow = table.copy(highlightSymbol = "→")
    val wide   = table.copy(highlightSymbol = "選")
    assert(trimmedLines(rendered(narrow, DataTableState(), 17, 2)).head == " name     size")
    assert(trimmedLines(rendered(wide, DataTableState(), 17, 2)).head == "  name     size")

  test("the highlight symbol is styled with the selected row's style"):
    val state  = DataTableState()
    state.selected = Some(0)
    val buffer = rendered(table.copy(highlightSymbol = "> "), state, 17, 4)
    assert(buffer.get(0, 1).style.modifiers.hasAny(Modifiers.Reverse))
    assert(!buffer.get(0, 2).style.modifiers.hasAny(Modifiers.Reverse))

  test("a highlight symbol wider than the whole area leaves no room for cells and draws no garbage"):
    val state  = DataTableState()
    state.selected = Some(0)
    val buffer = rendered(table.copy(highlightSymbol = ">>>>>>"), state, 3, 2)
    assert(trimmedLines(buffer) == Seq("", ">>>"))

  test("no widths at all gives every named column an equal share of the area"):
    val equal  = DataTable(columns = Seq("a", "b"), rows = Seq(Seq("1", "2")), widths = Seq.empty, columnSpacing = 0)
    val buffer = rendered(equal, DataTableState(), 8, 2)
    assert(buffer.get(0, 0).symbol == "a")
    assert(buffer.get(4, 0).symbol == "b") // two four-column halves of an eight-column area
    assert(buffer.get(4, 1).symbol == "2")

  test("flex places the leftover width when the columns are all fixed"):
    val centred = table.copy(flex = Flex.Center)
    // eight plus six plus one cell of spacing is fifteen columns of content in a twenty-one column area
    assert(rendered(centred, DataTableState(), 21, 2).get(3, 0).symbol == "n")
    assert(rendered(table, DataTableState(), 21, 2).get(0, 0).symbol == "n")

  test("the highlight gutter is taken off before the flex distributes what is left"):
    val centred = table.copy(flex = Flex.Center, highlightSymbol = "> ")
    // the gutter takes two columns, so the fifteen columns of content centre in the remaining nineteen
    assert(rendered(centred, DataTableState(), 21, 2).get(4, 0).symbol == "n")
