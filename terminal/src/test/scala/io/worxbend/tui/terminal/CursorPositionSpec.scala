package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, Position, Size}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.Duration

/** Covers [[Backend.setCursorPosition]]: the physical-cursor placement a frame asks for after it has been flushed. */
final class CursorPositionSpec extends AnyFunSuite:

  test("the default implementation succeeds and does nothing"):
    // A backend with no addressable cursor must keep compiling and keep working; this is the contract that lets the
    // method be added to a published trait without breaking anyone's implementation.
    final class Bare extends Backend:
      def size: Either[BackendError, Size]                                  = Right(Size(1, 1))
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

    assert(Bare().setCursorPosition(Position(3, 4)) == Right(()))

  test("the headless backend records where a frame parked the cursor"):
    val backend = HeadlessBackend(Size(10, 4))
    assert(backend.cursorPosition.isEmpty)
    assert(backend.setCursorPosition(Position(6, 2)) == Right(()))
    assert(backend.cursorPosition.contains(Position(6, 2)))

  test("the most recent position is the one that is kept"):
    val backend = HeadlessBackend(Size(10, 4))
    val _       = backend.setCursorPosition(Position(1, 1))
    val _       = backend.setCursorPosition(Position(0, 3))
    assert(backend.cursorPosition.contains(Position(0, 3)))

  test("closing the headless backend forgets the position along with the rest of the terminal state"):
    val backend = HeadlessBackend(Size(10, 4))
    val _       = backend.setCursorPosition(Position(2, 2))
    val _       = backend.hideCursor()
    val _       = backend.close()
    assert(backend.cursorPosition.isEmpty)
    assert(backend.isCursorVisible)
