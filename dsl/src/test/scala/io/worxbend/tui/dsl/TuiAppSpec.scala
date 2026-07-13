package io.worxbend.tui.dsl

import io.worxbend.tui.core.Size
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot

import org.scalatest.funsuite.AnyFunSuite

final class TuiAppSpec extends AnyFunSuite:

  /** A miniature counter app exercising the full reactive path: Signal → view → key handler → redraw. */
  private final class CounterApp extends TuiApp:
    val count                              = Signal(0)
    def view(using ReactiveScope): Element =
      panel("Counter")(
        text(s"count: ${count.get}")
      ).onKeyEvent {
        case KeyEvent(KeyCode.Char('+'), _) =>
          count.update(_ + 1)
          true
        case KeyEvent(KeyCode.Char('q'), _) =>
          quit()
          true
        case _                              => false
      }

  test("a TuiApp renders its view and reacts to signal updates from key handlers"):
    val backend = HeadlessBackend(Size(20, 4))
    val app     = CounterApp()
    val pilot   = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    assert(pilot.screenLines(1).startsWith("│count: 0"))
    pilot.pressKey(KeyCode.Char('+')).pressKey(KeyCode.Char('+')).waitForIdle()
    assert(pilot.screenLines(1).startsWith("│count: 2"))
    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())

  test("an unconsumed Ctrl+C quits by default"):
    val backend = HeadlessBackend(Size(20, 4))
    val app     = CounterApp()
    val pilot   = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    pilot.pressKey(KeyCode.Char('c'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())

  test("an event that touches no signal the view read schedules no redraw"):
    val backend     = HeadlessBackend(Size(20, 4))
    val app         = CounterApp()
    val pilot       = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    val drawsBefore = backend.drawCount
    pilot.pressKey(KeyCode.Char('x')).waitForIdle()
    assert(backend.drawCount == drawsBefore)
    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())
