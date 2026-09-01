package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Event, MouseButton, MouseEvent, MouseEventKind, Position}

/** The three mouse-report wire formats a terminal can send — SGR 1006, urxvt 1015, and legacy X10 — transcribed into
  * [[Event.Mouse]] values.
  *
  * A transcription table only: no input is read and no state is held, so it is safe to call from any thread. It sits
  * beside [[CsiKeys]] and [[KittyKeys]] rather than inside [[InputDecoder]] for the same reason those do — what a
  * button byte *means* is checked against an external specification (XTerm's `ctlseqs.ms`), not against how bytes are
  * pulled off the wire.
  *
  * The three formats agree on the button byte and disagree on everything else: SGR writes it undecorated and carries a
  * per-button release in its final byte, while urxvt and X10 both bias it by [[X10Bias]] and have no release code, so
  * for those two whether a report is a press has to be derived from the bits. Each entry point below states which.
  */
private[terminal] object MouseReports:

  /** Every coordinate and button value in an X10-derived mouse report is written biased by this much. */
  private val X10Bias = 32

  /** Bit 5 of a mouse report's button byte: set when the report describes motion rather than a press or a release. */
  private val MotionBit = 32

  /** The low two bits of a mouse report's button byte, which name the button involved. */
  private val ButtonMask = 3

  /** The button-bits value that means "no button" — a motion report carrying it is a hover, not a drag. */
  private val NoButtonHeld = 3

  /** How far a mouse report's shift/alt/ctrl bits sit above the CSI modifier parameter's: mouse uses 4/8/16 where a CSI
    * modifier bitmask uses 1/2/4.
    */
  private val MouseModifierShift = 2

  /** SGR mouse report `CSI < b ; x ; y (M|m)`: button bits carry drag/scroll/modifier flags, coordinates are one-based.
    *
    * `isPress` comes from the final byte — `M` for a press, `m` for a release — because SGR, alone among the three
    * formats, reports which button was released rather than only that one was.
    */
  def sgr(params: Seq[Int], isPress: Boolean): Option[Event] =
    params match
      case Seq(button, column, row) => event(button, column - 1, row - 1, isPress)
      case _                        => None

  /** urxvt mouse report `CSI b ; x ; y M` (DEC mode 1015).
    *
    * The same button byte X10 uses, biased by 32, but with the coordinates written as decimal text rather than as raw
    * bytes — so it is readable past the column 95 where [[x10]] has to give up. It exists for a terminal that ignores
    * the SGR request (mode 1006); a terminal that honours SGR never sends this form, because 1006 is requested after
    * 1015 and wins.
    *
    * Like X10, and unlike SGR, it has no per-button release code — button bits `3` mean "some button came up" — so
    * whether this is a press is derived from the bits, not from the final byte, which is always `M`.
    *
    * The `button >= 32` guard is what keeps some other three-parameter CSI sequence ending in `M` from being read as a
    * click: every real report carries the +32 bias, so a smaller first parameter cannot be one.
    */
  def urxvt(params: Seq[Int]): Option[Event] =
    params match
      case Seq(button, column, row) if button >= X10Bias =>
        val bits    = button - X10Bias
        val isPress = (bits & ButtonMask) != NoButtonHeld
        event(bits, column - 1, row - 1, isPress)
      case _                                             => None

  /** Legacy X10 mouse report `CSI M b x y`: three raw bytes, each the value biased by 32.
    *
    * This branch exists only for a terminal that ignores [[AnsiSequences.EnableMouseCapture]]'s request for SGR 1006
    * and keeps sending X10 — otherwise those three bytes are decoded as text and injected as keystrokes. SGR 1006 is
    * unaffected by everything below, because its coordinates are decimal *text* and go through [[sgr]].
    *
    * X10's effective ceiling here is column/row 95, not the protocol's own 223. The decoder reads through a reader that
    * UTF-8-decodes the stream (`JLine3Backend` builds its terminal with `stdinEncoding(UTF_8)`), and a coordinate byte
    * at or above 0x80 is not valid UTF-8 on its own: it comes back as U+FFFD, which the range check below rejects so
    * the click is dropped rather than reported at an invented position such as `Position(65500, 9)`. Worse, when two
    * coordinate bytes happen to form a legal UTF-8 sequence (0xC3 0xA0, say) they collapse into one character, and the
    * byte after the report is then consumed as the missing coordinate — damage one character of lookahead cannot
    * repair. Reading raw bytes instead is not an option: the same reader's UTF-16 code units are what
    * `InputDecoder.decodeControl`'s C1 branch and `printable`'s surrogate recombination are built on.
    */
  def x10(button: Int, column: Int, row: Int): Option[Event] =
    if button > 0xff || column > 0xff || row > 0xff then None // a replacement character, not a coordinate byte
    else
      val bits    = button - X10Bias
      // X10 has no separate release code: button 3 means "some button came up"
      val isPress = (bits & ButtonMask) != NoButtonHeld
      event(bits, column - X10Bias - 1, row - X10Bias - 1, isPress)

  /** The [[Event.Mouse]] a button byte and a zero-based position describe, with the button byte carrying no format
    * bias: SGR's parameter has none to start with, and [[urxvt]] and [[x10]] subtract [[X10Bias]] before calling.
    */
  def event(button: Int, x: Int, y: Int, isPress: Boolean): Option[Event] =
    val kind      =
      if (button & 64) != 0 then wheelKind(button)
      else if (button & MotionBit) != 0 then
        // bit 5 says "this report is motion". The low two bits then name the button that is held, and the value 3
        // means "none" — so 3 is the pointer moving over the window with nothing pressed, which is a hover, and
        // anything else is a drag. Only a terminal asked for `MouseCaptureMode.AllMotion` sends the hover form; under
        // buttons-only tracking this branch never sees a 3, which is why `Moved` used to be unreachable.
        if (button & ButtonMask) == NoButtonHeld then MouseEventKind.Moved else MouseEventKind.Drag
      else if isPress then MouseEventKind.Down
      else MouseEventKind.Up
    // a mouse report carries the same shift/alt/ctrl bitmask as a CSI modifier parameter, shifted up by two positions
    val modifiers = InputDecoder.modifiersFromBits(button >> MouseModifierShift)
    val pressed   = pressedButton(button)
    Some(Event.Mouse(MouseEvent(Position(math.max(0, x), math.max(0, y)), kind, modifiers, pressed)))

  /** Which button the report names.
    *
    * The low two bits are the button number: 0 left, 1 middle, 2 right. The fourth value, 3, is X10's "some button came
    * up" release, which genuinely does not say which one — it becomes [[MouseButton.Unknown]] rather than an invented
    * guess. A wheel report (bit 64) reuses those same two bits for the scroll direction, so it names no button either.
    *
    * SGR 1006, unlike X10, keeps the button number on the release as well as the press, which is what makes a
    * right-button drag-and-release usable.
    */
  private def pressedButton(button: Int): MouseButton =
    if (button & 64) != 0 then MouseButton.Unknown
    else
      button & 3 match
        case 0 => MouseButton.Left
        case 1 => MouseButton.Middle
        case 2 => MouseButton.Right
        case _ => MouseButton.Unknown

  /** Wheel buttons 64 and 65 are wheel-up and wheel-down; 66 and 67 are wheel-left and wheel-right, the horizontal
    * wheel a sideways trackpad swipe sends.
    *
    * All four are distinct kinds rather than the low bit folded onto the vertical pair: reading the low bit alone
    * turned every sideways swipe into a scroll of the focused list. They used to be dropped instead, which was the
    * honest answer only while [[MouseEventKind]] had no horizontal vocabulary — it has one now.
    */
  private def wheelKind(button: Int): MouseEventKind =
    button & 3 match
      case 0 => MouseEventKind.ScrollUp
      case 1 => MouseEventKind.ScrollDown
      case 2 => MouseEventKind.ScrollLeft
      case _ => MouseEventKind.ScrollRight
