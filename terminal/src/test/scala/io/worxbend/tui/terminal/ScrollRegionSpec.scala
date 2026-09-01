package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, Rect, Size, Style}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.Duration

/** Covers the scrolling-region vocabulary: the DECSTBM sequences, the band validation every backend shares, and what
  * `HeadlessBackend` records so a test above this module can assert on it.
  *
  * A scrolling region is the band of rows a terminal will let a scroll move. It is the primitive behind inserting lines
  * above a live interface without repainting the interface, and it is also the one primitive here that can leave a
  * terminal unusable if it is left set — hence the reset in `RestoreAll`, pinned below.
  */final class ScrollRegionSpec extends AnyFunSuite:

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

  /** A buffer whose every row is its own index repeated, so a shift is legible cell by cell. */
  private def numbered(width: Int, height: Int): Buffer =
    val buffer = Buffer(Rect(0, 0, width, height))
    (0 until height).foreach(y => buffer.setString(0, y, y.toString * width, Style.Default))
    buffer

  private def rowText(buffer: Buffer, y: Int): String =
    (0 until buffer.area.width).map(x => buffer.get(x, y).symbol).mkString

  // ---------------------------------------------------------------- the sequences

  test("DECSTBM converts a zero-based inclusive band to the one-based inclusive sequence"):
    assert(AnsiSequences.setScrollRegion(0, 4) == s"$Esc[1;5r")
    assert(AnsiSequences.setScrollRegion(3, 3) == s"$Esc[4;4r")

  test("the region is released with the parameterless form"):
    assert(AnsiSequences.ResetScrollRegion == s"$Esc[r")

  test("SD mirrors SU, and both treat a non-positive count as nothing to do"):
    assert(AnsiSequences.scrollUp(2) == s"$Esc[2S")
    assert(AnsiSequences.scrollDown(2) == s"$Esc[2T")
    assert(AnsiSequences.scrollDown(0).isEmpty)
    assert(AnsiSequences.scrollDown(-1).isEmpty)

  test("releasing the region is part of the unconditional restore string"):
    // a region left clamped outlives the process: every later scroll, the user's shell included, then refuses to touch
    // the rest of the screen. Releasing one that was never set does nothing, which is what makes it safe to send blind.
    assert(AnsiSequences.RestoreAll.contains(AnsiSequences.ResetScrollRegion))

  // ---------------------------------------------------------------- the row band

  test("a row range is inclusive at both ends, the way DECSTBM reads"):
    assert(RowRange(2, 5).height == 4)
    assert(RowRange(3, 3).height == 1)

  test("a row range that runs backwards or starts above the screen is a defect, not a runtime failure"):
    val _ = intercept[IllegalArgumentException](RowRange(5, 2))
    val _ = intercept[IllegalArgumentException](RowRange(-1, 3))

  // ---------------------------------------------------------------- the frame-record shift

  test("scrolling up moves each row's content to the row above it and blanks the bottom"):
    val moved = ScrollDirection.shifted(numbered(3, 5), RowRange(0, 4), 1, ScrollDirection.Up)
    assert(rowText(moved, 0) == "111")
    assert(rowText(moved, 3) == "444")
    assert(rowText(moved, 4) == "   ")

  test("scrolling down moves each row's content to the row below it and blanks the top"):
    val moved = ScrollDirection.shifted(numbered(3, 5), RowRange(0, 4), 1, ScrollDirection.Down)
    assert(rowText(moved, 0) == "   ")
    assert(rowText(moved, 1) == "000")
    assert(rowText(moved, 4) == "333")

  test("rows outside the band are left exactly as they were"):
    // the point of a region: a list inside a bordered panel scrolls without the border moving with it
    val moved = ScrollDirection.shifted(numbered(3, 6), RowRange(2, 4), 1, ScrollDirection.Up)
    assert(rowText(moved, 0) == "000")
    assert(rowText(moved, 1) == "111")
    assert(rowText(moved, 2) == "333")
    assert(rowText(moved, 3) == "444")
    assert(rowText(moved, 4) == "   ")
    assert(rowText(moved, 5) == "555")

  test("scrolling by the height of the band, or further, blanks it entirely"):
    val exactly = ScrollDirection.shifted(numbered(2, 4), RowRange(1, 2), 2, ScrollDirection.Up)
    assert(rowText(exactly, 1) == "  ")
    assert(rowText(exactly, 2) == "  ")
    assert(rowText(exactly, 3) == "33")
    val beyond  = ScrollDirection.shifted(numbered(2, 4), RowRange(1, 2), 99, ScrollDirection.Down)
    assert(rowText(beyond, 1) == "  ")
    assert(rowText(beyond, 2) == "  ")

  test("a shift of nothing, or a band the frame does not cover, changes nothing"):
    val frame = numbered(2, 3)
    assert(rowText(ScrollDirection.shifted(frame, RowRange(0, 2), 0, ScrollDirection.Up), 0) == "00")
    // rows past the bottom of this buffer: the terminal has its own view of the screen, and modelling it is all this
    // does, so naming rows outside the frame is ignored rather than rejected
    assert(rowText(ScrollDirection.shifted(frame, RowRange(7, 9), 1, ScrollDirection.Up), 0) == "00")

  test("the source frame is never modified"):
    val frame = numbered(2, 3)
    val _     = ScrollDirection.shifted(frame, RowRange(0, 2), 1, ScrollDirection.Up)
    assert(rowText(frame, 0) == "00")

  test("a two-column grapheme survives the shift instead of being torn in half"):
    // 漢 occupies two columns: the cell to its right is a continuation the terminal never draws separately. A shift
    // that copied cells one at a time without that knowledge would leave a half glyph behind.
    val frame = Buffer(Rect(0, 0, 4, 3))
    frame.setString(0, 1, "漢a", Style.Default)
    val moved = ScrollDirection.shifted(frame, RowRange(0, 2), 1, ScrollDirection.Up)
    assert(moved.get(0, 0).symbol == "漢")
    assert(moved.isContinuation(1, 0))
    assert(moved.get(2, 0).symbol == "a")

  test("an emoji sequence and a combining mark move as whole clusters"):
    val frame = Buffer(Rect(0, 0, 6, 2))
    frame.setString(0, 1, "👨‍👩‍👧é", Style.Default)
    val moved = ScrollDirection.shifted(frame, RowRange(0, 1), 1, ScrollDirection.Up)
    assert(moved.get(0, 0).symbol == "👨‍👩‍👧")
    assert(moved.get(2, 0).symbol == "é")

  // ---------------------------------------------------------------- the backend surface

  test("the default reports the capability as missing rather than succeeding silently"):
    // a caller's fallback is to repaint the rows itself, so a no-op that claimed success would leave stale rows on
    // screen with nothing to notice it by
    val backend: Backend = BareBackend()
    assert(backend.scrollRegionUp(RowRange(0, 2), 1).isLeft)
    assert(backend.scrollRegionDown(RowRange(0, 2), 1).isLeft)
    assert(backend.scrollRegionUp(RowRange(0, 2), 1).left.exists(_.message.contains("scroll region")))

  test("the headless backend records the scroll and moves the rows of its retained frame"):
    val backend = HeadlessBackend(Size(3, 5))
    assert(backend.draw(numbered(3, 5)) == Right(()))
    assert(backend.scrollRegionUp(RowRange(0, 4), 1) == Right(()))
    assert(backend.regionScrolls == Seq((RowRange(0, 4), 1, ScrollDirection.Up)))
    val frame   = backend.lastDrawn.getOrElse(fail("nothing was drawn"))
    assert(rowText(frame, 0) == "111")
    assert(rowText(frame, 4) == "   ")

  test("a scroll of no rows is recorded as nothing, so a caller computing a delta needs no guard"):
    val backend = HeadlessBackend(Size(3, 5))
    assert(backend.scrollRegionUp(RowRange(0, 4), 0) == Right(()))
    assert(backend.scrollRegionDown(RowRange(0, 4), -2) == Right(()))
    assert(backend.regionScrolls.isEmpty)

  test("scrolls accumulate in order, so a test can count them"):
    // the assertion that separates an app which scrolls from one that repaints the list and happens to look the same
    val backend = HeadlessBackend(Size(3, 5))
    val _       = backend.scrollRegionUp(RowRange(1, 3), 1)
    val _       = backend.scrollRegionDown(RowRange(1, 3), 2)
    assert(
      backend.regionScrolls ==
        Seq((RowRange(1, 3), 1, ScrollDirection.Up), (RowRange(1, 3), 2, ScrollDirection.Down))
    )
