package io.worxbend.tui.terminal

import io.worxbend.tui.core.{
  Event,
  KeyCode,
  KeyEvent,
  KeyModifiers,
  MediaKey,
  ModifierKey,
  MouseButton,
  MouseEvent,
  MouseEventKind,
  Position,
}

import org.scalatest.funsuite.AnyFunSuite

final class InputDecoderSpec extends AnyFunSuite:

  /** A decoder fed from a fixed script of character codes; reads past the end report a timeout. */
  private def decoderFor(chars: Int*): InputDecoder =
    val iterator = chars.iterator
    InputDecoder(_ => if iterator.hasNext then iterator.next() else -2)

  private def decoded(chars: Int*): Event =
    decoderFor(chars*).decode(10).getOrElse(fail("expected an event"))

  private def csi(body: String): Seq[Int] = 0x1b +: '['.toInt +: body.map(_.toInt)

  test("a timeout with no input decodes to no event"):
    assert(decoderFor().decode(10).isEmpty)

  test("a printable character decodes to its key"):
    assert(decoded('q') == Event.Key(KeyEvent(KeyCode.Char('q'), KeyModifiers.None)))

  test("carriage return and line feed both decode to Enter"):
    assert(decoded(0x0d) == Event.Key(KeyEvent.of(KeyCode.Enter)))
    assert(decoded(0x0a) == Event.Key(KeyEvent.of(KeyCode.Enter)))

  test("tab and backspace decode to their named keys"):
    assert(decoded(0x09) == Event.Key(KeyEvent.of(KeyCode.Tab)))
    assert(decoded(0x7f) == Event.Key(KeyEvent.of(KeyCode.Backspace)))

  test("a control character decodes to Ctrl plus the letter"):
    assert(decoded(3) == Event.Key(KeyEvent(KeyCode.Char('c'), KeyModifiers.Ctrl)))

  test("a lone escape decodes to the Escape key"):
    assert(decoded(0x1b) == Event.Key(KeyEvent.of(KeyCode.Escape)))

  test("escape followed by a printable character decodes to Alt plus the key"):
    assert(decoded(0x1b, 'x') == Event.Key(KeyEvent(KeyCode.Char('x'), KeyModifiers.Alt)))

  test("CSI arrow sequences decode to the arrow keys"):
    assert(decoded(csi("A")*) == Event.Key(KeyEvent.of(KeyCode.Up)))
    assert(decoded(csi("B")*) == Event.Key(KeyEvent.of(KeyCode.Down)))
    assert(decoded(csi("C")*) == Event.Key(KeyEvent.of(KeyCode.Right)))
    assert(decoded(csi("D")*) == Event.Key(KeyEvent.of(KeyCode.Left)))

  test("a modified arrow carries the xterm modifier parameter"):
    assert(decoded(csi("1;5C")*) == Event.Key(KeyEvent(KeyCode.Right, KeyModifiers.Ctrl)))
    assert(decoded(csi("1;2A")*) == Event.Key(KeyEvent(KeyCode.Up, KeyModifiers.Shift)))
    assert(decoded(csi("1;7D")*) == Event.Key(KeyEvent(KeyCode.Left, KeyModifiers.Ctrl | KeyModifiers.Alt)))

  test("Home and End decode from both the letter and tilde encodings"):
    assert(decoded(csi("H")*) == Event.Key(KeyEvent.of(KeyCode.Home)))
    assert(decoded(csi("F")*) == Event.Key(KeyEvent.of(KeyCode.End)))
    assert(decoded(csi("1~")*) == Event.Key(KeyEvent.of(KeyCode.Home)))
    assert(decoded(csi("4~")*) == Event.Key(KeyEvent.of(KeyCode.End)))

  test("navigation tilde sequences decode to their named keys"):
    assert(decoded(csi("2~")*) == Event.Key(KeyEvent.of(KeyCode.Insert)))
    assert(decoded(csi("3~")*) == Event.Key(KeyEvent.of(KeyCode.Delete)))
    assert(decoded(csi("5~")*) == Event.Key(KeyEvent.of(KeyCode.PageUp)))
    assert(decoded(csi("6~")*) == Event.Key(KeyEvent.of(KeyCode.PageDown)))

  test("function keys decode from SS3 and tilde encodings"):
    assert(decoded(0x1b, 'O', 'P') == Event.Key(KeyEvent.of(KeyCode.F(1))))
    assert(decoded(0x1b, 'O', 'S') == Event.Key(KeyEvent.of(KeyCode.F(4))))
    assert(decoded(csi("15~")*) == Event.Key(KeyEvent.of(KeyCode.F(5))))
    assert(decoded(csi("17~")*) == Event.Key(KeyEvent.of(KeyCode.F(6))))
    assert(decoded(csi("24~")*) == Event.Key(KeyEvent.of(KeyCode.F(12))))

  test("the legacy shifted function keys decode to F13-F20"):
    // xterm sends these when Shift is held with F1-F8. The numbers are not contiguous: 27, 30 and 35 name no key,
    // which is why the pairs are written out rather than derived from an offset. 29 is missing for a different
    // reason: it is claimed by both F16 and the menu key, and `CsiKeys` resolves that in favour of Menu, so F16 is
    // unreachable through the tilde encoding and only a kitty-protocol terminal can report it.
    val expected = Seq(25 -> 13, 26 -> 14, 28 -> 15, 31 -> 17, 32 -> 18, 33 -> 19, 34 -> 20)
    expected.foreach: (tilde, functionKey) =>
      assert(decoded(csi(s"$tilde~")*) == Event.Key(KeyEvent.of(KeyCode.F(functionKey))))

  test("the tilde numbers xterm leaves unassigned still decode to no key"):
    Seq(27, 30, 35).foreach: tilde =>
      assert(decoderFor(csi(s"$tilde~")*).decode(10).isEmpty, s"CSI $tilde~ should name no key")

  test("a shifted function key carries the xterm modifier parameter"):
    assert(decoded(csi("25;2~")*) == Event.Key(KeyEvent(KeyCode.F(13), KeyModifiers.Shift)))
    assert(decoded(csi("34;5~")*) == Event.Key(KeyEvent(KeyCode.F(20), KeyModifiers.Ctrl)))

  test("every f13-f20 spec names a key some terminal can actually send"):
    // The two vocabularies have to agree: `KeyEvent.parse` accepting "f13" while no decoder path ever produces
    // KeyCode.F(13) on a non-kitty terminal is a binding that silently never fires. This is the assertion that
    // would have caught that, so it compares the parsed spec against what the legacy sequence decodes to.
    // 29 is left out: it decodes to Menu rather than F16, so "f16" has no legacy sequence to compare against.
    Seq(25 -> 13, 26 -> 14, 28 -> 15, 31 -> 17, 32 -> 18, 33 -> 19, 34 -> 20).foreach: (tilde, n) =>
      val parsed = KeyEvent.parse(s"f$n").getOrElse(fail(s"f$n should parse"))
      assert(decoded(csi(s"$tilde~")*) == Event.Key(parsed))

  test("shift-tab decodes from CSI Z"):
    assert(decoded(csi("Z")*) == Event.Key(KeyEvent(KeyCode.Tab, KeyModifiers.Shift)))

  test("CSI Z folds the parsed modifiers in rather than replacing them with Shift"):
    // xterm sends CSI 1;5Z for Ctrl+Shift+Tab; the sequence's own `Z` already means Shift
    assert(decoded(csi("1;5Z")*) == Event.Key(KeyEvent(KeyCode.Tab, KeyModifiers.Ctrl | KeyModifiers.Shift)))
    assert(decoded(csi("1;3Z")*) == Event.Key(KeyEvent(KeyCode.Tab, KeyModifiers.Alt | KeyModifiers.Shift)))

  test("an SGR mouse report keeps decoding when a field carries a sub-parameter"):
    assert(
      decoded(csi("<0:1;10;5M")*) == Event.Mouse(MouseEvent(Position(9, 4), MouseEventKind.Down, KeyModifiers.None))
    )

  test("an SGR mouse press decodes with zero-based coordinates"):
    assert(decoded(csi("<0;10;5M")*) == Event.Mouse(MouseEvent(Position(9, 4), MouseEventKind.Down, KeyModifiers.None)))

  test("an SGR mouse release decodes to Up"):
    assert(decoded(csi("<0;3;3m")*) == Event.Mouse(MouseEvent(Position(2, 2), MouseEventKind.Up, KeyModifiers.None)))

  test("a drag report decodes to Drag"):
    assert(decoded(csi("<32;4;4M")*) == Event.Mouse(MouseEvent(Position(3, 3), MouseEventKind.Drag, KeyModifiers.None)))

  test("wheel reports decode to scroll events naming no button"):
    // a wheel notch presses nothing, so there is no button identity to report and the decoder says so
    assert(
      decoded(csi("<64;1;1M")*) ==
        Event.Mouse(MouseEvent(Position(0, 0), MouseEventKind.ScrollUp, KeyModifiers.None, MouseButton.Unknown))
    )
    assert(
      decoded(csi("<65;1;1M")*) ==
        Event.Mouse(MouseEvent(Position(0, 0), MouseEventKind.ScrollDown, KeyModifiers.None, MouseButton.Unknown))
    )

  test("horizontal wheel reports decode to the sideways scroll kinds"):
    // xterm buttons 66 and 67 are wheel-left and wheel-right, what a sideways trackpad swipe sends
    assert(
      decoded(csi("<66;5;3M")*) ==
        Event.Mouse(MouseEvent(Position(4, 2), MouseEventKind.ScrollLeft, KeyModifiers.None, MouseButton.Unknown))
    )
    assert(
      decoded(csi("<67;5;3M")*) ==
        Event.Mouse(MouseEvent(Position(4, 2), MouseEventKind.ScrollRight, KeyModifiers.None, MouseButton.Unknown))
    )

  test("a modified horizontal wheel report keeps its modifier"):
    // 66 plus the shift bit (4) is 70: the modifier shift applies to horizontal reports like any other
    assert(
      decoded(csi("<70;5;3M")*) ==
        Event.Mouse(MouseEvent(Position(4, 2), MouseEventKind.ScrollLeft, KeyModifiers.Shift, MouseButton.Unknown))
    )

  test("the legacy X10 encoding reports horizontal wheel notches too"):
    // decodeX10Mouse shares the same button decoding, with every byte biased by 32
    val leftSwipe = Seq(0x1b, '['.toInt, 'M'.toInt, 32 + 66, 32 + 1, 32 + 1)
    assert(
      decoded(leftSwipe*) ==
        Event.Mouse(MouseEvent(Position(0, 0), MouseEventKind.ScrollLeft, KeyModifiers.None, MouseButton.Unknown))
    )

  test("the low two button bits name the button that was pressed"):
    def buttonOf(report: String): MouseButton =
      decoded(csi(report)*) match
        case Event.Mouse(event) => event.button
        case other              => fail(s"expected a mouse event, got $other")

    assert(buttonOf("<0;10;5M") == MouseButton.Left)
    assert(buttonOf("<1;10;5M") == MouseButton.Middle)
    assert(buttonOf("<2;10;5M") == MouseButton.Right)

  test("an SGR release still names its button, unlike the legacy encoding"):
    assert(
      decoded(csi("<2;3;3m")*) ==
        Event.Mouse(MouseEvent(Position(2, 2), MouseEventKind.Up, KeyModifiers.None, MouseButton.Right))
    )

  test("a drag report carries the button that is being held"):
    // 32 is the motion flag, 2 the right button
    assert(
      decoded(csi("<34;4;4M")*) ==
        Event.Mouse(MouseEvent(Position(3, 3), MouseEventKind.Drag, KeyModifiers.None, MouseButton.Right))
    )

  test("the button bits and the modifier bits do not collide"):
    // 16 is the ctrl bit, 2 the right button: both must survive the same report
    assert(
      decoded(csi("<18;2;2M")*) ==
        Event.Mouse(MouseEvent(Position(1, 1), MouseEventKind.Down, KeyModifiers.Ctrl, MouseButton.Right))
    )

  test("mouse modifier bits decode to key modifiers"):
    assert(decoded(csi("<16;2;2M")*) == Event.Mouse(MouseEvent(Position(1, 1), MouseEventKind.Down, KeyModifiers.Ctrl)))
    assert(decoded(csi("<4;2;2M")*) == Event.Mouse(MouseEvent(Position(1, 1), MouseEventKind.Down, KeyModifiers.Shift)))

  test("a mouse report carrying several modifier bits decodes to all of them"):
    // 4|8|16 is the mouse encoding of shift|alt|ctrl, the same bitmask a CSI modifier parameter carries at 1|2|4
    assert(
      decoded(csi("<28;2;2M")*) ==
        Event.Mouse(
          MouseEvent(Position(1, 1), MouseEventKind.Down, KeyModifiers.Shift | KeyModifiers.Alt | KeyModifiers.Ctrl)
        )
    )

  test("the kitty lock and system key block decodes to named keys"):
    assert(decoded(csi("57358u")*) == Event.Key(KeyEvent.of(KeyCode.CapsLock)))
    assert(decoded(csi("57359u")*) == Event.Key(KeyEvent.of(KeyCode.ScrollLock)))
    assert(decoded(csi("57360u")*) == Event.Key(KeyEvent.of(KeyCode.NumLock)))
    assert(decoded(csi("57361u")*) == Event.Key(KeyEvent.of(KeyCode.PrintScreen)))
    assert(decoded(csi("57362u")*) == Event.Key(KeyEvent.of(KeyCode.Pause)))
    assert(decoded(csi("57363u")*) == Event.Key(KeyEvent.of(KeyCode.Menu)))

  test("a modifier still rides along with a system key"):
    assert(decoded(csi("57363;5u")*) == Event.Key(KeyEvent(KeyCode.Menu, KeyModifiers.Ctrl)))

  test("xterm's CSI 29~ is the menu key, so it works without the kitty protocol"):
    assert(decoded(csi("29~")*) == Event.Key(KeyEvent.of(KeyCode.Menu)))

  test("the kitty media block decodes to transport and volume keys"):
    val expected = Seq(
      57428 -> MediaKey.Play,
      57429 -> MediaKey.Pause,
      57430 -> MediaKey.PlayPause,
      57431 -> MediaKey.Reverse,
      57432 -> MediaKey.Stop,
      57433 -> MediaKey.FastForward,
      57434 -> MediaKey.Rewind,
      57435 -> MediaKey.TrackNext,
      57436 -> MediaKey.TrackPrevious,
      57437 -> MediaKey.Record,
      57438 -> MediaKey.LowerVolume,
      57439 -> MediaKey.RaiseVolume,
      57440 -> MediaKey.MuteVolume,
    )
    for (codePoint, key) <- expected do
      assert(decoded(csi(s"${codePoint}u")*) == Event.Key(KeyEvent.of(KeyCode.Media(key))))

  test("the code points bounding the media block decode as their own blocks, not as media keys"):
    // the boundary is the part of a transcribed table that rots, so it is asserted rather than assumed. 57427 is
    // unassigned; 57441 is LEFT_SHIFT, the first code point of the modifier-only block that sits directly above.
    assert(decoderFor(csi("57427u")*).decode(10).isEmpty)
    assert(decoded(csi("57441u")*) == Event.Key(KeyEvent.of(KeyCode.Modifier(ModifierKey.LeftShift))))

  test("a media key carries its modifiers like any other key"):
    assert(decoded(csi("57438;2u")*) == Event.Key(KeyEvent(KeyCode.Media(MediaKey.LowerVolume), KeyModifiers.Shift)))

  test("kitty keypad keys decode to their non-keypad equivalents"):
    assert(decoded(csi("57399u")*) == Event.Key(KeyEvent.of(KeyCode.Char('0')))) // KP_0
    assert(decoded(csi("57408u")*) == Event.Key(KeyEvent.of(KeyCode.Char('9')))) // KP_9
    assert(decoded(csi("57414u")*) == Event.Key(KeyEvent.of(KeyCode.Enter)))     // KP_ENTER
    assert(decoded(csi("57423u")*) == Event.Key(KeyEvent.of(KeyCode.Home)))      // KP_HOME
    assert(decoded(csi("57376u")*) == Event.Key(KeyEvent.of(KeyCode.F(13))))
    assert(decoded(csi("57398u")*) == Event.Key(KeyEvent.of(KeyCode.F(35))))

  test("SS3 application-keypad keys decode to the characters they print"):
    // a terminal in DECKPAM mode sends `ESC O <final>` for the numeric keypad; before these arms the whole keypad
    // decoded to nothing, so pressing keypad 4 typed no character at all
    assert(decoded(0x1b, 'O', 'p') == Event.Key(KeyEvent.of(KeyCode.Char('0'))))
    assert(decoded(0x1b, 'O', 't') == Event.Key(KeyEvent.of(KeyCode.Char('4'))))
    assert(decoded(0x1b, 'O', 'y') == Event.Key(KeyEvent.of(KeyCode.Char('9'))))
    assert(decoded(0x1b, 'O', 'M') == Event.Key(KeyEvent.of(KeyCode.Enter)))
    assert(decoded(0x1b, 'O', 'j') == Event.Key(KeyEvent.of(KeyCode.Char('*'))))
    assert(decoded(0x1b, 'O', 'k') == Event.Key(KeyEvent.of(KeyCode.Char('+'))))
    assert(decoded(0x1b, 'O', 'l') == Event.Key(KeyEvent.of(KeyCode.Char(','))))
    assert(decoded(0x1b, 'O', 'm') == Event.Key(KeyEvent.of(KeyCode.Char('-'))))
    assert(decoded(0x1b, 'O', 'n') == Event.Key(KeyEvent.of(KeyCode.Char('.'))))
    assert(decoded(0x1b, 'O', 'o') == Event.Key(KeyEvent.of(KeyCode.Char('/'))))
    assert(decoded(0x1b, 'O', 'X') == Event.Key(KeyEvent.of(KeyCode.Char('='))))
    assert(decoded(0x1b, 'O', ' ') == Event.Key(KeyEvent.of(KeyCode.Char(' '))))
    assert(decoded(0x1b, 'O', 'I') == Event.Key(KeyEvent.of(KeyCode.Tab)))

  test("an SS3 keypad key decodes to the same event as its kitty counterpart"):
    // one physical key, one event, whichever protocol the terminal speaks: `ESC O n` and kitty's KP_DECIMAL both
    // have to produce '.', or a binding written against one terminal stops firing on the other
    assert(decoded(0x1b, 'O', 'n') == decoded(csi("57409u")*))
    assert(decoded(0x1b, 'O', 'M') == decoded(csi("57414u")*))
    assert(decoded(0x1b, 'O', 'p') == decoded(csi("57399u")*))

  test("the SS3 arms that are not keypad keys keep their existing meaning"):
    // `p`..`y` must not swallow the function-key and cursor finals that share the SS3 introducer
    assert(decoded(0x1b, 'O', 'P') == Event.Key(KeyEvent.of(KeyCode.F(1))))
    assert(decoded(0x1b, 'O', 'A') == Event.Key(KeyEvent.of(KeyCode.Up)))
    assert(decoded(0x1b, 'O', 'H') == Event.Key(KeyEvent.of(KeyCode.Home)))

  test("an unknown SS3 final is dropped rather than reported as Escape"):
    // 'z' sits just past the keypad digit range, which is where an off-by-one in the guard would show up
    assert(decoderFor(0x1b, 'O', 'z').decode(10).isEmpty)
    assert(decoderFor(0x1b, 'O', 'a').decode(10).isEmpty)

  test("a torn escape sequence is dropped rather than reported as a key"):
    // reporting Escape here would mean a half-arrived arrow key silently closes the user's dialog
    assert(decoderFor(0x1b, '[').decode(10).isEmpty)
    assert(decoderFor(csi("1;5")*).decode(10).isEmpty)

  test("kitty CSI-u sequences decode without the escape timeout heuristic"):
    assert(decoded(csi("27u")*) == Event.Key(KeyEvent.of(KeyCode.Escape)))
    assert(decoded(csi("13u")*) == Event.Key(KeyEvent.of(KeyCode.Enter)))
    assert(decoded(csi("120;5u")*) == Event.Key(KeyEvent(KeyCode.Char('x'), KeyModifiers.Ctrl)))
    assert(decoded(csi("9;2u")*) == Event.Key(KeyEvent(KeyCode.Tab, KeyModifiers.Shift)))

  test("terminal focus reports decode to focus events"):
    assert(decoded(csi("I")*) == Event.FocusGained)
    assert(decoded(csi("O")*) == Event.FocusLost)

  test("a bracketed paste arrives as one event with the payload intact"):
    val payload = "hello\nworld"
    val bytes   = csi("200~") ++ payload.map(_.toInt) ++ csi("201~")
    assert(decoded(bytes*) == Event.Paste("hello\nworld"))

  test("paste content containing a stray escape survives"):
    val bytes = csi("200~") ++ Seq('a'.toInt, 0x1b, 'b'.toInt) ++ csi("201~")
    decoded(bytes*) match
      case Event.Paste(text) => assert(text.startsWith("a") && text.contains("b"))
      case other             => fail(s"expected paste, got $other")

  test("an 8-bit C1 control decodes to no event"):
    // C1 names no key; reporting one as an unmodified Char inserts it into whatever text field has focus
    assert(decoderFor(0x85).decode(10).isEmpty)
    assert(decoderFor(0x9b).decode(10).isEmpty)
    assert(decoderFor(0x1b, 0x9b).decode(10).isEmpty)

  test("a printable Latin-1 character just above the C1 range still decodes"):
    assert(decoded(0xe9) == Event.Key(KeyEvent(KeyCode.Char(0xe9), KeyModifiers.None)))
