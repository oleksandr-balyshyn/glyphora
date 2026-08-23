package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Constraint, Line, Rect, Span, Style, Text}

import org.scalatest.funsuite.AnyFunSuite

/** Rendering must cost what the *viewport* costs, not what the dataset costs.
  *
  * These assert observable work rather than wall-clock time, so they are stable on a loaded CI box: a `Seq` that counts
  * how often it was traversed catches an accidental `foreach` over ten thousand rows far more reliably than a timing
  * threshold.
  */
final class ViewportCostSpec extends AnyFunSuite:

  private val widths = Seq(Constraint.Length(6), Constraint.Fill(1))

  /** A view over `rows` that records how many elements were actually pulled. */
  final class CountingSeq[A](underlying: Seq[A]) extends Seq[A]:
    var touched: Int          = 0
    def apply(i: Int): A      = { touched += 1; underlying(i) }
    def length: Int           = underlying.length
    def iterator: Iterator[A] =
      underlying.iterator.map { element =>
        touched += 1
        element
      }

  test("Table only touches the rows it can draw"):
    val data   = Vector.tabulate(10000)(i => Seq(Line.raw(i.toString), Line.raw(s"row $i")))
    val rows   = CountingSeq(data)
    val buffer = Buffer(Rect(0, 0, 40, 20))
    Table(rows, widths).render(Rect(0, 0, 40, 20), buffer)
    assert(rows.touched <= 20, s"walked ${rows.touched} rows to draw 20")
    assert(buffer.get(0, 0).symbol == "0", "the first row still rendered")

  test("Table still renders every row that fits, header included"):
    val data   = Vector.tabulate(3)(i => Seq(Line.raw(i.toString), Line.raw(s"row $i")))
    val buffer = Buffer(Rect(0, 0, 40, 10))
    Table(data, widths, header = Some(Seq(Line.raw("id"), Line.raw("name")))).render(Rect(0, 0, 40, 10), buffer)
    assert(buffer.get(0, 0).symbol == "i") // header
    assert(buffer.get(0, 1).symbol == "0")
    assert(buffer.get(0, 3).symbol == "2")

  test("Table clips rather than overflowing when there are more rows than lines"):
    val data   = Vector.tabulate(50)(i => Seq(Line.raw(i.toString), Line.raw("x")))
    val buffer = Buffer(Rect(0, 0, 40, 3))
    Table(data, widths).render(Rect(0, 0, 40, 3), buffer)
    assert(buffer.get(0, 2).symbol == "2")

  test("a wrapping Paragraph only wraps as far as the viewport needs"):
    val lines  = Vector.tabulate(10000)(i => Line(Seq(Span(s"line $i " + "word " * 40, Style.Default))))
    val text   = CountingSeq(lines)
    val buffer = Buffer(Rect(0, 0, 30, 10))
    Paragraph(Text(text), overflow = Overflow.Wrap).render(Rect(0, 0, 30, 10), buffer)
    assert(text.touched <= 20, s"wrapped ${text.touched} source lines to fill 10 rows")
    assert(buffer.get(0, 0).symbol == "l")

  test("DataTable does not re-sort while only the scroll offset moves"):
    val data   = Vector.tabulate(500)(i => Seq(i.toString, s"item ${(i * 37) % 500}"))
    val table  = DataTable(Seq("id", "name"), data, widths)
    val state  = DataTableState()
    state.sort = Some(ColumnSort(1, SortDirection.Ascending))
    val first  = table.filteredRows(state)
    state.offset = 42
    val second = table.filteredRows(state)
    assert(first eq second, "scrolling recomputed the sorted view")

  test("DataTable recomputes when the sort, direction or filter changes"):
    val data   = Vector.tabulate(50)(i => Seq(i.toString, s"item ${(i * 7) % 50}"))
    val table  = DataTable(Seq("id", "name"), data, widths)
    val state  = DataTableState()
    val plain  = table.filteredRows(state)
    state.sortBy(1)
    val sorted = table.filteredRows(state)
    assert(sorted ne plain)
    assert(sorted.map(_(1)) == sorted.map(_(1)).sorted)
    state.sortBy(1) // same column again flips direction
    val reversed = table.filteredRows(state)
    assert(reversed.map(_(1)) == sorted.map(_(1)).reverse)
    state.setFilter("item 1")
    val filtered = table.filteredRows(state)
    assert(filtered.forall(_.exists(_.contains("item 1"))))
    assert(filtered.size < data.size)

  test("invalidate is what a caller uses after swapping same-length data"):
    val first  = Vector(Seq("1", "b"), Seq("2", "a"))
    val second = Vector(Seq("1", "z"), Seq("2", "y"))
    val state  = DataTableState()
    state.sort = Some(ColumnSort(1, SortDirection.Ascending))
    assert(DataTable(Seq("id", "name"), first, widths).filteredRows(state).map(_(1)) == Seq("a", "b"))
    // the cache key cannot see through a Seq to its contents, so the caller must say the data moved
    state.invalidate()
    assert(DataTable(Seq("id", "name"), second, widths).filteredRows(state).map(_(1)) == Seq("y", "z"))
