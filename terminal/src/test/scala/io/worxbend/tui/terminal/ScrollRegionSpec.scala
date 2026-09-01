package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, Rect, Size, Style}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.Duration

/** Covers the scrolling-region vocabulary: the DECSTBM sequences, the `RowRange` band that validates itself, and what
  * `HeadlessBackend` records so a test above this module can assert on it.
  *
  * A scrolling region is the band of rows a terminal will let a scroll move. It is the primitive behind inserting lines
  * above a live interface without repainting the interface, and it is also the one primitive here that can leave a
  * terminal unusable if it is left set — hence the reset in `RestoreAll`, pinned below.
  *
  * The frame-record half matters as much as the sequences. A backend's `draw` writes only the cells that differ from
  * the frame it last flushed. The moment the terminal shifts rows on its own, that record stops describing the screen:
  * every row of the scrolled band reads as changed, and the next frame repaints all of them — which is exactly the work
  * the scroll was asked for to avoid. So the record is shifted the same way, and these tests pin that too.
  */
final class ScrollRegionSpec extends AnyFunSuite:

  private val Esc = ""

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
    // the boundary bands a caller actually writes: the whole of a 24-row screen, its very last row on its own, and the
    // only band a one-row screen has
    assert(RowRange(0, 23).height == 24)
    assert(RowRange(23, 23).height == 1)
    assert(RowRange(0, 0).height == 1)

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
    // screen with nothing to notice it by. `BareBackend` implements only the abstract members, which is also the
    // contract that let this operation be added to a published trait at all: a third-party backend written before
    // scrolling regions existed still compiles, and now answers honestly instead of pretending.
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
