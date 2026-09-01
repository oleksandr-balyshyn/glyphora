package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Event, KeyModifiers, MouseEvent, MouseEventKind, Position, Size}

import org.scalatest.funsuite.AnyFunSuite

/** Hover — pointer motion with no button held — end to end: the request the backend sends, and the report the decoder
  * turns back into a [[MouseEventKind.Moved]].
  *
  * `Moved` was in the event vocabulary from the start but nothing could produce it, because the backend only ever asked
  * for button-event tracking (DEC modes 1000 and 1002), under which a terminal reports motion solely while a button is
  * down. Both halves of that are covered here: that `MouseCaptureMode.AllMotion` really does add mode 1003, and that a
  * motion report naming no button really does decode to `Moved` rather than to a phantom `Drag`.
  */
final class MouseCaptureModeSpec extends AnyFunSuite:

  private val Esc = ''

  /** Decodes one event from a fixed script of character codes; reads past the end report a timeout. */
  private def decoded(chars: Int*): Event =
    val iterator = chars.iterator
    InputDecoder(_ => if iterator.hasNext then iterator.next() else -2)
      .decode(10)
      .getOrElse(fail("expected an event"))

  private def csi(body: String): Seq[Int] = 0x1b +: '['.toInt +: body.map(_.toInt)

  test("only all-motion capture asks the terminal for mode 1003"):
    assert(!AnsiSequences.enableMouseCapture(MouseCaptureMode.Buttons).contains(s"$Esc[?1003h"))
    assert(AnsiSequences.enableMouseCapture(MouseCaptureMode.AllMotion).contains(s"$Esc[?1003h"))
    // buttons-only is exactly what the mode-less constant has always been, so nothing changes for existing callers
    assert(AnsiSequences.enableMouseCapture(MouseCaptureMode.Buttons) == AnsiSequences.EnableMouseCapture)

  test("disabling capture resets mode 1003 even when it was never set"):
    // a DEC private-mode reset is a no-op for a mode that is already off, and sending it unconditionally means no
    // teardown path can leave all-motion tracking flooding the user's shell after the app exits
    assert(AnsiSequences.DisableMouseCapture.contains(s"$Esc[?1003l"))

  test("the headless backend remembers which mode was requested"):
    val backend = HeadlessBackend(Size(10, 3))
    assert(backend.mouseCaptureMode.isEmpty)
    assert(!backend.isMouseCaptured)

    assert(backend.enableMouseCapture(MouseCaptureMode.AllMotion).isRight)
    assert(backend.mouseCaptureMode.contains(MouseCaptureMode.AllMotion))
    assert(backend.isMouseCaptured)

    // the no-argument form is buttons-only, which is what every caller written before this existed meant
    assert(backend.enableMouseCapture().isRight)
    assert(backend.mouseCaptureMode.contains(MouseCaptureMode.Buttons))

    assert(backend.disableMouseCapture().isRight)
    assert(backend.mouseCaptureMode.isEmpty)

  test("an SGR motion report with no button held decodes as Moved, not Drag"):
    // 32 is the motion bit; +3 in the low two bits means "no button", which is a hover
    assert(
      decoded(csi("<35;11;6M")*) == Event.Mouse(MouseEvent(Position(10, 5), MouseEventKind.Moved, KeyModifiers.None))
    )

  test("an SGR motion report naming a held button is still a Drag"):
    // 32 + 0: motion with the left button down
    assert(
      decoded(csi("<32;11;6M")*) == Event.Mouse(MouseEvent(Position(10, 5), MouseEventKind.Drag, KeyModifiers.None))
    )

  test("modifiers survive a hover report"):
    // 35 + 4 sets the shift bit, which sits two positions above the button bits
    assert(
      decoded(csi("<39;11;6M")*) == Event.Mouse(MouseEvent(Position(10, 5), MouseEventKind.Moved, KeyModifiers.Shift))
    )

  test("a legacy X10 hover report decodes as Moved too"):
    // X10 biases every byte by 32: button 35 (motion, no button), column 11, row 6
    val report = csi("M") ++ Seq(32 + 35, 32 + 11, 32 + 6)
    assert(decoded(report*) == Event.Mouse(MouseEvent(Position(10, 5), MouseEventKind.Moved, KeyModifiers.None)))

  test("a hover at the very top-left corner is not clamped away"):
    // the smallest coordinates the protocol can express, one-based 1;1, are the zero-based origin
    assert(
      decoded(csi("<35;1;1M")*) == Event.Mouse(MouseEvent(Position(0, 0), MouseEventKind.Moved, KeyModifiers.None))
    )
