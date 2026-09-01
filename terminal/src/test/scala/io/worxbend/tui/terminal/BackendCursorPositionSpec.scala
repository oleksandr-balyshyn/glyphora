package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, Position, Size}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.Duration

/** The hardware-caret contract on [[Backend]]: `setCursorPosition` moves the terminal's own cursor and nothing else.
  *
  * [[JLine3Backend]] cannot be constructed without a controlling terminal (see [[JLine3BackendSpec]]), so the sequence
  * it writes is pinned in [[AnsiSequencesSpec]] via `moveTo` and the observable behaviour is pinned here on the two
  * backends a test can actually build: a bare `Backend` that overrides nothing, and [[HeadlessBackend]].
  */
final class BackendCursorPositionSpec extends AnyFunSuite:

  /** The smallest thing that is a `Backend`: it overrides only the abstract members, so every defaulted member — the
    * new `setCursorPosition` included — is the trait's own.
    */
  private final class BareBackend extends Backend:
    def size: Either[BackendError, Size]                                  = Right(Size(10, 3))
    def draw(buffer: Buffer): Either[BackendError, Unit]                  =
      val _ = buffer
      Right(())
    def enableRawMode(): Either[BackendError, Unit]                       = Right(())
    def disableRawMode(): Either[BackendError, Unit]                      = Right(())
    def enterAlternateScreen(): Either[BackendError, Unit]                = Right(())
    def leaveAlternateScreen(): Either[BackendError, Unit]                = Right(())
    def enableMouseCapture(): Either[BackendError, Unit]                  = Right(())
    def disableMouseCapture(): Either[BackendError, Unit]                 = Right(())
    def hideCursor(): Either[BackendError, Unit]                          = Right(())
    def showCursor(): Either[BackendError, Unit]                          = Right(())
    def readEvent(timeout: Duration): Either[BackendError, Option[Event]] =
      val _ = timeout
      Right(None)
    def close(): Either[BackendError, Unit]                               = Right(())

  test("setCursorPosition defaults to a successful no-op for a backend with no real cursor"):
    // the default is what keeps this addition source-compatible: a Backend written against 0.12.0 still compiles
    val backend: Backend = BareBackend()
    assert(backend.setCursorPosition(Position(3, 1)) == Right(()))

  test("the headless backend records where the caret was parked"):
    val backend = HeadlessBackend(Size(20, 5))
    assert(backend.cursorPosition.isEmpty)
    assert(backend.setCursorPosition(Position(4, 2)).isRight)
    assert(backend.cursorPosition.contains(Position(4, 2)))

  test("the last caret request of a frame is the one that stands"):
    // one terminal, one cursor: two widgets both asking must not leave the caret at the first one's cell
    val backend = HeadlessBackend(Size(20, 5))
    assert(backend.setCursorPosition(Position(1, 0)).isRight)
    assert(backend.setCursorPosition(Position(7, 3)).isRight)
    assert(backend.cursorPosition.contains(Position(7, 3)))
    assert(backend.cursorMoveCount == 2L)

  test("moving the caret does not make it visible"):
    // position and visibility are separate operations, so a frame that moves the caret while it is hidden — a
    // background repaint, say — cannot flash a cursor the application never asked to show
    val backend = HeadlessBackend(Size(20, 5))
    assert(backend.hideCursor().isRight)
    assert(backend.setCursorPosition(Position(2, 2)).isRight)
    assert(!backend.isCursorVisible)
    assert(backend.showCursor().isRight)
    assert(backend.isCursorVisible)
    assert(backend.cursorPosition.contains(Position(2, 2)))

  test("an out-of-range position is recorded rather than rejected"):
    // the device clamps; this backend must not invent a different answer, or a test would pass against a rule the
    // real terminal does not enforce
    val backend = HeadlessBackend(Size(20, 5))
    assert(backend.setCursorPosition(Position(999, -4)).isRight)
    assert(backend.cursorPosition.contains(Position(999, -4)))

  test("closing the backend forgets the caret along with every other terminal mode"):
    val backend = HeadlessBackend(Size(20, 5))
    assert(backend.setCursorPosition(Position(1, 1)).isRight)
    assert(backend.close().isRight)
    assert(backend.cursorPosition.isEmpty)
