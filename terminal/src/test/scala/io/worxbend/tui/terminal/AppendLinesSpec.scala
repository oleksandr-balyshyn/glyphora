package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, Size}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.Duration

/** [[Backend.appendLines]]: the "make room by scrolling the screen up" primitive an inline viewport is built on.
  *
  * [[JLine3Backend]] needs a controlling terminal to construct, which CI does not have (see [[JLine3BackendSpec]]), so
  * the sequence it writes is pinned here on [[AnsiSequences.scrollUp]] and the behaviour on the two backends a test can
  * build.
  */
final class AppendLinesSpec extends AnyFunSuite:

  private val Esc = ""

  /** A `Backend` that overrides only the abstract members, so every defaulted one is the trait's own. */
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

  test("scrollUp emits SU for a positive count and nothing at all otherwise"):
    // the empty string matters: a caller computing "how many more rows do I need" passes zero on most frames, and an
    // `ESC[0S` on each of those would be a write per frame for no visible change
    assert(AnsiSequences.scrollUp(3) == s"$Esc[3S")
    assert(AnsiSequences.scrollUp(1) == s"$Esc[1S")
    assert(AnsiSequences.scrollUp(0) == "")
    assert(AnsiSequences.scrollUp(-2) == "")

  test("appendLines defaults to a successful no-op for a backend with no real terminal"):
    val backend: Backend = BareBackend()
    assert(backend.appendLines(4) == Right(()))

  test("the headless backend totals the rows it was asked to scroll away"):
    val backend = HeadlessBackend(Size(20, 5))
    assert(backend.appendedLineCount == 0L)
    assert(backend.appendLines(3).isRight)
    assert(backend.appendLines(2).isRight)
    assert(backend.appendedLineCount == 5L)

  test("a zero or negative count scrolls nothing rather than failing"):
    val backend = HeadlessBackend(Size(20, 5))
    assert(backend.appendLines(0).isRight)
    assert(backend.appendLines(-1).isRight)
    assert(backend.appendedLineCount == 0L)
