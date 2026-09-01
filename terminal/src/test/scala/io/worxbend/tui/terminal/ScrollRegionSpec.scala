package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, Size}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.Duration

/** Covers the scrolling-region vocabulary: the DECSTBM sequences, the band validation every backend shares, and what
  * `HeadlessBackend` records so a test above this module can assert on it.
  *
  * A scrolling region is the band of rows a terminal will let a scroll move. It is the primitive behind inserting lines
  * above a live interface without repainting the interface, and it is also the one primitive here that can leave a
  * terminal unusable if it is left set — hence the reset in `RestoreAll`, pinned below.
  */
final class ScrollRegionSpec extends AnyFunSuite:

  private val Esc = ""

  // ---------------------------------------------------------------- the sequences

  test("a scrolling region is set with one-based, inclusive rows"):
    // zero-based on this side of the API, one-based on the wire: rows 0..23 of a 24-row terminal are `1;24`
    assert(AnsiSequences.setScrollRegion(0, 23) == s"$Esc[1;24r")
    assert(AnsiSequences.setScrollRegion(3, 5) == s"$Esc[4;6r")

  test("a one-row region is legal and names the same row twice"):
    assert(AnsiSequences.setScrollRegion(7, 7) == s"$Esc[8;8r")

  test("the reset form takes no parameters at all"):
    assert(AnsiSequences.ResetScrollRegion == s"$Esc[r")

  test("scrolling down has its own sequence, distinct from scrolling up"):
    assert(AnsiSequences.scrollDown(1) == s"$Esc[1T")
    assert(AnsiSequences.scrollDown(4) == s"$Esc[4T")
    assert(AnsiSequences.scrollUp(4) == s"$Esc[4S")

  test("a non-positive scroll distance produces nothing to write"):
    assert(AnsiSequences.scrollDown(0).isEmpty)
    assert(AnsiSequences.scrollDown(-2).isEmpty)

  test("the emergency restore resets the scrolling region"):
    // a process killed while a region was set otherwise leaves the *user's shell* scrolling inside a box, which is
    // exactly the damage RestoreAll exists to undo
    assert(AnsiSequences.RestoreAll.contains(AnsiSequences.ResetScrollRegion))

  // ---------------------------------------------------------------- the shared validation

  test("a band inside the screen is accepted, including the very last row"):
    Backend.checkScrollRegion(0, 23, 24)
    Backend.checkScrollRegion(23, 23, 24)
    Backend.checkScrollRegion(0, 0, 1)

  test("a band that starts above the screen is rejected"):
    assertThrows[IllegalArgumentException](Backend.checkScrollRegion(-1, 5, 24))

  test("a band whose last row is above its first is rejected"):
    assertThrows[IllegalArgumentException](Backend.checkScrollRegion(6, 5, 24))

  test("a band that runs off the bottom of the screen is rejected"):
    assertThrows[IllegalArgumentException](Backend.checkScrollRegion(0, 24, 24))

  // ---------------------------------------------------------------- the inherited default

  test("a backend that implements nothing optional still succeeds and does nothing"):
    // the contract that lets these be added to a published trait: a third-party backend written before scrolling
    // regions existed keeps compiling and keeps working
    final class Bare extends Backend:
      def size: Either[BackendError, Size]                                  = Right(Size(10, 4))
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

    val backend = Bare()
    assert(backend.scrollRegionUp(0, 3, 1) == Right(()))
    assert(backend.scrollRegionDown(0, 3, 1) == Right(()))

  // ---------------------------------------------------------------- the headless recording

  test("the headless backend records the band and how far it moved"):
    val backend = HeadlessBackend(Size(20, 10))
    assert(backend.scrollRegionUp(0, 4, 2) == Right(()))
    assert(backend.scrolledBands == Seq(ScrolledRegion(0, 4, 2)))

  test("scrolling down is recorded as a negative distance, so one log shows both directions"):
    val backend = HeadlessBackend(Size(20, 10))
    val _       = backend.scrollRegionUp(0, 4, 1)
    val _       = backend.scrollRegionDown(0, 4, 3)
    assert(backend.scrolledBands == Seq(ScrolledRegion(0, 4, 1), ScrolledRegion(0, 4, -3)))

  test("a non-positive distance records nothing, in either direction"):
    val backend = HeadlessBackend(Size(20, 10))
    val _       = backend.scrollRegionUp(0, 4, 0)
    val _       = backend.scrollRegionDown(0, 4, -1)
    assert(backend.scrolledBands.isEmpty)

  test("the headless backend rejects the same bands a real terminal would"):
    // shared validation, so a band that a headless test accepted and a terminal refused cannot exist
    val backend = HeadlessBackend(Size(20, 10))
    assertThrows[IllegalArgumentException](backend.scrollRegionUp(0, 10, 1))
    assertThrows[IllegalArgumentException](backend.scrollRegionDown(4, 2, 1))

  test("the band is validated against the terminal's current height"):
    val backend = HeadlessBackend(Size(20, 10))
    assert(backend.scrollRegionUp(0, 9, 1) == Right(()))
    backend.resizeTo(Size(20, 6))
    assertThrows[IllegalArgumentException](backend.scrollRegionUp(0, 9, 1))
