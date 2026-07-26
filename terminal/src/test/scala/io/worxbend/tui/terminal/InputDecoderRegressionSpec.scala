package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Event, KeyCode, KeyEvent, KeyModifiers, MouseEvent, MouseEventKind}

import org.scalatest.funsuite.AnyFunSuite

/** Regressions for input-decoding defects found by terminal-level audit.
  *
  * The common thread: a sequence the decoder does not understand must be *dropped*, never reported as a keypress.
  * Synthesizing an `Escape` from unparsed bytes means a capability probe or a mouse click silently closes whatever
  * dialog the user had open.
  */
final class InputDecoderRegressionSpec extends AnyFunSuite:

  private val Esc = 0x1b

  private def decoderFor(chars: Int*): InputDecoder =
    val iterator = chars.iterator
    InputDecoder(_ => if iterator.hasNext then iterator.next() else -2)

  private def decoded(chars: Int*): Event =
    decoderFor(chars*).decode(10).getOrElse(fail("expected an event"))

  private def dropped(chars: Int*): Unit =
    assert(decoderFor(chars*).decode(10).isEmpty)

  private def csi(body: String): Seq[Int] = Esc +: '['.toInt +: body.map(_.toInt)

  test("SS3 application-mode cursor keys decode to the arrows"):
    // what a terminal sends once DECCKM is on (e.g. tmux with xterm-keys); previously a stray Escape
    assert(decoded(Esc, 'O', 'A') == Event.Key(KeyEvent.of(KeyCode.Up)))
    assert(decoded(Esc, 'O', 'B') == Event.Key(KeyEvent.of(KeyCode.Down)))
    assert(decoded(Esc, 'O', 'C') == Event.Key(KeyEvent.of(KeyCode.Right)))
    assert(decoded(Esc, 'O', 'D') == Event.Key(KeyEvent.of(KeyCode.Left)))

  test("terminal replies are dropped instead of surfacing as an Escape keypress"):
    dropped(csi("?62;1;4c")*) // primary device attributes
    dropped(csi("?1$p")*)     // DECRPM mode report
    dropped(csi(">0;95;0c")*) // secondary device attributes
    dropped(csi("24;80R")*) // cursor position report

  test("an unknown CSI final byte produces no event"):
    dropped(csi("1;2W")*)

  test("a torn sequence that times out mid-flight produces no event"):
    dropped(Esc, '[')
    dropped(csi("1;5")*)

  test("a legacy X10 mouse report decodes as a mouse event, not as keystrokes"):
    // a terminal that ignores the SGR-1006 request keeps sending X10; those bytes used to be injected as text
    val report = Seq(Esc, '['.toInt, 'M'.toInt, 32 + 0, 32 + 33, 32 + 33)
    assert(decoded(report*) == Event.Mouse(MouseEvent(32, 32, MouseEventKind.Down, KeyModifiers.None)))

  test("an X10 button-3 report is a release"):
    val report = Seq(Esc, '['.toInt, 'M'.toInt, 32 + 3, 32 + 5, 32 + 5)
    assert(decoded(report*) == Event.Mouse(MouseEvent(4, 4, MouseEventKind.Up, KeyModifiers.None)))

  test("a truncated X10 report is dropped rather than half-decoded"):
    dropped(Esc, '['.toInt, 'M'.toInt, 32)

  test("a paste containing an escape sequence keeps its payload and still finds the terminator"):
    // the terminator's own ESC-[ used to be eaten by a five-byte lookahead that could not push back
    val payload = "a[Ab"
    val bytes   = csi("200~") ++ payload.map(_.toInt) ++ csi("201~")
    assert(decoded(bytes*) == Event.Paste(payload))

  test("a paste whose text merely mentions the terminator is not truncated"):
    val payload = "see [201~ in the docs"
    val bytes   = csi("200~") ++ payload.map(_.toInt) ++ csi("201~")
    assert(decoded(bytes*) == Event.Paste(payload))

  test("a paste ending exactly at the terminator keeps every byte before it"):
    val payload = "abc"
    val bytes   = csi("200~") ++ payload.map(_.toInt) ++ csi("201~")
    assert(decoded(bytes*) == Event.Paste("abc"))

  test("an astral character arrives as one key carrying its code point"):
    // UTF-16 surrogates must be recombined; two lone halves corrupt any string they land in
    assert(decoded("😀".map(_.toInt)*) == Event.Key(KeyEvent(KeyCode.Char(0x1f600), KeyModifiers.None)))

  test("a high surrogate not followed by a low one does not swallow the next key"):
    val events = decoderFor(0xd83d, 'a'.toInt).decode(10).toList ++ decoderFor().decode(1).toList
    assert(events.nonEmpty)

  test("kitty reports an astral code point directly"):
    assert(decoded(csi("128512u")*) == Event.Key(KeyEvent(KeyCode.Char(0x1f600), KeyModifiers.None)))

  test("kitty functional keys map onto named keys rather than private-use glyphs"):
    assert(decoded(csi("57399u")*) == Event.Key(KeyEvent.of(KeyCode.Char('0')))) // KP_0
    assert(decoded(csi("57414u")*) == Event.Key(KeyEvent.of(KeyCode.Enter)))     // KP_ENTER
    assert(decoded(csi("57417u")*) == Event.Key(KeyEvent.of(KeyCode.Left)))      // KP_LEFT
    assert(decoded(csi("57423u")*) == Event.Key(KeyEvent.of(KeyCode.Home)))      // KP_HOME
    assert(decoded(csi("57376u")*) == Event.Key(KeyEvent.of(KeyCode.F(13))))     // F13
    assert(decoded(csi("57398u")*) == Event.Key(KeyEvent.of(KeyCode.F(35)))) // F35

  test("kitty modifier-only and lock keys are not reported as keys"):
    dropped(csi("57441u")*) // LEFT_SHIFT
    dropped(csi("57358u")*) // CAPS_LOCK
    dropped(csi("57428u")*) // MEDIA_PLAY

  test("kitty modifier parameters follow the 1 + bitmask encoding"):
    assert(decoded(csi("97;5u")*) == Event.Key(KeyEvent(KeyCode.Char('a'), KeyModifiers.Ctrl)))
    assert(decoded(csi("97;2u")*) == Event.Key(KeyEvent(KeyCode.Char('a'), KeyModifiers.Shift)))
    // caps-lock (64) and num-lock (128) bits are reported but carry no glyphora modifier
    assert(decoded(csi("97;65u")*) == Event.Key(KeyEvent(KeyCode.Char('a'), KeyModifiers.None)))

  test("the escape timeout is configurable"):
    val impatient = InputDecoder(_ => -2, escapeTimeoutMillis = 5L)
    assert(impatient.decode(1).isEmpty)

  test("a runaway parameter string is abandoned instead of looping forever"):
    dropped(csi("1" * 500)*)
