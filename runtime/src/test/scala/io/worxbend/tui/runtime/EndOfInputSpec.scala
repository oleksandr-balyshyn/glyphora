package io.worxbend.tui.runtime

import io.worxbend.tui.core.{Buffer, Event, Size}
import io.worxbend.tui.terminal.{Backend, BackendError}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.Duration

/** What the runner does when its input stream ends.
  *
  * A terminal application whose stdin reaches end of file — `app < script.txt` where the script ran out, a terminal
  * that was closed underneath the process — can never receive another key. Before this, the backend reported that as
  * "no event available", which is what a plain poll timeout also reports, so the loop went straight back to reading and
  * the process pinned a core until it was killed. Now the end of the stream is its own event and the loop stops.
  */
final class EndOfInputSpec extends AnyFunSuite:

  /** Delivers `before`, then reports end of input forever. Counts reads so a spinning loop is visible. */
  private final class EndingBackend(before: Event*) extends Backend:
    private val scripted                                                  = scala.collection.mutable.Queue.from(before)
    var reads                                                             = 0
    def size: Either[BackendError, Size]                                  = Right(Size(20, 3))
    def draw(buffer: Buffer): Either[BackendError, Unit]                  = Right(())
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
      // a real loop would spin here forever; the cap turns that into a finite, reportable failure
      if reads > 500 then Left(BackendError.NotInRawMode)
      else Right(Some(if scripted.isEmpty then Event.EndOfInput else scripted.dequeue()))
    def close(): Either[BackendError, Unit]                               = Right(())

  test("the loop exits cleanly when the input stream ends"):
    val backend = EndingBackend()
    val result  = TerminalRunner(backend, RunnerConfig(tickRate = None)).run(
      _ => (),
      (_, _) => EventOutcome.Ignored,
      _ => (),
    )
    assert(result == Right(()))
    assert(backend.reads == 1, "the runner kept reading after the stream ended")

  test("the application sees the end of input before the loop stops"):
    val seen    = scala.collection.mutable.ArrayBuffer.empty[Event]
    val backend = EndingBackend()
    val result  = TerminalRunner(backend, RunnerConfig(tickRate = None)).run(
      _ => (),
      (event, _) => { seen += event; EventOutcome.Ignored },
      _ => (),
    )
    assert(result == Right(()))
    assert(seen.toSeq == Seq(Event.EndOfInput))

  test("an application cannot keep the loop alive past the end of its input"):
    // Unlike Ctrl+C, this is not a request the app may decline: there is no input left to deliver, so a handler that
    // asks for a redraw gets its frame and the loop still stops.
    val backend = EndingBackend()
    val result  = TerminalRunner(backend, RunnerConfig(tickRate = None)).run(
      _ => (),
      (_, _) => EventOutcome.Redraw,
      _ => (),
    )
    assert(result == Right(()))
    assert(backend.reads == 1)
