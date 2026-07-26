package io.worxbend.tui.runtime

import io.worxbend.tui.core.{Buffer, Event, KeyCode, KeyEvent, Size}
import io.worxbend.tui.terminal.{Backend, BackendError, HeadlessBackend}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.{Duration, DurationInt}

/** Regressions for event-loop lifecycle defects found by the terminal audit. */
final class RunnerLifecycleSpec extends AnyFunSuite:

  /** Records every timeout the runner asks for and never delivers an event. */
  private final class TimeoutSpy(reported: Size, stopAfter: Int) extends Backend:
    val asked                                       = scala.collection.mutable.ArrayBuffer.empty[Duration]
    def size: Either[BackendError, Size]            = Right(reported)
    def draw(b: Buffer): Either[BackendError, Unit] = Right(())
    def enableRawMode()                             = Right(())
    def disableRawMode()                            = Right(())
    def enterAlternateScreen()                      = Right(())
    def leaveAlternateScreen()                      = Right(())
    def enableMouseCapture()                        = Right(())
    def disableMouseCapture()                       = Right(())
    def hideCursor()                                = Right(())
    def showCursor()                                = Right(())
    def readEvent(timeout: Duration): Either[BackendError, Option[Event]] =
      asked += timeout
      if asked.size > stopAfter then Left(BackendError.NotInRawMode) else Right(None)
    def close(): Unit                                                     = ()

  test("a tick rate never produces a zero poll timeout, even when frames overrun the budget"):
    // JLine treats a non-positive timeout as "block until a key arrives", so a zero here wedges the loop: ticks,
    // animation, toast ageing and async results all stop until the user happens to press something
    val spy    = TimeoutSpy(Size(80, 24), stopAfter = 40)
    var now    = 0L
    val runner = TerminalRunner(
      spy,
      RunnerConfig(tickRate = Some(16.millis)),
      nanoTime = () => { now += 30_000_000L; now }, // every frame overruns the 16ms budget
    )
    val _      = runner.run((_, _) => false, _ => ())
    assert(spy.asked.nonEmpty)
    assert(spy.asked.forall(t => !t.isFinite || t.toNanos > 0), s"zero timeout requested: ${spy.asked.take(5)}")

  test("the poll timeout still tracks the tick rate when frames are cheap"):
    val spy    = TimeoutSpy(Size(80, 24), stopAfter = 5)
    val runner = TerminalRunner(spy, RunnerConfig(tickRate = Some(50.millis)), nanoTime = () => 0L)
    val _      = runner.run((_, _) => false, _ => ())
    assert(spy.asked.head == 50.millis)

  test("a backend must reject a zero timeout rather than silently polling once"):
    val backend = HeadlessBackend(Size(10, 3))
    assertThrows[IllegalArgumentException](backend.readEvent(Duration.Zero))

  test("an unconsumed interrupt quits the runner through its normal teardown"):
    val backend = HeadlessBackend(Size(20, 3))
    backend.postEvent(Event.Interrupt)
    val result  = TerminalRunner(backend).run((_, _) => false, _ => ())
    assert(result.isRight)
    assert(!backend.isRawMode, "raw mode was not restored")
    assert(!backend.isAlternateScreen, "the alternate screen was not left")
    assert(backend.isCursorVisible, "the cursor was left hidden")

  test("an app may consume an interrupt and keep running"):
    val backend = HeadlessBackend(Size(20, 3))
    var seen    = 0
    backend.postEvent(Event.Interrupt)
    backend.postEvent(Event.Key(KeyEvent.of(KeyCode.Char('q'))))
    val result  = TerminalRunner(backend).run(
      (event, handle) =>
        event match
          case Event.Interrupt =>
            seen += 1
            true // consumed: stay running
          case _ =>
            handle.quit()
            false
      ,
      _ => (),
    )
    assert(result.isRight)
    assert(seen == 1)

  test("queued render-thread work wakes the backend instead of waiting out the poll"):
    val backend = HeadlessBackend(Size(20, 3))
    var ran     = false
    backend.postEvent(Event.Key(KeyEvent.of(KeyCode.Char('x'))))
    backend.postEvent(Event.Key(KeyEvent.of(KeyCode.Char('q'))))
    val result  = TerminalRunner(backend).run(
      (event, handle) =>
        event match
          case Event.Key(KeyEvent(KeyCode.Char('x'), _)) =>
            // queue from a foreign thread, the shape `Async` uses to deliver an IO result
            val worker = Thread(() => RenderThread.runLater { ran = true })
            worker.start()
            worker.join()
            false
          case _                                         =>
            handle.quit()
            false
      ,
      _ => (),
    )
    assert(result.isRight)
    assert(backend.wakeCount > 0, "runLater did not ask the backend to wake")
    assert(ran, "queued work never ran")
