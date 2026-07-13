package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, KeyCode, KeyEvent, Rect, Size, Style}

import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable
import scala.concurrent.duration.Duration

/** Proves the `Backend` trait has no JLine leakage: this file implements a complete fake backend without importing a
  * single JLine type.
  */
final class FakeBackendSpec extends AnyFunSuite:

  private final class FakeBackend(events: Event*) extends Backend:
    private val queue = mutable.Queue[Event](events*)
    val drawn         = mutable.Buffer[Buffer]()
    var rawMode       = false

    def size: Either[BackendError, Size]                                  = Right(Size(20, 5))
    def draw(buffer: Buffer): Either[BackendError, Unit]                  =
      drawn += buffer.snapshot
      Right(())
    def enableRawMode(): Either[BackendError, Unit]                       =
      rawMode = true
      Right(())
    def disableRawMode(): Either[BackendError, Unit]                      =
      if !rawMode then Left(BackendError.NotInRawMode)
      else
        rawMode = false
        Right(())
    def enterAlternateScreen(): Either[BackendError, Unit]                = Right(())
    def leaveAlternateScreen(): Either[BackendError, Unit]                = Right(())
    def enableMouseCapture(): Either[BackendError, Unit]                  = Right(())
    def disableMouseCapture(): Either[BackendError, Unit]                 = Right(())
    def hideCursor(): Either[BackendError, Unit]                          = Right(())
    def showCursor(): Either[BackendError, Unit]                          = Right(())
    def readEvent(timeout: Duration): Either[BackendError, Option[Event]] =
      Right(if queue.isEmpty then None else Some(queue.dequeue()))
    def close(): Unit                                                     = ()

  test("a backend can be implemented purely in terms of core types"):
    val backend: Backend = FakeBackend(Event.Key(KeyEvent.of(KeyCode.Enter)))
    assert(backend.size == Right(Size(20, 5)))
    assert(backend.readEvent(Duration.Zero) == Right(Some(Event.Key(KeyEvent.of(KeyCode.Enter)))))
    assert(backend.readEvent(Duration.Zero) == Right(None))

  test("drawing hands the backend the frame buffer"):
    val backend = FakeBackend()
    val buffer  = Buffer(Rect(0, 0, 20, 5))
    buffer.setString(0, 0, "hi", Style.Default)
    assert(backend.draw(buffer).isRight)
    assert(backend.drawn.head.get(0, 0).symbol == "h")

  test("disabling raw mode before enabling it is the modeled NotInRawMode error"):
    val backend = FakeBackend()
    assert(backend.disableRawMode() == Left(BackendError.NotInRawMode))
    assert(backend.enableRawMode().isRight)
    assert(backend.disableRawMode().isRight)
