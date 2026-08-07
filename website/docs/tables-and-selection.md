---
title: Tables & selection
description: Refresh, sort, filter, page, and select rows in glyphora tables without losing ordering, scroll position, or the highlighted item.
---

# Drive a table over data that keeps changing

A table over static rows needs nothing from this page. A table whose rows are
replaced wholesale every couple of seconds — a process list, a job queue, a
metrics sample — needs all of it, because the ordering, the filter, the scroll
position and the highlighted row survive a refresh only if you make them.

Every snippet here comes from `examples/procmon`, a `top`-style monitor whose
`DataTable` is thrown away and rebuilt on a timer while the reader is still
looking at it. [Build a process monitor](./build-a-process-monitor) assembles
them into a running application; this page is the reference for each piece on
its own. Unless a snippet shows additional imports, assume:

```scala
import io.worxbend.tui.dsl.*
import io.worxbend.tui.widgets.{DataTable, DataTableState}
```

## Choose Table or DataTable

| Widget | Owns | Reach for it when |
|---|---|---|
| `table` / `TableElement` | column widths and an optional header, nothing else | the rows are short, static and read-only — a percentile summary, a key list |
| `dataTable` / `DataTableElement` | sort, filter, selection, scroll and paging, all in a caller-owned `DataTableState` | the reader has to find a row, and the rows outlive one frame |

`table` renders and stops there: no state, no focus, no keys. That is the point —
a six-row latency summary that could be highlighted but never acted on invites a
click that does nothing. The `table(rows, widths*)` factory omits the header, so
construct `TableElement(rows, widths, header = Some(...))` directly when you want
one.

## Rebuild the table, keep the state

`DataTable` is an immutable case class over the rows. `DataTableState` is the
mutable half holding everything the reader has done to them. Build the first per
frame; create the second once:

```scala
val tableState: DataTableState = DataTableState()

private def buildTable(rows: Seq[ProcessInfo]): DataTable =
  DataTable(
    columns = Seq("   PID", "USER", " CPU%", " MEM%", "COMMAND"),
    rows = rows.map(cellsOf),
    widths = Seq(
      Constraint.Length(8),
      Constraint.Length(10),
      Constraint.Length(7),
      Constraint.Length(7),
      Constraint.Fill(1),
    ),
  )

def view(using ReactiveScope): Element =
  dataTable(buildTable(processes.get), tableState).fill
```

A fresh `DataTable` per frame is cheap: it holds the rows and nothing derived
from them. `DataTableState` is the half that must not be rebuilt — constructing
it inside `view` resets the sort column, filter, selection and scroll offset on
every redraw, so the table sits at row zero for ever. That is the general rule
applied to one widget: [The state ownership
rule](./widgets#the-state-ownership-rule).

## Invalidate on every refresh

`DataTable` memoises its filtered, sorted view on the state, keyed on
`(sortColumn, sortAscending, filter, rowCount)` — re-sorting ten thousand rows on
every frame is what pushes a redraw past the tick budget. Drop the cache
yourself whenever the data changes:

```scala
private def refresh(): Unit =
  Async.runCatching(source.sample()) {
    case Right(sampled) =>
      processes.set(sampled.toVector)
      tableState.invalidate()
      restoreSelection()
    case Left(error) =>
      notify(s"sample failed: ${error.getMessage}", NoticeLevel.Warning)
  }
```

The key cannot see through a `Seq` to its contents, so a refresh that returns the
same *number* of rows with different numbers in them keeps the previous ordering
indefinitely. Call `invalidate()` on every refresh that might not change the row
count, which in practice means every refresh. The bug survives a casual test
because a list whose length happens to change on each sample hides it completely.
The refresh itself — timers, cancellation, stale responses — belongs to [Live
data & background work](./live-data).

## Sort numbers that carry units

`DataTable` picks one ordering for a whole column: numeric when every cell in it
parses as a non-`NaN` `Double`, case-insensitive text otherwise. So a column of
`"900M"` and `"1.2G"` has no numeric reading at all, and `"1.2G"` sorts above
`"900M"`. Emit bare numbers and put the unit in the header:

```scala
private def cellsOf(process: ProcessInfo): Seq[String] =
  Seq(
    integer(process.pid, PidWidth),
    process.user,
    decimal(process.cpuPercent, NumberWidth),
    decimal(process.memPercent, NumberWidth),
    process.command,
  )
```

The alternative is to sort the domain objects yourself and hand the widget rows
that are already in order, leaving `tableState.sortColumn = None`:

```scala
val ordered = processes.get.sortBy(-_.cpuPercent)
dataTable(buildTable(ordered), tableState).fill
```

That costs the header's `▲`/`▼` indicator, which is drawn from `sortColumn` — you
own signalling the sort in the column title. What cannot work is a unit suffix
inside the cell: it silently drops the column back to text ordering, and text
ordering of `"9.5"` against `"11.0"` is wrong in a way nobody reports as a bug.

## Bind sort keys, and mind the case

`DataTable` ships no sort or filter keys. Up/Down move the selection while
focused, PageUp/PageDown turn the page once a `pageSize` is set, and everything
else is yours to declare:

```scala
override def bindings: KeyBindings = KeyBindings(
  binding("p", "sort by pid")(sortBy(PidColumn)),
  binding("u", "sort by user")(sortBy(UserColumn)),
  binding("c", "sort by cpu")(sortBy(CpuColumn)),
  binding("m", "sort by memory")(sortBy(MemColumn)),
  binding("n", "sort by command")(sortBy(CommandColumn)),
)

private def sortBy(column: Int): Unit =
  tableState.sortBy(column)
  restoreSelection()
```

`state.sortBy(column)` starts ascending and flips the direction when the same
column is sorted again, so one key per column covers both directions. A
single-character spec keeps the case you wrote, because that case is what the
terminal reports: `binding("C", ...)` fires on Shift+C and never on plain `c`.
See [Declare commands once](./app-shell#declare-commands-once) for the rest of
the spec grammar.

## Filter as the user types

The filter lives on the table state, the text lives in a `TextInputState`, and
one of them has to push into the other. Push only on change:

```scala
import io.worxbend.tui.widgets.TextInputState

val filterInput: TextInputState = TextInputState()

private def syncFilter(): Unit =
  if tableState.filter != filterInput.value then
    tableState.setFilter(filterInput.value)
    restoreSelection()

def view(using ReactiveScope): Element =
  val table = buildTable(processes.get)
  syncFilter()
  column((filterRow :+ dataTable(table, tableState).fill)*)
```

`setFilter` keeps rows where *any* cell contains the text, case-insensitively —
and clears the selection and the scroll offset on the way past. That is why the
guard matters: calling it unconditionally would wipe the highlight and jump to
the top on every frame, including frames a timer drew while nobody was typing.
While the input holds focus it consumes keys first, so `n` types an `n` instead
of re-sorting by command.

## Pin the selection to an identity

`tableState.selected` is an index into `visibleRows`, and that sequence is
rebuilt by every sort, filter and refresh — index 3 names a different process
each time. Re-derive it from an identity you own:

```scala
private var selectedPid: Option[Int] = None

private def visibleRows: Seq[Seq[String]] =
  buildTable(processes.peek).visibleRows(tableState)

private def pidOf(row: Seq[String]): Option[Int] =
  row.headOption.flatMap(_.trim.toIntOption)

private def rememberSelection(): Unit =
  selectedPid = tableState.selected.flatMap(visibleRows.lift).flatMap(pidOf)

private def restoreSelection(): Unit =
  val rows = visibleRows
  tableState.selected = selectedPid
    .map(pid => rows.indexWhere(pidOf(_).contains(pid)))
    .filter(_ >= 0)

private def moveSelection(delta: Int): Unit =
  val rows = visibleRows
  if delta < 0 then tableState.selectPrevious(rows.size)
  else tableState.selectNext(rows.size)
  rememberSelection()
```

Nothing built in can do this for you: the widget's rows are `Seq[Seq[String]]`
and map to no domain object, so `selected` is an index and only an index. Reading
the pid back out of a cell is the price of that API — a parallel index kept
alongside would be invalidated by the first sort. Move the selection through
`moveSelection` rather than the widget's built-in Up/Down, which knows about row
indices and nothing else, and call `restoreSelection()` after every sort, filter
and refresh.

## Page without arithmetic bugs

Paging is opt-in: without a `pageSize` the body scrolls, PageUp/PageDown are left
unconsumed and keep bubbling to your bindings.

```scala
tableState.pageSize = Some(20)

tableState.nextPage(table.filteredRows(tableState).size)
tableState.previousPage()
```

Pass `filteredRows(...).size`, not the unfiltered row count: the last page is
bounded by the filtered domain, and the larger number lets the reader page off
the end into blank rows. Both calls clear the selection and the offset
deliberately, because a highlight inherited from the previous page reads as a
choice the reader did not make. And with a page size set, `selected` indexes the
*page* rather than the whole filtered set, so the identity search above must run
over the page. `pageSize = Some(area.height - 2)` is the tempting idiom and
yields `Some(0)` on a two-row terminal; the widget floors the page at one row
rather than showing nothing on every page for ever.

## Scroll with the wheel

`dataTable` has no built-in wheel behavior, unlike `list` — a wheel over an
uncustomised table does nothing at all, which reads as a hung application. Add
one handler, and turn mouse reporting on with `RunnerConfig(mouseCapture = true)`:

```scala
import io.worxbend.tui.core.MouseEventKind

dataTable(table, tableState)
  .onMouseEvent { event =>
    event.kind match
      case MouseEventKind.ScrollDown =>
        moveSelection(1)
        true
      case MouseEventKind.ScrollUp =>
        moveSelection(-1)
        true
      case _ => false
  }
  .fill
```

Moving the selection rather than the offset keeps the wheel and the arrow keys
doing the same thing, and lets one `moveSelection` record the pinned identity for
both. See [Mouse & focus](./mouse#backend-support) for what a terminal has to
support before any of this arrives.

## Right-align a numeric column

Neither `Table` nor `DataTable` can align a column, so padding is the only way to
make a numeric column readable:

```scala
import java.util.Locale

private def decimal(value: Double, width: Int): String =
  String.format(Locale.ROOT, s"%$width.1f", value)

private def integer(value: Int, width: Int): String =
  String.format(Locale.ROOT, s"%${width}d", value)
```

`Locale.ROOT`, not the ambient locale: `%.1f` writes `12,5` across much of
Europe, which looks wrong and also stops the cell parsing as a `Double`, dropping
the whole column back to text ordering. The `f` interpolator formats in the
default locale, so prefer `String.format` for any cell a column will sort.
Padding costs nothing in sort order — for non-negative numbers at one common
width and one decimal place, the numeric and lexicographic orderings agree. Pad
the header strings to match (`"   PID"`, `" CPU%"`), or the label sits left of
its own column of figures.

## Keep focus stable when the tree changes

Focus is positional unless an element carries `.key(...)`, and a table usually
shares its screen with something conditional:

```scala
private def filterRow(using ReactiveScope): Seq[Element] =
  if !filterOpen.get then Seq.empty
  else Seq(row(text(" filter ").dim.length(8), input(filterInput).fill).length(1))
```

Nothing here is keyed, and that is a decision rather than an omission: when the
row appears it becomes focusable zero and takes the focus the table had, so `/`
opens a box the reader can immediately type into, and when it disappears the
focus falls back to the table. Keying both would pin focus to the table and `/`
would open a filter box nobody was typing into. Reach for `.key("process-table")`
the moment a *different* conditional element sits above the table — one whose
appearance should not steal the keyboard. See [Keep focus stable across changing
trees](./mouse#keep-focus-stable-across-changing-trees).

## Where to go next

- [Build a process monitor](./build-a-process-monitor) — every recipe here,
  assembled into a running application with a headless test suite;
- [Live data & background work](./live-data) — the refresh that feeds the table:
  timers, cancellation, and stale responses;
- [Charts, gauges & status](./charts-and-status) — the summary header that sits
  above a table like this one;
- [Widget catalog](./widgets#tables-simple-and-interactive) — `DataTableState`'s
  fields and the rest of the caller-owned state models;
- [Mouse & focus](./mouse) — focus order, hit-testing, and the built-in
  interactions you do not have to write.
