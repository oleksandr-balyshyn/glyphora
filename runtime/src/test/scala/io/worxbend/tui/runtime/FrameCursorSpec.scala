package io.worxbend.tui.runtime

import io.worxbend.tui.core.{Buffer, Event, KeyCode, KeyEvent, KeyModifiers, Position, Size}
import io.worxbend.tui.terminal.{Backend, BackendError, HeadlessBackend}
import io.worxbend.tui.testsupport.Pilot

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.Duration

/** Covers the hardware caret: a frame declares where the terminal's own cursor belongs and the runner honours it after
  * the flush.
  *
  * The two properties worth pinning are that the declaration is per frame — stop declaring it and the cursor goes back
  * into hiding — and that an unchanged declaration costs no escape sequences, because a cursor that is moved and shown
  * again on every tick visibly stutters on a terminal that blinks it.
  */
final class FrameCursorSpec extends AnyFunSuite:

  private def quitOnQ(event: Event, handle: RunnerHandle): EventOutcome =
    event match
      case Event.Key(KeyEvent(KeyCode.Char('q'), _)) =>
        handle.quit()
        EventOutcome.Ignored
      case _                                         => EventOutcome.Redraw

  test("a frame that declares a cursor position leaves the terminal cursor there and visible"):
    val backend = HeadlessBackend(Size(20, 3))
    val pilot   = Pilot.start(backend) {
      TerminalRunner(backend).run(
        _ => (),
        quitOnQ,
        frame =>
          frame.renderWidget((_, _) => (), frame.area)
          frame.setCursorPosition(Position(4, 2)),
      )
    }
    pilot.waitForIdle()
    assert(backend.cursorPosition.contains(Position(4, 2)))
    assert(backend.isCursorVisible)
    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())

  test("a frame that declares nothing leaves the cursor hidden"):
    val backend = HeadlessBackend(Size(20, 3))
    val pilot   = Pilot.start(backend) {
      TerminalRunner(backend).run(_ => (), quitOnQ, frame => frame.renderWidget((_, _) => (), frame.area))
    }
    pilot.waitForIdle()
    assert(backend.cursorPosition.isEmpty)
    assert(!backend.isCursorVisible)
    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())

  test("withdrawing the declaration hides the cursor again on the next frame"):
    val backend       = HeadlessBackend(Size(20, 3))
    @volatile var own = true
    val pilot         = Pilot.start(backend) {
      TerminalRunner(backend).run(
        _ => (),
        quitOnQ,
        frame =>
          frame.renderWidget((_, _) => (), frame.area)
          if own then frame.setCursorPosition(Position(1, 1)),
      )
    }
    pilot.waitForIdle()
    assert(backend.isCursorVisible)
    own = false
    pilot.pressKey(KeyCode.Char('x')).waitForIdle()
    assert(!backend.isCursorVisible)
    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())

  test("the last declaration in a frame wins"):
    val backend = HeadlessBackend(Size(20, 3))
    val pilot   = Pilot.start(backend) {
      TerminalRunner(backend).run(
        _ => (),
        quitOnQ,
        frame =>
          frame.setCursorPosition(Position(1, 1))
          frame.setCursorPosition(Position(7, 0)),
      )
    }
    pilot.waitForIdle()
    assert(backend.cursorPosition.contains(Position(7, 0)))
    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())

  test("clearCursorPosition withdraws a position declared earlier in the same frame"):
    val backend = HeadlessBackend(Size(20, 3))
    val pilot   = Pilot.start(backend) {
      TerminalRunner(backend).run(
        _ => (),
        quitOnQ,
        frame =>
          frame.setCursorPosition(Position(1, 1))
          frame.clearCursorPosition(),
      )
    }
    pilot.waitForIdle()
    assert(backend.cursorPosition.isEmpty)
    assert(!backend.isCursorVisible)
    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())

  test("an unchanged declaration emits no further cursor traffic"):
    // Driven synchronously rather than through `Pilot`: the events are queued before the loop starts and the last of
    // them quits, so the run is deterministic and the counted writes belong to a known number of frames.
    val inner   = HeadlessBackend(Size(20, 3))
    val backend = CountingBackend(inner)
    inner.postEvent(Event.Key(KeyEvent(KeyCode.Char('x'), KeyModifiers.None)))
    inner.postEvent(Event.Key(KeyEvent(KeyCode.Char('y'), KeyModifiers.None)))
    inner.postEvent(Event.Key(KeyEvent(KeyCode.Char('q'), KeyModifiers.None)))
    val result  = TerminalRunner(backend).run(
      _ => (),
      quitOnQ,
      frame =>
        frame.renderWidget((_, _) => (), frame.area)
        frame.setCursorPosition(Position(2, 1)),
    )
    assert(result == Right(()))
    assert(inner.drawCount >= 3, "an initial frame plus one per redrawing key press")
    assert(backend.cursorWrites == 1, "only the first frame moves the cursor; the rest ask for the same position")

/** A [[Backend]] that forwards everything to a [[HeadlessBackend]] and counts the cursor moves on the way through.
  *
  * `HeadlessBackend` records the latest cursor position but not how often it was written, and "how often" is exactly
  * what the de-duplication in the frame composer is about. It is `final`, so this counts by delegation rather than by
  * subclassing; nothing outside this suite needs the number.
  */
private final class CountingBackend(inner: HeadlessBackend) extends Backend:

  private var moves = 0

  /** How many times a frame moved the physical cursor since this backend was created. */
  def cursorWrites: Int = moves

  override def setCursorPosition(position: Position): Either[BackendError, Unit] =
    moves += 1
    inner.setCursorPosition(position)

  def size: Either[BackendError, Size]                                  = inner.size
  def draw(buffer: Buffer): Either[BackendError, Unit]                  = inner.draw(buffer)
  def enableRawMode(): Either[BackendError, Unit]                       = inner.enableRawMode()
  def disableRawMode(): Either[BackendError, Unit]                      = inner.disableRawMode()
  def enterAlternateScreen(): Either[BackendError, Unit]                = inner.enterAlternateScreen()
  def leaveAlternateScreen(): Either[BackendError, Unit]                = inner.leaveAlternateScreen()
  def enableMouseCapture(): Either[BackendError, Unit]                  = inner.enableMouseCapture()
  def disableMouseCapture(): Either[BackendError, Unit]                 = inner.disableMouseCapture()
  def hideCursor(): Either[BackendError, Unit]                          = inner.hideCursor()
  def showCursor(): Either[BackendError, Unit]                          = inner.showCursor()
  def readEvent(timeout: Duration): Either[BackendError, Option[Event]] = inner.readEvent(timeout)
  def close(): Either[BackendError, Unit]                               = inner.close()
