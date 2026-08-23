package io.worxbend.tui.examples.procmon

import io.worxbend.tui.core.Size
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot
import io.worxbend.tui.widgets.{ColumnSort, SortDirection}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.{DurationInt, FiniteDuration}

final class ProcmonAppSpec extends AnyFunSuite:

  /** Polls until `predicate` holds.
    *
    * `Pilot.waitForIdle` proves the posted event queue drained; it says nothing about a sample that a tick started on
    * an `Async` worker and that lands on a later render-thread drain. Polling rather than sleeping a fixed time also
    * survives a parallel test run starving the tick thread for a while.
    */
  private def waitUntil(timeout: FiniteDuration = 10.seconds)(predicate: => Boolean): Unit =
    val deadline = System.nanoTime() + timeout.toNanos
    while !predicate && System.nanoTime() < deadline do Thread.sleep(20)

  /** A started app with its first sample already on screen. The synthetic source keeps this offline and repeatable. */
  private def startedApp(): (ProcmonApp, Pilot) =
    val backend = HeadlessBackend(Size(96, 24))
    val app     = ProcmonApp(SyntheticProcessSource())
    val pilot   = Pilot.start(backend) { app.runWith(backend) }
    pilot.waitForIdle()
    waitUntil()(app.sampleCount.peek > 0)
    pilot.waitForIdle()
    (app, pilot)

  test("the summary header, the process table and the key hints all render"):
    val (app, pilot) = startedApp()
    val screen       = pilot.screenText
    assert(screen.contains("procmon"))
    assert(screen.contains("48 processes"), "the header counts what the source returned")
    assert(screen.contains("source synthetic"))
    assert(screen.contains("CPU"))
    assert(screen.contains("MEM"))
    assert(screen.contains("PID"))
    assert(screen.contains("COMMAND"))
    assert(screen.contains("q quit"), "the status bar is built from the declared bindings' hints")
    // the default sort is CPU descending, the way `top` starts
    assert(app.tableState.sort.contains(ColumnSort(2, SortDirection.Descending)))
    pilot.press("q")
    assert(pilot.awaitTermination())

  test("sorting by a column once orders it, and a second press reverses it"):
    val (app, pilot) = startedApp()
    val pids         = app.processes.peek.map(_.pid)

    pilot.press("p").waitForIdle()
    assert(app.tableState.sort.contains(ColumnSort(0, SortDirection.Ascending)))
    assert(app.visibleProcessIds.head == pids.min)

    pilot.press("p").waitForIdle()
    assert(app.tableState.sort.contains(ColumnSort(0, SortDirection.Descending)))
    assert(app.visibleProcessIds.head == pids.max)

    pilot.press("q")
    assert(pilot.awaitTermination())

  test("the filter box narrows the table, Enter keeps the filter and Esc clears it"):
    val (app, pilot) = startedApp()
    assert(app.visibleProcessIds.sizeIs == 48)

    pilot.press("/").waitForIdle()
    pilot.typeText("nginx").waitForIdle()
    // the filter box owns the keyboard while it is open: 'n' typed into it must not also fire the sort-by-command
    // binding, because the focused element consumes the key before the app's bindings are consulted
    assert(app.tableState.sort.map(_.column).contains(2))
    assert(app.filterInput.value == "nginx")
    assert(app.visibleProcessIds.sizeIs == 3, "three of the forty-eight synthetic processes run nginx")
    assert(pilot.screenText.contains("nginx"))

    pilot.press("enter").waitForIdle()
    assert(app.visibleProcessIds.sizeIs == 3, "Enter commits the filter and closes the box")

    pilot.press("/").waitForIdle()
    pilot.press("esc").waitForIdle()
    assert(app.filterInput.value.isEmpty)
    assert(app.visibleProcessIds.sizeIs == 48, "Esc cancels the filter")

    pilot.press("q")
    assert(pilot.awaitTermination())

  test("the selection follows its process across a re-sort and across a refresh"):
    val (app, pilot) = startedApp()
    pilot.press("down", "down", "down").waitForIdle()

    val pinned = app.selectedProcessId
    assert(pinned.isDefined)
    assert(app.tableState.selected.contains(2))

    // sorting by pid moves nearly every row; the highlight must land on the same process at its new index
    pilot.press("p").waitForIdle()
    assert(app.selectedProcessId == pinned)
    val afterSort = app.tableState.selected
    assert(afterSort.isDefined)
    assert(app.visibleProcessIds(afterSort.get) == pinned.get)

    // and a refresh replaces every row's numbers underneath it without moving the highlight to another process
    val samplesBefore = app.sampleCount.peek
    pilot.press("r")
    waitUntil()(app.sampleCount.peek > samplesBefore)
    pilot.waitForIdle()
    assert(app.selectedProcessId == pinned)
    val afterRefresh  = app.tableState.selected
    assert(afterRefresh.isDefined)
    assert(app.visibleProcessIds(afterRefresh.get) == pinned.get)

    pilot.press("q")
    assert(pilot.awaitTermination())

  test("ticks refresh the table with no input at all"):
    val (app, pilot) = startedApp()
    pilot.press("-").waitForIdle() // shortest interval, so the test does not wait two seconds
    assert(app.refreshSeconds.peek == 1)
    val samplesBefore = app.sampleCount.peek
    val drawsBefore   = pilot.backend.drawCount
    waitUntil()(app.sampleCount.peek > samplesBefore && pilot.backend.drawCount > drawsBefore)
    assert(app.sampleCount.peek > samplesBefore)
    assert(pilot.backend.drawCount > drawsBefore, "a new sample repaints without anyone pressing a key")
    pilot.press("q")
    assert(pilot.awaitTermination())

  /** Exercises the real detection path — on a machine with `ps` this samples live processes, and everywhere else it
    * proves the synthetic fallback takes over rather than leaving the app with an empty table.
    */
  test("the detected source always produces rows, live or synthetic"):
    val source = ProcessSource.detect()
    assert(Seq("ps", "synthetic").contains(source.name))
    assert(source.sample().nonEmpty)
