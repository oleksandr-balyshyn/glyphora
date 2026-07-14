package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Event, KeyCode, KeyEvent, KeyModifiers, MouseEvent, MouseEventKind}

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

  test("shift-tab decodes from CSI Z"):
    assert(decoded(csi("Z")*) == Event.Key(KeyEvent(KeyCode.Tab, KeyModifiers.Shift)))

  test("an SGR mouse press decodes with zero-based coordinates"):
    assert(decoded(csi("<0;10;5M")*) == Event.Mouse(MouseEvent(9, 4, MouseEventKind.Down, KeyModifiers.None)))

  test("an SGR mouse release decodes to Up"):
    assert(decoded(csi("<0;3;3m")*) == Event.Mouse(MouseEvent(2, 2, MouseEventKind.Up, KeyModifiers.None)))

  test("a drag report decodes to Drag"):
    assert(decoded(csi("<32;4;4M")*) == Event.Mouse(MouseEvent(3, 3, MouseEventKind.Drag, KeyModifiers.None)))

  test("wheel reports decode to scroll events"):
    assert(decoded(csi("<64;1;1M")*) == Event.Mouse(MouseEvent(0, 0, MouseEventKind.ScrollUp, KeyModifiers.None)))
    assert(decoded(csi("<65;1;1M")*) == Event.Mouse(MouseEvent(0, 0, MouseEventKind.ScrollDown, KeyModifiers.None)))

  test("mouse modifier bits decode to key modifiers"):
    assert(decoded(csi("<16;2;2M")*) == Event.Mouse(MouseEvent(1, 1, MouseEventKind.Down, KeyModifiers.Ctrl)))
    assert(decoded(csi("<4;2;2M")*) == Event.Mouse(MouseEvent(1, 1, MouseEventKind.Down, KeyModifiers.Shift)))

  test("a torn escape sequence degrades to the Escape key instead of hanging"):
    assert(decoded(0x1b, '[') == Event.Key(KeyEvent.of(KeyCode.Escape)))

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
