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
    def close(): Either[BackendError, Unit]                               = Right(())

  /** Delivers a scripted event stream and then fails to hand the terminal back, so the two teardown paths can be told
    * apart. `HeadlessBackend` is `final`, so this is a fake of its own rather than a subclass.
    */
  private final class UnrestorableBackend(events: Event*) extends Backend:
    private val scripted                                                  = scala.collection.mutable.Queue.from(events)
    def size: Either[BackendError, Size]                                  = Right(Size(20, 3))
    def draw(b: Buffer): Either[BackendError, Unit]                       = Right(())
    def enableRawMode()                                                   = Right(())
    def disableRawMode()                                                  = Right(())
    def enterAlternateScreen()                                            = Right(())
    def leaveAlternateScreen()                                            = Right(())
    def enableMouseCapture()                                              = Right(())
    def disableMouseCapture()                                             = Right(())
    def hideCursor()                                                      = Right(())
    def showCursor()                                                      = Right(())
    def readEvent(timeout: Duration): Either[BackendError, Option[Event]] =
      Right(if scripted.isEmpty then None else Some(scripted.dequeue()))
    def close(): Either[BackendError, Unit] = Left(BackendError.UnsupportedTerminal("cannot restore"))

  test("a clean run that cannot restore the terminal reports the failure instead of exiting silently"):
    // the most user-visible failure the library has: the shell comes back raw, on the alternate screen, or with no
    // cursor. It used to be impossible for `run` to learn about it at all.
    val backend = UnrestorableBackend(Event.Key(KeyEvent.of(KeyCode.Char('q'))))
    val result  = TerminalRunner(backend).run(
      _ => (),
      (_, handle) => { handle.quit(); EventOutcome.Ignored },
      _ => (),
    )
    assert(result == Left(RunnerError.Backend(BackendError.UnsupportedTerminal("cannot restore"))))
    assert(result.left.exists(_.message == "terminal not supported: cannot restore"))

  test("a run that already failed keeps its own error rather than the restore failure"):
    val backend = UnrestorableBackend(
      Event.Key(KeyEvent.of(KeyCode.Char('x'))),
      Event.Key(KeyEvent.of(KeyCode.Char('q'))),
    )
    val result  = TerminalRunner(backend).run(
      _ => (),
      (event, handle) =>
        event match
          case Event.Key(KeyEvent(KeyCode.Char('x'), _)) =>
            RenderThread.runLater(throw RuntimeException("continuation boom"))
            EventOutcome.Ignored
          case _                                         =>
            handle.quit()
            EventOutcome.Ignored
      ,
      _ => (),
    )
    result match
      case Left(RunnerError.QueuedTask(QueuedTaskFailures(error, _))) => assert(error.getMessage == "continuation boom")
      case other => fail(s"expected the loop's own failure, got $other")

  test("work queued as the app quits still runs, and work queued afterwards is dropped"):
    val backend                                   = HeadlessBackend(Size(20, 3))
    var ranAtQuit                                 = false
    var ranAfterExit                              = false
    var captured: Option[RenderThread.RenderLoop] = None
    backend.postEvent(Event.Key(KeyEvent.of(KeyCode.Char('q'))))
    val result                                    = TerminalRunner(backend).run(
      _ => (),
      (_, handle) =>
        captured = Some(RenderThread.capture())
        handle.quit()
        // queued during the loop's final iteration: this run still owes it a drain
        RenderThread.runLater { ranAtQuit = true }
        EventOutcome.Ignored
      ,
      _ => (),
    )
    assert(result.isRight)
    assert(ranAtQuit, "work queued during the final iteration was discarded")

    // the shape an uncancelled `Async.every` has after its runner exits: the timer still holds the loop
    val loop = captured.getOrElse(fail("the loop was never captured"))
    loop.enqueue(() => ranAfterExit = true)
    RenderThread.drainPending(loop)
    assert(!ranAfterExit, "a retired loop must not accept, or run, new work")

  test("onStart runs on the render thread, once, before the first frame"):
    // `Async.every` and `Timers` capture the loop of the thread that calls them, so an app arming background work
    // anywhere but here attaches it to the wrong loop — or to none at all.
    val backend               = HeadlessBackend(Size(20, 3))
    var starts                = 0
    var drawsWhenStarted      = -1L
    var startedOnRenderThread = false
    backend.postEvent(Event.Key(KeyEvent.of(KeyCode.Char('q'))))
    val result                = TerminalRunner(backend).run(
      _ =>
        starts += 1
        drawsWhenStarted = backend.drawCount
        startedOnRenderThread = RenderThread.isRenderThread
      ,
      (_, handle) => { handle.quit(); EventOutcome.Ignored },
      _ => (),
    )
    assert(result.isRight)
    assert(starts == 1)
    assert(startedOnRenderThread, "onStart must run on the render thread")
    assert(drawsWhenStarted == 0L, "onStart ran after a frame had already been drawn")

  test("quit from onStart exits without drawing anything, and still restores the terminal"):
    // the start-up check that decides not to run at all: no config file, not a TTY, wrong arguments
    val backend = HeadlessBackend(Size(20, 3))
    val result  = TerminalRunner(backend).run(handle => handle.quit(), (_, _) => EventOutcome.Ignored, _ => ())
    assert(result.isRight)
    assert(backend.drawCount == 0L, "a run that quit before its first frame drew one anyway")
    assert(!backend.isRawMode, "raw mode was not restored")
    assert(!backend.isAlternateScreen, "the alternate screen was not left")
    assert(backend.isCursorVisible, "the cursor was left hidden")

  test("requestRedraw schedules a frame for state the runner cannot see"):
    // the defect this exists for: caller-owned widget state (a `ListState`, a table's rows) mutated on the render
    // thread by a background continuation changes the next frame and nothing schedules one, so the screen keeps the
    // old contents until the user happens to press a key.
    val backend  = HeadlessBackend(Size(20, 3))
    val rendered = scala.collection.mutable.ArrayBuffer.empty[String]
    var label    = "before"
    backend.postEvent(Event.Key(KeyEvent.of(KeyCode.Char('x'))))
    backend.postEvent(Event.Key(KeyEvent.of(KeyCode.Char('q'))))
    val result   = TerminalRunner(backend).run(
      _ => (),
      (event, handle) =>
        event match
          case Event.Key(KeyEvent(KeyCode.Char('x'), _)) =>
            label = "after"
            handle.requestRedraw()
            // deliberately Ignored: the handler claims nothing changed, exactly as an unrelated event would while a
            // continuation elsewhere did the mutating
            EventOutcome.Ignored
          case _                                         =>
            handle.quit()
            EventOutcome.Ignored
      ,
      _ => { val _ = rendered.append(label) },
    )
    assert(result.isRight)
    assert(rendered.toList == List("before", "after"), s"frames drawn: $rendered")

  test("a throwing event handler ends the run as RunnerError.Handler with the terminal restored"):
    // it used to unwind straight out of `run`, past the restore, handing the user a shell still in raw mode on the
    // alternate screen with no cursor
    val backend = HeadlessBackend(Size(20, 3))
    backend.postEvent(Event.Key(KeyEvent.of(KeyCode.Char('x'))))
    val result  = TerminalRunner(backend).run(
      _ => (),
      (_, _) => throw RuntimeException("handler boom"),
      _ => (),
    )
    result match
      case Left(RunnerError.Handler(error)) => assert(error.getMessage == "handler boom")
      case other                            => fail(s"expected a handler failure, got $other")
    assert(result.left.exists(_.message.contains("handler boom")))
    assert(!backend.isRawMode, "raw mode was not restored")
    assert(!backend.isAlternateScreen, "the alternate screen was not left")
    assert(backend.isCursorVisible, "the cursor was left hidden")

  test("a throwing onStart ends the run the same way, before any frame"):
    val backend = HeadlessBackend(Size(20, 3))
    val result  = TerminalRunner(backend).run(
      _ => throw IllegalStateException("start boom"),
      (_, _) => EventOutcome.Ignored,
      _ => (),
    )
    result match
      case Left(RunnerError.Handler(error)) => assert(error.getMessage == "start boom")
      case other                            => fail(s"expected a handler failure, got $other")
    assert(backend.drawCount == 0L)
    assert(!backend.isRawMode, "raw mode was not restored")

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
    val _      = runner.run(_ => (), (_, _) => EventOutcome.Ignored, _ => ())
    assert(spy.asked.nonEmpty)
    assert(spy.asked.forall(t => !t.isFinite || t.toNanos > 0), s"zero timeout requested: ${spy.asked.take(5)}")

  test("the poll timeout still tracks the tick rate when frames are cheap"):
    val spy    = TimeoutSpy(Size(80, 24), stopAfter = 5)
    val runner = TerminalRunner(spy, RunnerConfig(tickRate = Some(50.millis)), nanoTime = () => 0L)
    val _      = runner.run(_ => (), (_, _) => EventOutcome.Ignored, _ => ())
    assert(spy.asked.head == 50.millis)

  test("a backend must reject a zero timeout rather than silently polling once"):
    val backend = HeadlessBackend(Size(10, 3))
    assertThrows[IllegalArgumentException](backend.readEvent(Duration.Zero))

  test("an unconsumed interrupt quits the runner through its normal teardown"):
    val backend = HeadlessBackend(Size(20, 3))
    backend.postEvent(Event.Interrupt)
    val result  = TerminalRunner(backend).run(_ => (), (_, _) => EventOutcome.Ignored, _ => ())
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
      _ => (),
      (event, handle) =>
        event match
          case Event.Interrupt =>
            seen += 1
            EventOutcome.Redraw // consumed: stay running, and repaint whatever the app put on screen instead
          case _ =>
            handle.quit()
            EventOutcome.Ignored
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
      _ => (),
      (event, handle) =>
        event match
          case Event.Key(KeyEvent(KeyCode.Char('x'), _)) =>
            // queue from a foreign thread, the shape `Async` uses to deliver an IO result
            val worker = Thread(() => RenderThread.runLater { ran = true })
            worker.start()
            worker.join()
            EventOutcome.Ignored
          case _                                         =>
            handle.quit()
            EventOutcome.Ignored
      ,
      _ => (),
    )
    assert(result.isRight)
    assert(backend.wakeCount > 0, "runLater did not ask the backend to wake")
    assert(ran, "queued work never ran")

  test("a throwing continuation does not tear the runner down and is reported after it exits"):
    // the regression: one `.get` on a failed request used to unwind out of `run`, taking the app and every other
    // queued body with it
    val backend = HeadlessBackend(Size(20, 3))
    var ran     = false
    backend.postEvent(Event.Key(KeyEvent.of(KeyCode.Char('x'))))
    backend.postEvent(Event.Key(KeyEvent.of(KeyCode.Char('q'))))
    val result  = TerminalRunner(backend).run(
      _ => (),
      (event, handle) =>
        event match
          case Event.Key(KeyEvent(KeyCode.Char('x'), _)) =>
            RenderThread.runLater(throw RuntimeException("continuation boom"))
            RenderThread.runLater { ran = true }
            EventOutcome.Ignored
          case _                                         =>
            handle.quit()
            EventOutcome.Ignored
      ,
      _ => (),
    )
    assert(ran, "the body queued behind the throwing one never ran")
    result match
      case Left(RunnerError.QueuedTask(QueuedTaskFailures(error, count))) =>
        assert(error.getMessage == "continuation boom")
        assert(count == 1)
      case other => fail(s"expected a recorded queued-task failure, got $other")
    assert(!backend.isRawMode, "raw mode was not restored")
    assert(!backend.isAlternateScreen, "the alternate screen was not left")
    assert(backend.isCursorVisible, "the cursor was left hidden")

  test("an installed onTaskError handler takes over reporting and the run still succeeds"):
    val backend                        = HeadlessBackend(Size(20, 3))
    val seen                           = scala.collection.mutable.ArrayBuffer.empty[Throwable]
    val record: RenderTaskErrorHandler = error => { val _ = seen.append(error) }
    backend.postEvent(Event.Key(KeyEvent.of(KeyCode.Char('x'))))
    backend.postEvent(Event.Key(KeyEvent.of(KeyCode.Char('q'))))
    val result                         = TerminalRunner(backend, RunnerConfig(onTaskError = Some(record))).run(
      _ => (),
      (event, handle) =>
        event match
          case Event.Key(KeyEvent(KeyCode.Char('x'), _)) =>
            RenderThread.runLater(throw RuntimeException("continuation boom"))
            EventOutcome.Ignored
          case _                                         =>
            handle.quit()
            EventOutcome.Ignored
      ,
      _ => (),
    )
    assert(result.isRight, s"an installed handler owns reporting, so the run should succeed: $result")
    assert(seen.map(_.getMessage).toList == List("continuation boom"))
