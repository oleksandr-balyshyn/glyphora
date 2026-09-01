package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, Rect, Size}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.Duration

/** The optional [[Backend]] operations — the ones with a default body, which a backend may override and a caller may
  * always call.
  *
  * Two things are worth pinning here. The first is that the defaults are *silent successes*, not failures: an app that
  * sets a window title must keep working against a backend that has no window, so `setTitle` on a bare implementation
  * returns `Right(())` and does nothing. The second is that adding one of these to the trait cannot break an existing
  * implementor — [[MinimalBackend]] below implements only the abstract members and still compiles, which is exactly the
  * guarantee a published `0.x` library owes anyone who wrote their own backend against an earlier release.
  */
final class BackendCapabilitySpec extends AnyFunSuite:

  /** A backend that implements the abstract members of [[Backend]] and nothing else. */
  private final class MinimalBackend extends Backend:
    def size: Either[BackendError, Size]                                  = Right(Size(10, 2))
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

  test("a backend that overrides nothing still answers every optional operation"):
    val backend: Backend = MinimalBackend()
    assert(backend.setTitle("anything") == Right(()))
    assert(backend.clearRegion(ClearType.All) == Right(()))
    assert(backend.requestFullRedraw() == ())

  test("the headless backend records the last title it was given"):
    val backend = HeadlessBackend(Size(10, 2))
    assert(backend.titleContents.isEmpty)
    assert(backend.setTitle("glyphora — 日本語").isRight)
    assert(backend.titleContents.contains("glyphora — 日本語"))
    // the *last* one wins: an app that retitles per document must not leave the first title observable
    assert(backend.setTitle("").isRight)
    assert(backend.titleContents.contains(""))

  test("the headless backend counts full-repaint requests and a plain draw raises none"):
    val backend = HeadlessBackend(Size(10, 2))
    assert(backend.fullRedrawCount == 0L)
    assert(backend.draw(Buffer(Rect(0, 0, 10, 2))).isRight)
    assert(backend.fullRedrawCount == 0L, "drawing is not itself a request to repaint everything")
    backend.requestFullRedraw()
    backend.requestFullRedraw()
    // counted rather than collapsed: the headless backend keeps whole frames instead of a diff baseline, so there is
    // nothing here to invalidate, and how often the app asked is the only honest thing it can report
    assert(backend.fullRedrawCount == 2L)
