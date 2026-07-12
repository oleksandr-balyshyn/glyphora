package io.worxbend.tui.runtime

import io.worxbend.tui.core.{Color, Event, KeyCode, KeyEvent, Size, Style}
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.DurationInt

final class TerminalRunnerSpec extends AnyFunSuite:

  /** The immediate-mode hello-world render function — the same drawing the real example performs, asserted here on
    * `Buffer` contents instead of a live terminal (PLAN.md §11, step 4).
    */
  private def helloWorldRender(frame: Frame): Unit =
    frame.renderWidget(
      (area, buffer) =>
        buffer.setString(area.x + 2, area.y + 1, "Hello from glyphora!", Style.Default.bold.withFg(Color.Cyan))
        buffer.setString(area.x + 2, area.y + 3, "Press 'q' to quit", Style.Default.dim)
      ,
      frame.area,
    )

  private def quitOnQ(event: Event, handle: RunnerHandle): Boolean =
    event match
      case Event.Key(KeyEvent(KeyCode.Char('q'), _)) =>
        handle.quit()
        false
      case _ => true

  test("the runner renders an initial frame before any event arrives"):
    val backend = HeadlessBackend(Size(30, 5))
    val pilot = Pilot.start(backend)(TerminalRunner(backend).run(quitOnQ, helloWorldRender))
    pilot.waitForIdle()
    assert(pilot.screenLines(1) == "  Hello from glyphora!")
    assert(pilot.screenLines(3) == "  Press 'q' to quit")
    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())

  test("the runner sets up and tears down the terminal around the loop"):
    val backend = HeadlessBackend(Size(30, 5))
    val pilot = Pilot.start(backend)(TerminalRunner(backend).run(quitOnQ, helloWorldRender))
    pilot.waitForIdle()
    assert(backend.isRawMode)
    assert(backend.isAlternateScreen)
    assert(!backend.isCursorVisible)
    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())

  test("an event handler returning true triggers a redraw with updated state"):
    val backend = HeadlessBackend(Size(20, 3))
    var count = 0
    val pilot = Pilot.start(backend) {
      val _ = TerminalRunner(backend).run(
        (event, handle) =>
          event match
            case Event.Key(KeyEvent(KeyCode.Char('q'), _)) =>
              handle.quit()
              false
            case Event.Key(KeyEvent(KeyCode.Char('+'), _)) =>
              count += 1
              true
            case _ => false
        ,
        frame =>
          frame.renderWidget(
            (area, buffer) => buffer.setString(area.x, area.y, s"count=$count", Style.Default),
            frame.area,
          ),
      )
    }
    pilot.waitForIdle()
    assert(pilot.screenLines.head == "count=0")
    pilot.pressKey(KeyCode.Char('+')).pressKey(KeyCode.Char('+')).waitForIdle()
    assert(pilot.screenLines.head == "count=2")
    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())

  test("a resize event recreates the frame at the new size"):
    val backend = HeadlessBackend(Size(20, 3))
    var lastArea = Size(0, 0)
    val pilot = Pilot.start(backend) {
      val _ = TerminalRunner(backend).run(
        quitOnQ,
        frame =>
          lastArea = Size(frame.area.width, frame.area.height)
          frame.renderWidget((_, _) => (), frame.area),
      )
    }
    pilot.waitForIdle()
    assert(lastArea == Size(20, 3))
    pilot.resize(40, 10).waitForIdle()
    assert(lastArea == Size(40, 10))
    assert(pilot.backend.lastDrawn.exists(_.area.width == 40))
    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())

  test("a configured tick rate delivers Tick events without input"):
    val backend = HeadlessBackend(Size(10, 2))
    @volatile var ticks = 0
    val pilot = Pilot.start(backend) {
      val _ = TerminalRunner(backend, RunnerConfig(tickRate = Some(10.millis))).run(
        (event, handle) =>
          event match
            case Event.Tick =>
              ticks += 1
              if ticks >= 3 then handle.quit()
              true
            case _ => false
        ,
        frame => frame.renderWidget((_, _) => (), frame.area),
      )
    }
    assert(pilot.awaitTermination(2.seconds))
    assert(ticks >= 3)

  test("the render thread is registered for the duration of the loop"):
    val backend = HeadlessBackend(Size(10, 2))
    @volatile var wasRenderThread = false
    val pilot = Pilot.start(backend) {
      val _ = TerminalRunner(backend).run(
        (event, handle) =>
          wasRenderThread = RenderThread.isRenderThread
          quitOnQ(event, handle)
        ,
        frame => frame.renderWidget((_, _) => (), frame.area),
      )
    }
    pilot.pressKey(KeyCode.Char('x')).waitForIdle()
    assert(wasRenderThread)
    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())
