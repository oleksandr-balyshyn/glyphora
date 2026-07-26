# `DataTable` re-sorts every frame; `Table` and `Paragraph` scale with content, not viewport

**Labels:** `performance`, `high`, `tui-widgets`

## Problem

Measured at 200×50 on JDK 25, warmed, with thread-local allocation counters:

```
Table(50 rows)   render                        128.5 us/op    456.0 KiB/op
Table(10k rows)  render (same 50 visible)      218.0 us/op    457.0 KiB/op
DataTable(10k rows) scroll one row             218.7 us/op    493.4 KiB/op
DataTable(10k rows) sorted, scroll one row    1576.5 us/op    782.1 KiB/op
Paragraph(50 wide-char lines, wrap) render     892.7 us/op   1547.6 KiB/op
```

### a) `DataTable` re-filters and re-sorts on every render

`widgets/src/main/scala/io/worxbend/tui/widgets/DataTable.scala:92-94`:

```scala
def render(area: Rect, buffer: Buffer, state: DataTableState): Unit =
  if !area.isEmpty then
    val view = visibleRows(state)
```

`visibleRows` → `filteredRows` (`DataTable.scala:68-80`) filters and then
`filtered.sortWith(...)` every time. Scrolling changes only `state.offset`; nothing about the
order changed. At 60 Hz that is ~95 ms of CPU per second re-sorting an unchanged list — and it
is also what pushes a frame past the tick budget and triggers issue #04.

### b) `Table` walks every row

`widgets/src/main/scala/io/worxbend/tui/widgets/Table.scala:28-31`:

```scala
rows.foreach { cells =>
  if y < area.bottom then
    renderRow(buffer, columns, cells, y, style)
    y += 1
}
```

The body is guarded but the traversal is not short-circuited — 10 000 iterations to draw 50.

### c) `Paragraph` wraps everything before taking the viewport

`widgets/src/main/scala/io/worxbend/tui/widgets/Paragraph.scala:19-20`:

```scala
val lines = if wrap then text.lines.flatMap(Paragraph.wrapLine(_, area.width)) else text.lines
lines.take(area.height).zipWithIndex.foreach { (line, row) =>
```

## Proposal

1. Memoize the `DataTable` view on the state object, keyed by the inputs that affect it:

```scala
// DataTableState
private[widgets] var viewCache
  : Option[((Option[Int], Boolean, String, Int), Seq[Seq[String]])] = None

def invalidate(): Unit = viewCache = None
```

`filteredRows` returns the cached value when
`(sortColumn, sortAscending, filter, rows.size)` matches. `rows.size` is a cheap proxy;
document that callers replacing the data with a same-length list must call `invalidate()`.

2. `Table`: `rows.iterator.take(area.height - headerRows).foreach { ... }`.

3. `Paragraph`: `text.lines.iterator.flatMap(wrapLine(_, area.width)).take(area.height)`.

Audit the rest of `tui-widgets` for the same shape — `ListView`, `Tree`, `DirectoryTree` and
`Log` are the likely candidates.

## Acceptance criteria

- [ ] `DataTable(10k).render` with `sortColumn = Some(1)` is under 250 µs/op
- [ ] `Table(10k).render` and `Table(50).render` are within 10 % of each other
- [ ] `Paragraph` with 10 000 wrapped lines into a 50-row area is within 10 % of 50 lines
- [ ] Sorting, filtering and data replacement still produce correct output (existing `DataTableSpec` passes)
- [ ] A benchmark for each of the three is committed so regressions are visible
