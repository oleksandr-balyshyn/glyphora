package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, KeyCode, KeyEvent, Size}

import scala.concurrent.duration.{Duration, DurationInt}

import org.scalatest.funsuite.AnyFunSuite

final class WindowSizeSpec extends AnyFunSuite:

  private def decoderFor(chars: Int*): InputDecoder =
    val iterator = chars.iterator
    InputDecoder(_ => if iterator.hasNext then iterator.next() else -2)

  private def csi(body: String): Seq[Int] = 0x1b +: '['.toInt +: body.map(_.toInt)

  // ------------------------------------------------------------------ the value

  test("a reported window divides into a cell size and an aspect ratio"):
    val window = WindowSize(Size(80, 24), Some(Size(800, 480)))
    assert(window.cellPixels.contains(Size(10, 20)))
    assert(window.cellAspectRatio.contains(2.0))

  test("nothing is derived when the terminal reported no pixels at all"):
    val window = WindowSize(Size(80, 24), None)
    assert(window.cellPixels.isEmpty)
    assert(window.cellAspectRatio.isEmpty)

  /** Every one of these would divide by zero or produce a meaningless cell. `None` means "assume a cell shape", and
    * that is the only honest answer for all of them.
    */
  test("a degenerate report yields no cell geometry rather than a fabricated one"):
    assert(WindowSize(Size(80, 24), Some(Size(0, 0))).cellPixels.isEmpty)
    assert(WindowSize(Size(80, 24), Some(Size(800, 0))).cellPixels.isEmpty)
    assert(WindowSize(Size(80, 24), Some(Size(0, 480))).cellPixels.isEmpty)
    assert(WindowSize(Size(0, 0), Some(Size(800, 480))).cellPixels.isEmpty)
    assert(WindowSize(Size(0, 24), Some(Size(800, 480))).cellAspectRatio.isEmpty)

  /** A window narrower than its own cell grid rounds a cell down to zero columns, and a ratio over a zero width is an
    * infinity. Reporting `None` keeps callers from ever seeing one.
    */
  test("a window too small for one whole cell reports no aspect ratio"):
    assert(WindowSize(Size(80, 24), Some(Size(40, 480))).cellPixels.contains(Size(0, 20)))
    assert(WindowSize(Size(80, 24), Some(Size(40, 480))).cellAspectRatio.isEmpty)

  // ------------------------------------------------------------------ the default

  /** A backend written before `windowSize` existed: it overrides only what the trait leaves abstract. Building one here
    * is the compile-time half of the non-breaking promise — if `windowSize` ever became abstract, this stops compiling.
    */
  private def onlySize(reported: Either[BackendError, Size]): Backend =
    new Backend:
      def size: Either[BackendError, Size]                                  = reported
      def draw(buffer: Buffer): Either[BackendError, Unit]                  = Right(())
      def enableRawMode(): Either[BackendError, Unit]                       = Right(())
      def disableRawMode(): Either[BackendError, Unit]                      = Right(())
      def enterAlternateScreen(): Either[BackendError, Unit]                = Right(())
      def leaveAlternateScreen(): Either[BackendError, Unit]                = Right(())
      def enableMouseCapture(): Either[BackendError, Unit]                  = Right(())
      def disableMouseCapture(): Either[BackendError, Unit]                 = Right(())
      def hideCursor(): Either[BackendError, Unit]                          = Right(())
      def showCursor(): Either[BackendError, Unit]                          = Right(())
      def close(): Either[BackendError, Unit]                               = Right(())
      def readEvent(timeout: Duration): Either[BackendError, Option[Event]] = Right(None)

  test("a backend that implements only size reports cells and no pixels"):
    assert(onlySize(Right(Size(100, 40))).windowSize == Right(WindowSize(Size(100, 40), None)))

  test("a failing size makes windowSize fail the same way rather than reporting a guess"):
    assert(onlySize(Left(BackendError.NotInRawMode)).windowSize == Left(BackendError.NotInRawMode))

  // ------------------------------------------------------------------ the headless seam

  test("a headless backend reports whatever pixel size a test states, and none by default"):
    val backend = HeadlessBackend(Size(80, 24))
    assert(backend.windowSize == Right(WindowSize(Size(80, 24), None)))
    backend.pixelsTo(Some(Size(800, 480)))
    assert(backend.windowSize.map(_.cellAspectRatio) == Right(Some(2.0)))
    backend.pixelsTo(None)
    assert(backend.windowSize.map(_.cellPixels) == Right(None))

  test("a headless resize keeps the pixel size the test set, so the two seams stay independent"):
    val backend = HeadlessBackend(Size(80, 24))
    backend.pixelsTo(Some(Size(800, 480)))
    backend.resizeTo(Size(40, 24))
    assert(backend.windowSize == Right(WindowSize(Size(40, 24), Some(Size(800, 480)))))

  // ------------------------------------------------------------------ the reply

  test("a text-area report is captured and never surfaces as a keypress"):
    val decoder = decoderFor(csi("4;480;800t")*)
    assert(decoder.readTextAreaSize(10.millis).contains(Size(800, 480)))

  test("the reply's height-then-width wire order is swapped exactly once"):
    val decoder = decoderFor(csi("4;1080;1920t")*)
    assert(decoder.readTextAreaSize(10.millis).contains(Size(1920, 1080)))

  test("a report that arrives unasked is still dropped rather than dispatched as a key"):
    assert(decoderFor(csi("4;480;800t")*).decode(10).isEmpty)

  /** `CSI 8 ; rows ; cols t` is the *character* size report and `CSI 9 ; …` the screen size. Reading either as pixels
    * would hand a caller a cell geometry off by an order of magnitude.
    */
  test("a different XTWINOPS answer is dropped instead of being read as pixels"):
    assert(decoderFor(csi("8;24;80t")*).readTextAreaSize(10.millis).isEmpty)
    assert(decoderFor(csi("9;1080;1920t")*).readTextAreaSize(10.millis).isEmpty)
    assert(decoderFor(csi("t")*).readTextAreaSize(10.millis).isEmpty)

  test("a truncated report yields nothing and leaves the decoder usable"):
    val decoder = decoderFor((csi("4;480") ++ Seq('q'.toInt))*)
    assert(decoder.readTextAreaSize(10.millis).isEmpty)

  /** The ordering guarantee both reply round trips make: a key typed while the query is in flight is queued, not
    * dropped, and comes back out in the order it was typed.
    */
  test("keys typed while the query is in flight are delivered afterwards, in order"):
    val decoder = decoderFor((Seq('a'.toInt, 'b'.toInt) ++ csi("4;480;800t"))*)
    assert(decoder.readTextAreaSize(50.millis).contains(Size(800, 480)))
    assert(decoder.decode(10).contains(Event.Key(KeyEvent.char('a'))))
    assert(decoder.decode(10).contains(Event.Key(KeyEvent.char('b'))))

  test("a terminal that never answers times out rather than blocking forever"):
    assert(decoderFor().readTextAreaSize(5.millis).isEmpty)

  /** F3 and a cursor report collide on their contents; a text-area report does not collide with anything, so it must
    * not disturb the sequences around it.
    */
  test("capturing a report leaves the following key intact"):
    val decoder = decoderFor((csi("4;480;800t") ++ csi("A"))*)
    assert(decoder.readTextAreaSize(10.millis).contains(Size(800, 480)))
    assert(decoder.decode(10).contains(Event.Key(KeyEvent.of(KeyCode.Up))))
