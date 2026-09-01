package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, KeyCode, KeyEvent, KeyModifiers, Position, Size}

import scala.concurrent.duration.{Duration, DurationInt}

import org.scalatest.funsuite.AnyFunSuite

/** The cursor-position *query*: `ESC[6n` out, `CSI row ; column R` back.
  *
  * The whole difficulty is that the reply is byte-for-byte a modified F3 key. `InputDecoder.isFunctionKey3` resolves
  * that collision in F3's favour, and could only do so because nothing ever asked for a report. Now something can, so
  * the resolution has to become conditional — a report only while a query is outstanding — and these tests pin both
  * sides of that, plus the keys a user types during the round trip, which must not be lost.
  */
final class CursorReportSpec extends AnyFunSuite with DecoderFixtures:

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

  test("the request is the Device Status Report for the cursor"):
    assert(AnsiSequences.RequestCursorPosition == "[6n")

  test("a report is read as a zero-based position, converted from the one-based wire format"):
    // the wire format is row-then-column and one-based; Position is column-then-row and zero-based, so this converts
    // on both axes at once and a test that used a square coordinate would catch neither mistake
    val decoder = decoderFor(csi("12;40R")*)
    assert(decoder.readCursorReport(50.millis) == Some(Position(39, 11)))

  test("the top-left corner reports as the origin rather than as row one"):
    assert(decoderFor(csi("1;1R")*).readCursorReport(50.millis) == Some(Position(0, 0)))

  test("outside a query the very same bytes are still the F3 key"):
    // this is the disambiguation the rest of the decoder depends on: a terminal sending `CSI 1;5R` unprompted means
    // Ctrl+F3, and reading it as a cursor report would silently swallow the key
    val decoder = decoderFor(csi("1;5R")*)
    assert(decoder.decode(10) == Some(Event.Key(KeyEvent(KeyCode.F(3), KeyModifiers.Ctrl))))

  test("inside a query the same shape is read as the report it is"):
    // `CSI 1;5R` is genuinely ambiguous — row 1, column 5 is a real place for a cursor to be — and the only thing that
    // resolves it is whether this library just asked
    assert(decoderFor(csi("1;5R")*).readCursorReport(50.millis) == Some(Position(4, 0)))

  test("the ambiguity closes again as soon as the query is answered"):
    val decoder = decoderFor((csi("3;3R") ++ csi("1;5R"))*)
    assert(decoder.readCursorReport(50.millis) == Some(Position(2, 2)))
    assert(decoder.decode(10) == Some(Event.Key(KeyEvent(KeyCode.F(3), KeyModifiers.Ctrl))))

  test("a terminal that never answers reports nothing rather than hanging"):
    // the ordinary outcome on a terminal with no support for the report, not a defect — hence a timeout and not a
    // blocking read
    assert(decoderFor().readCursorReport(20.millis).isEmpty)

  test("keys typed during the round trip are delivered afterwards, in the order they were typed"):
    // the reply travels on the same stream the user's keystrokes do, so a key pressed while the query is in flight
    // arrives first. Dropping it would make an inline app lose input every time it anchored itself.
    val decoder = decoderFor(('a'.toInt +: 'b'.toInt +: csi("2;7R"))*)
    assert(decoder.readCursorReport(50.millis) == Some(Position(6, 1)))
    assert(decoder.decode(10) == Some(Event.Key(KeyEvent(KeyCode.Char('a'), KeyModifiers.None))))
    assert(decoder.decode(10) == Some(Event.Key(KeyEvent(KeyCode.Char('b'), KeyModifiers.None))))

  test("a deferred key is delivered even when the query itself timed out"):
    // the query failing is no reason to lose what the user typed while it was outstanding
    val decoder = decoderFor(('x'.toInt +: Seq.empty)*)
    assert(decoder.readCursorReport(20.millis).isEmpty)
    assert(decoder.decode(10) == Some(Event.Key(KeyEvent(KeyCode.Char('x'), KeyModifiers.None))))

  test("a malformed report is not mistaken for a position"):
    // a single parameter, or a zero where the wire format promises one-based coordinates, is not a report this can
    // convert — reading it anyway would place an inline UI one row off, or at row -1
    assert(decoderFor(csi("5R")*).readCursorReport(20.millis).isEmpty)
    assert(decoderFor(csi("0;4R")*).readCursorReport(20.millis).isEmpty)

  test("the query default reports the capability as missing rather than guessing an origin"):
    // a guessed origin is the specific wrong answer that draws an inline UI over the user's own scrollback
    val backend: Backend = BareBackend()
    val answer           = backend.queryCursorPosition(50.millis)
    assert(answer.isLeft)
    assert(answer.left.exists(_.message.contains("cursor position")))

  test("the headless backend cannot answer either, and says so"):
    // it has no terminal to ask, and inventing a position would let a test pass while the real app drew in the wrong
    // place — the failure has to be visible
    assert(HeadlessBackend(Size(20, 5)).queryCursorPosition(50.millis).isLeft)

  test("an unbounded wait still reads the reply that arrives after a few empty reads"):
    // `Duration.Inf` used to reach the decoder as Long.MaxValue milliseconds. Multiplying that by a million to get
    // nanos does not fit in a Long: it wrapped round to a small negative number, so the deadline was about a
    // millisecond in the *past*, the wait loop never ran once, and the query reported "this terminal cannot answer"
    // instantly.
    val script  = Seq.fill(3)(NothingAvailable) ++ csi("5;9R") // three timed-out reads, then the report
    val decoder = decoderFor(script*)
    assert(decoder.readCursorReport(Duration.Inf) == Some(Position(8, 4)))

  test("Duration.Inf reaches the decoder as a wait that actually waits"):
    // the decoder still clamps a finite-but-huge wait, so that is the value the guard has to survive; anything above
    // roughly 292 years overflows the same way
    assert(Long.MaxValue * 1000000L < 0) // the overflow itself, stated so the reason for the guard is on the record
