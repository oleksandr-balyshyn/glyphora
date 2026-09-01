package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, Size}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.Duration

/** Pins `Backend.requestSize` — the one direction of the size relationship that did not exist.
  *
  * The library has always been able to *observe* a size (`Backend.size`, and `Event.Resize` from SIGWINCH). Asking for
  * one is a different thing with a much weaker guarantee, and these tests are mostly about keeping that weakness
  * visible: the sequence, the argument order that is easy to transpose, and the fact that a request is recorded
  * separately from whatever the terminal did about it.
  */
final class RequestSizeSpec extends AnyFunSuite:

  private val Esc = "\u001b"

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

  test("the sequence names rows before columns, the reverse of how a Size reads"):
    // the whole reason this lives in a named helper: a transposed request produces a terminal of the wrong shape
    // rather than an error, and nothing downstream would ever report it
    assert(AnsiSequences.resizeWindow(Size(120, 40)) == s"$Esc[8;40;120t")
    // a square size cannot catch a transposition, so the assertion above uses one that can; this one pins the form
    assert(AnsiSequences.resizeWindow(Size(1, 1)) == s"$Esc[8;1;1t")

  test("requestSize defaults to a successful no-op for a backend with no emulator to ask"):
    val backend: Backend = BareBackend()
    assert(backend.requestSize(Size(80, 24)) == Right(()))

  test("a headless backend records what was asked for and grants it"):
    val backend = HeadlessBackend(Size(20, 5))
    assert(backend.requestedSizes.isEmpty)
    assert(backend.requestSize(Size(100, 30)) == Right(()))
    assert(backend.requestedSizes == Seq(Size(100, 30)))
    assert(backend.size == Right(Size(100, 30)))

  test("granting a request goes through the app's normal resize path, not behind its back"):
    // a size that changed with no event would leave every cached layout in the app stale, and a test that asserted on
    // `size` alone would pass while the real terminal fell apart
    val backend = HeadlessBackend(Size(20, 5))
    val _       = backend.requestSize(Size(40, 10))
    assert(backend.readEvent(Duration(50, "ms")) == Right(Some(Event.Resize(Size(40, 10)))))

  test("requests are kept in order, so a test can assert on the last one an app made"):
    val backend = HeadlessBackend(Size(20, 5))
    val _       = backend.requestSize(Size(30, 8))
    val _       = backend.requestSize(Size(31, 9))
    assert(backend.requestedSizes == Seq(Size(30, 8), Size(31, 9)))

  test("a non-positive size is a defect in the caller, not a terminal failure"):
    // there is no terminal in the world that could grant it, so reporting it as a `BackendError` would invite a caller
    // to handle at runtime what a reviewer should have caught at the call site
    val backend = HeadlessBackend(Size(20, 5))
    val _       = intercept[IllegalArgumentException](backend.requestSize(Size(0, 10)))
    val _       = intercept[IllegalArgumentException](backend.requestSize(Size(10, 0)))
    val _       = intercept[IllegalArgumentException](backend.requestSize(Size(-1, -1)))
    assert(backend.requestedSizes.isEmpty)
    assert(backend.size == Right(Size(20, 5)))
