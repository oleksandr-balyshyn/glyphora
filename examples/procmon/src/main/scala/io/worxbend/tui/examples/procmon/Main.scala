package io.worxbend.tui.examples.procmon

import io.worxbend.tui.core.MouseEventKind
import io.worxbend.tui.dsl.*
import io.worxbend.tui.runtime.RunnerConfig
import io.worxbend.tui.widgets.{DataTable, DataTableState, TextInputState}

import java.util.Locale
import scala.concurrent.duration.DurationInt

/** procmon: a `top`-like live process monitor — a sortable, filterable `DataTable` whose rows are thrown away and
  * rebuilt every couple of seconds while the user is still looking at them.
  *
  * The five things it exists to show:
  *   - a `DataTable` driven entirely from the app's own key bindings — the widget ships no sort or filter keys;
  *   - a refresh on a timer that does its blocking work off the render thread and lands the result back on it;
  *   - derived statistics as `Computed` fields on the app rather than arithmetic inside `view`;
  *   - `DataTableState.invalidate()`, without which a refresh that keeps the row count keeps the old ordering for ever;
  *   - a selection pinned to a *process*, so a row that moves under a re-sort takes the highlight with it.
  *
  * It runs on any machine: [[ProcessSource.detect]] uses live `ps` output when it can and a synthetic list when it
  * cannot, and the tests always use the synthetic one.
  *
  * Keys: `↑`/`↓` select (mouse wheel too) · `/` filter, then `Enter` to keep it or `Esc` to clear it ·
  * `p`/`u`/`c`/`m`/`n` sort by PID/USER/CPU/MEM/COMMAND, again to reverse · `r` refresh now · `+`/`-` slower/faster
  * refresh · `ctrl+p` command palette · `q` quit.
  */
final class ProcmonApp(val source: ProcessSource = ProcessSource.detect()) extends TuiApp:

  import ProcmonApp.*

  override def config: RunnerConfig = RunnerConfig(tickRate = Some(TickRate), mouseCapture = true)

  // ---- state that must outlive `view` ----

  /** The latest sample. A `Signal`, so a refresh redraws without anyone asking. */
  val processes: Signal[Vector[ProcessInfo]] = Signal(Vector.empty)

  /** How many samples have completed — the app's own liveness counter, and what the tick test polls on. */
  val sampleCount: Signal[Int] = Signal(0)

  val refreshSeconds: Signal[Int] = Signal(2)

  /** Sort column and direction, filter text, selection and scroll offset. The widget half is rebuilt every frame; this
    * half is created once and never replaced, or every refresh would reset the user's view.
    */
  val tableState: DataTableState = DataTableState()

  val filterInput: TextInputState = TextInputState()

  private val filterOpen: Signal[Boolean] = Signal(false)

  /** Which *process* is selected. `DataTableState.selected` is an index into the filtered, sorted rows and means
    * something different after every refresh; a pid does not.
    */
  private var selectedPid: Option[Int] = None

  private var ticksUntilRefresh: Int = 0

  // start where `top` starts, on the busiest process. `sortBy` always begins ascending, so the two fields are set
  // directly — they are public, and they are the whole of `DataTableState`'s sort API.
  tableState.sortColumn = Some(CpuColumn)
  tableState.sortAscending = false

  /** Header statistics, derived once per change rather than recomputed per frame.
    *
    * A `Computed` built inside `view` would re-subscribe on every redraw and never be released, which is what
    * `Computed.dispose` exists to clean up after. Long-lived derived values belong here, as fields.
    */
  private val summary: Computed[Summary] = Computed {
    val all = processes.get
    Summary(all.size, all.map(_.cpuPercent).sum, all.map(_.memPercent).sum)
  }

  // ---- refreshing ----

  /** Ticks are the app's clock; refreshes are a multiple of them.
    *
    * Counting ticks rather than starting an `Async.every` poller keeps everything on one timer: there is no
    * `Cancelable` to leak when the app quits, `+`/`-` change the interval by changing a number, and `r` reaches exactly
    * the same code path — which is what makes the refresh testable without waiting on wall-clock time.
    */
  override def onTick(): Unit =
    if ticksUntilRefresh <= 0 then
      ticksUntilRefresh = refreshSeconds.peek * TicksPerSecond
      refresh()
    ticksUntilRefresh -= 1

  private def refresh(): Unit =
    // `sample()` shells out to `ps`, which costs tens of milliseconds — far too long to spend on the render thread,
    // because that is also the thread drawing frames. `Async.runCatching` runs it on a worker and delivers the result
    // back on the render thread, which is the only place a `Signal` may be written.
    Async.runCatching(source.sample()) {
      case Right(sampled) =>
        processes.set(sampled.toVector)
        sampleCount.update(_ + 1)
        // Mandatory, and the single easiest bug to write here: the filtered/sorted view is memoized on a key that
        // stands in for the data with its *row count*. A refresh that returns the same number of processes with
        // different numbers in them would otherwise keep the previous ordering indefinitely.
        tableState.invalidate()
        restoreSelection()
      case Left(error)    =>
        // toasts age in ticks, not seconds, so the lifetime has to be computed from the tick rate
        notify(s"sample failed: ${error.getMessage}", NoticeLevel.Warning, ttlTicks = 3 * TicksPerSecond)
    }

  // ---- keys ----

  /** One declaration per key drives three things: dispatch, the status-bar hints, and the `ctrl+p` palette. */
  override def bindings: KeyBindings = KeyBindings(
    binding("r", "refresh now")(refresh()),
    binding("/", "filter rows")(filterOpen.set(true)),
    binding("p", "sort by pid")(sortBy(PidColumn)),
    binding("u", "sort by user")(sortBy(UserColumn)),
    binding("c", "sort by cpu")(sortBy(CpuColumn)),
    binding("m", "sort by memory")(sortBy(MemColumn)),
    binding("n", "sort by command")(sortBy(CommandColumn)),
    binding("+", "slower refresh")(refreshSeconds.update(seconds => math.min(MaxRefreshSeconds, seconds + 1))),
    binding("-", "faster refresh")(refreshSeconds.update(seconds => math.max(1, seconds - 1))),
    binding("q", "quit")(quit()),
  )

  private def sortBy(column: Int): Unit =
    tableState.sortBy(column) // the same column twice flips the direction — that rule lives in the widget state
    restoreSelection() // sorting moves every row; the selection has to follow its process to the new index

  /** Enter: hide the filter box and keep filtering. The text stays in `filterInput`, so `/` reopens the box with the
    * same substring still in it and the table never flickers back to the full list.
    */
  private def applyFilter(): Unit =
    filterOpen.set(false)

  /** Escape: throw the filter away and show every process again.
    *
    * `setFilter("")` also drops the widget's selection and scroll offset, which is why `restoreSelection()` runs right
    * after it — the highlight follows its *process* back into the unfiltered list rather than landing on whatever row
    * happens to sit at the old index.
    */
  private def cancelFilter(): Unit =
    filterInput.clear()
    tableState.setFilter("")
    restoreSelection()
    filterOpen.set(false)

  // ---- selection, pinned to a process rather than to a row ----

  /** The rows the table is showing right now, in sort order — the domain `tableState.selected` indexes into. */
  private def visibleRows: Seq[Seq[String]] = buildTable(processes.peek).visibleRows(tableState)

  /** Rows are strings by the time the widget sees them, so reading the selection back means parsing the PID cell. That
    * is the cost of a table whose API is `Seq[Seq[String]]`; the alternative is a parallel index the sort would
    * immediately invalidate.
    */
  private def pidOf(row: Seq[String]): Option[Int] = row.headOption.flatMap(_.trim.toIntOption)

  /** Pins the selection to a process by reading the PID out of the row the table is currently highlighting.
    *
    * `rows` is passed in rather than read from [[visibleRows]] again because the caller has just built that list —
    * rebuilding it here would run every `String.format` in [[cellsOf]] a second time per keystroke, and would leave the
    * reader to prove that the two independently computed lists still agree. Render thread only: it reads `tableState`.
    */
  private def rememberSelection(rows: Seq[Seq[String]]): Unit =
    selectedPid = tableState.selected.flatMap(rows.lift).flatMap(pidOf)

  private def restoreSelection(): Unit =
    val rows = visibleRows
    tableState.selected = selectedPid.map(pid => rows.indexWhere(pidOf(_).contains(pid))).filter(_ >= 0)

  private def moveSelection(delta: Int): Unit =
    val rows = visibleRows
    if delta < 0 then tableState.selectPrevious(rows.size) else tableState.selectNext(rows.size)
    rememberSelection(rows)

  /** The pid under the highlight, for tests and for anything that would act on the selection (a `kill` key). */
  def selectedProcessId: Option[Int] = selectedPid

  /** The pids the table is showing, in sort order — the projection the tests assert against, and the same view
    * `tableState.selected` indexes into.
    */
  def visibleProcessIds: Seq[Int] = visibleRows.flatMap(pidOf)

  // ---- view ----

  def view(using ReactiveScope): Element =
    given Theme = theme
    val table   = buildTable(processes.get)
    syncFilter()
    val visible = table.visibleRows(tableState)
    column(
      (Seq(summaryPanel(visible.size)) ++ filterRow ++ Seq(tableElement(table), statusBar(Hints)))*
    ).onKeyEvent(handleUnclaimedKey)

  /** Pushes the input's text into the table state, but only when it actually changed: `setFilter` also clears the
    * selection and the scroll offset, and doing that on every frame would make the table impossible to use.
    */
  private def syncFilter(): Unit =
    if tableState.filter != filterInput.value then
      tableState.setFilter(filterInput.value)
      restoreSelection()

  private def summaryPanel(shown: Int)(using ReactiveScope, Theme): Element =
    val stats = summary.get
    panel("procmon")(
      text(
        s"${stats.count} processes · $shown shown · sample ${sampleCount.get} · " +
          s"every ${refreshSeconds.get}s · source ${source.name}"
      ).dim,
      // these are sums over the process list, so on a many-core machine the CPU figure can exceed 100 and the bar
      // simply saturates — `progressBar` clamps its ratio, and the exact number is in the label either way
      progressBar(stats.cpuPercent / 100.0).label(s"CPU ${decimal(stats.cpuPercent, 5)}%").ramp(ColorRamp.Traffic),
      progressBar(stats.memPercent / 100.0).label(s"MEM ${decimal(stats.memPercent, 5)}%").ramp(ColorRamp.Traffic),
    ).rounded.length(5)

  /** The filter box, present only while filtering — and deliberately *above* the table.
    *
    * Focus is positional unless an element carries `.key(...)`: nothing here does, so when this row appears it becomes
    * focusable zero and takes the focus the table had, and when it disappears the focus falls back to the table. Naming
    * them would pin focus to the table instead, and `/` would open a filter box nobody was typing into.
    */
  private def filterRow(using ReactiveScope): Seq[Element] =
    if !filterOpen.get then Seq.empty
    else
      Seq(
        row(
          text(" filter ").dim.length(8),
          input(filterInput, placeholder = "substring — matched against every column").onKeyEvent {
            case KeyEvent(KeyCode.Enter, _)  =>
              applyFilter()
              true
            case KeyEvent(KeyCode.Escape, _) =>
              cancelFilter()
              true
            case _                           => false
          }.fill,
        ).length(1)
      )

  private def tableElement(table: DataTable): Element =
    dataTable(table, tableState)
      .onKeyEvent {
        // handled here rather than left to the widget's built-in Up/Down so that every move also records *which
        // process* is selected; the built-in only knows about row indices
        case KeyEvent(KeyCode.Down, _) =>
          moveSelection(1)
          true
        case KeyEvent(KeyCode.Up, _)   =>
          moveSelection(-1)
          true
        case _                         => false
      }
      .onMouseEvent { event =>
        // `DataTableElement` has no built-in wheel behavior of its own, unlike `list`
        event.kind match
          case MouseEventKind.ScrollDown =>
            moveSelection(1)
            true
          case MouseEventKind.ScrollUp   =>
            moveSelection(-1)
            true
          case _                         => false
      }
      .fill

  /** Up/Down while the filter box holds focus. The table consumes them first whenever the table is focused, so this
    * only ever fires for the other case — the reader can keep steering the list while still typing.
    */
  private def handleUnclaimedKey(event: KeyEvent): Boolean =
    event.code match
      case KeyCode.Down =>
        moveSelection(1)
        true
      case KeyCode.Up   =>
        moveSelection(-1)
        true
      case _            => false

  // ---- rows ----

  /** A fresh `DataTable` per call: it is an immutable case class over the rows, and cheap. Only `tableState` persists.
    */
  private def buildTable(rows: Seq[ProcessInfo]): DataTable =
    DataTable(
      columns = Seq("   PID", "USER", " CPU%", " MEM%", "COMMAND"),
      rows = rows.map(cellsOf),
      widths = Seq(
        Constraint.Length(PidWidth + 2),
        Constraint.Length(10),
        Constraint.Length(NumberWidth + 2),
        Constraint.Length(NumberWidth + 2),
        Constraint.Fill(1),
      ),
    )

  /** Numbers are right-aligned to a fixed width, which buys two things at once.
    *
    * `DataTable` has no column alignment, so padding is the only way to make a numeric column readable. And it makes
    * the column sort correctly whichever branch the widget takes: it compares cells numerically when every one of them
    * parses as a number, and lexicographically otherwise — and for non-negative numbers padded to one common width and
    * one decimal place, those two orderings agree. Add a unit suffix here and that stops being true, which is why the
    * units live in the header.
    */
  private def cellsOf(process: ProcessInfo): Seq[String] =
    Seq(
      integer(process.pid, PidWidth),
      process.user,
      decimal(process.cpuPercent, NumberWidth),
      decimal(process.memPercent, NumberWidth),
      process.command,
    )

object ProcmonApp:

  private final case class Summary(count: Int, cpuPercent: Double, memPercent: Double)

  private val PidColumn     = 0
  private val UserColumn    = 1
  private val CpuColumn     = 2
  private val MemColumn     = 3
  private val CommandColumn = 4

  private val PidWidth    = 6
  private val NumberWidth = 5

  private val MaxRefreshSeconds = 10

  /** Fast enough that a keystroke never waits on the clock, and a whole number of ticks to the second. */
  private val TickRate       = 250.millis
  private val TicksPerSecond = (1.second / TickRate).toInt

  /** A curated subset: every binding is reachable through `ctrl+p`, but only the ones worth a permanent reminder belong
    * on a one-row status bar.
    */
  private val Hints = Seq(
    "↑/↓"       -> "select",
    "/"         -> "filter",
    "p/u/c/m/n" -> "sort",
    "r"         -> "refresh",
    "q"         -> "quit",
  )

  // Locale.ROOT rather than the ambient locale: `%.1f` writes "12,5" in much of Europe, which would both look wrong
  // and stop the column sorting as a number.
  private def decimal(value: Double, width: Int): String = String.format(Locale.ROOT, s"%$width.1f", value)

  private def integer(value: Int, width: Int): String = String.format(Locale.ROOT, s"%${width}d", value)

object Main:
  def main(args: Array[String]): Unit =
    ProcmonApp().run().left.foreach(error => println(s"failed to run: $error"))
