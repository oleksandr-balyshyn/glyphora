package io.worxbend.tui.runtime

import io.worxbend.tui.core.{Buffer, Event, KeyCode, KeyEvent, Size}
import io.worxbend.tui.terminal.{Backend, BackendError, HeadlessBackend}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.Duration

/** How the default reporting of absorbed queued-task failures stays honest.
  *
  * Isolating a throwing continuation (issue #17) must not turn into a new silent-failure mode: an app whose
  * continuation throws on every tick for an hour has to report an hour's worth of failures, not one.
  */
final class RunnerTaskFailureReportingSpec extends AnyFunSuite:

  /** Delivers one key event, then fails the way a broken terminal does. */
  private final class FailAfterFirstKey extends Backend:
    private var reads                                                     = 0
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
      reads += 1
      if reads == 1 then Right(Some(Event.Key(KeyEvent.of(KeyCode.Char('x'))))) else Left(BackendError.NotInRawMode)
    def close(): Either[BackendError, Unit]                               = Right(())

  /** Runs `bodies` as queued render-thread work, triggered by the first key, quitting on the second. */
  private def runQueueing(backend: HeadlessBackend, bodies: Seq[() => Unit]): Either[RunnerError, Unit] =
    backend.postEvent(Event.Key(KeyEvent.of(KeyCode.Char('x'))))
    backend.postEvent(Event.Key(KeyEvent.of(KeyCode.Char('q'))))
    TerminalRunner(backend).run(
      _ => (),
      (event, handle) =>
        event match
          case Event.Key(KeyEvent(KeyCode.Char('x'), _)) =>
            bodies.foreach(body => RenderThread.runLater(body()))
            EventOutcome.Ignored
          case _                                         =>
            handle.quit()
            EventOutcome.Ignored
      ,
      _ => (),
    )

  test("every absorbed queued-task failure is counted, not just the first"):
    val backend = HeadlessBackend(Size(20, 3))
    val result  = runQueueing(backend, (1 to 3).map(i => () => throw RuntimeException(s"boom-$i")))
    result match
      case Left(RunnerError.QueuedTask(QueuedTaskFailures(error, count))) =>
        assert(error.getMessage == "boom-1")
        assert(count == 3, "later failures were dropped from the count")
        assert(
          error.getSuppressed.map(_.getMessage).toList == List("boom-2", "boom-3"),
          "later failures lost their stack traces entirely",
        )
      case other => fail(s"expected a queued-task failure, got $other")

  test("a flood of failures is counted exactly while the retained traces stay bounded"):
    val backend = HeadlessBackend(Size(20, 3))
    val result  = runQueueing(backend, (1 to 500).map(i => () => throw RuntimeException(s"boom-$i")))
    result match
      case Left(RunnerError.QueuedTask(QueuedTaskFailures(error, count))) =>
        assert(count == 500, "the report must not understate how often the continuation failed")
        assert(error.getSuppressed.length == 16, s"unbounded retention: ${error.getSuppressed.length} suppressed")
      case other => fail(s"expected a queued-task failure, got $other")

  test("one cached throwable rethrown on every tick is still counted"):
    // `addSuppressed(self)` throws IllegalArgumentException, and a memoised failure re-delivered each tick is exactly
    // the shape that hits it
    val backend = HeadlessBackend(Size(20, 3))
    val cached  = RuntimeException("same instance every time")
    val result  = runQueueing(backend, Seq.fill(4)(() => throw cached))
    result match
      case Left(RunnerError.QueuedTask(QueuedTaskFailures(error, count))) =>
        assert(error eq cached)
        assert(count == 4)
        assert(error.getSuppressed.isEmpty, "a throwable must not be attached to itself")
      case other => fail(s"expected a queued-task failure, got $other")

  test("a backend failure afterwards reports the terminal error without erasing the task failure"):
    val result = TerminalRunner(FailAfterFirstKey()).run(
      _ => (),
      (event, _) =>
        event match
          case Event.Key(KeyEvent(KeyCode.Char('x'), _)) =>
            RenderThread.runLater(throw RuntimeException("boom-1"))
            EventOutcome.Ignored
          case _                                         => EventOutcome.Ignored
      ,
      _ => (),
    )
    result match
      case Left(RunnerError.Backend(BackendError.NotInRawMode, Some(QueuedTaskFailures(error, count)))) =>
        assert(error.getMessage == "boom-1")
        assert(count == 1)
      case other                                                                                        =>
        fail(s"the queued-task failure vanished behind the backend error: $other")

  test("an installed handler still owns reporting, and the run reports no queued-task failure"):
    val backend                        = HeadlessBackend(Size(20, 3))
    val seen                           = scala.collection.mutable.ArrayBuffer.empty[String]
    val record: RenderTaskErrorHandler = error => { val _ = seen.append(error.getMessage) }
    backend.postEvent(Event.Key(KeyEvent.of(KeyCode.Char('x'))))
    backend.postEvent(Event.Key(KeyEvent.of(KeyCode.Char('q'))))
    val result                         = TerminalRunner(backend, RunnerConfig(onTaskError = Some(record))).run(
      _ => (),
      (event, handle) =>
        event match
          case Event.Key(KeyEvent(KeyCode.Char('x'), _)) =>
            RenderThread.runLater(throw RuntimeException("boom-1"))
            RenderThread.runLater(throw RuntimeException("boom-2"))
            EventOutcome.Ignored
          case _                                         =>
            handle.quit()
            EventOutcome.Ignored
      ,
      _ => (),
    )
    assert(result.isRight, s"an installed handler owns reporting, so the run should succeed: $result")
    assert(seen.toList == List("boom-1", "boom-2"))
