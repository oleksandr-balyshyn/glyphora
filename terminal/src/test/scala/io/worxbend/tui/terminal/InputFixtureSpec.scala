package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Event, KeyCode, KeyEvent, KeyModifiers, MouseButton, MouseEvent, MouseEventKind, Position}

import org.scalatest.funsuite.AnyFunSuite

/** Whole-session replay: many sequences back to back through one decoder, asserting the *entire* event list.
  *
  * Single-sequence tests cannot catch the failure mode that matters most here — a sequence that consumes bytes
  * belonging to the next one. Both the bracketed-paste and X10-mouse defects this suite pins were exactly that: the
  * decoder resynchronized in the wrong place and every following keystroke came out wrong.
  *
  * The byte strings are derived from the published specifications (XTerm `ctlseqs.ms` for CSI/SS3/mouse, the kitty
  * keyboard protocol for `CSI u`), not captured from a live terminal — this machine had no GUI emulator available. To
  * add a real capture: run `script -f trace.raw`, press the keys, then paste the bytes here with the terminal and key
  * names recorded alongside, so a future reader can tell a captured fixture from a derived one.
  */
final class InputFixtureSpec extends AnyFunSuite:

  private val Esc = 0x1b

  /** Replays `bytes` through a single decoder until it stops producing events. */
  private def replay(bytes: Seq[Int]): List[Event] =
    var index    = 0
    val decoder  = InputDecoder(_ =>
      if index < bytes.length then { val c = bytes(index); index += 1; c }
      else -2
    )
    val events   = List.newBuilder[Event]
    var draining = true
    while draining do
      decoder.decode(10L) match
        case Some(event) => events += event
        case None        => if index >= bytes.length then draining = false
    events.result()

  private def csi(body: String): Seq[Int] = Esc +: '['.toInt +: body.map(_.toInt)
  private def ss3(body: String): Seq[Int] = Esc +: 'O'.toInt +: body.map(_.toInt)
  private def text(s: String): Seq[Int]   = s.map(_.toInt)

  private def key(code: KeyCode): Event                  = Event.Key(KeyEvent.of(code))
  private def key(code: KeyCode, m: KeyModifiers): Event = Event.Key(KeyEvent(code, m))

  test("xterm: arrows, modified arrows, function keys and editing keys in one stream"):
    val session = csi("A") ++ csi("B") ++ csi("1;5C") ++ csi("1;3D") ++
      ss3("P") ++ csi("15~") ++ csi("24~") ++ csi("3~") ++ csi("5~") ++ csi("H") ++ csi("F")
    assert(
      replay(session) == List(
        key(KeyCode.Up),
        key(KeyCode.Down),
        key(KeyCode.Right, KeyModifiers.Ctrl),
        key(KeyCode.Left, KeyModifiers.Alt),
        key(KeyCode.F(1)),
        key(KeyCode.F(5)),
        key(KeyCode.F(12)),
        key(KeyCode.Delete),
        key(KeyCode.PageUp),
        key(KeyCode.Home),
        key(KeyCode.End),
      )
    )

  test("xterm: typing around a capability reply loses nothing"):
    // a device-attributes reply can land in the middle of typing; it must vanish without eating neighbours
    val session = text("ab") ++ csi("?62;1;4c") ++ text("cd")
    assert(replay(session) == List('a', 'b', 'c', 'd').map(c => key(KeyCode.Char(c))))

  test("tmux with xterm-keys: SS3 cursor keys interleaved with text"):
    val session = text("x") ++ ss3("A") ++ text("y") ++ ss3("D") ++ text("z")
    assert(
      replay(session) == List(
        key(KeyCode.Char('x')),
        key(KeyCode.Up),
        key(KeyCode.Char('y')),
        key(KeyCode.Left),
        key(KeyCode.Char('z')),
      )
    )

  test("SGR 1006 mouse: press, drag, release and wheel around a keystroke"):
    val session = csi("<0;10;5M") ++ csi("<32;12;7M") ++ csi("<0;12;7m") ++ csi("<64;12;7M") ++ text("k")
    assert(
      replay(session) == List(
        Event.Mouse(MouseEvent(Position(9, 4), MouseEventKind.Down, KeyModifiers.None)),
        Event.Mouse(MouseEvent(Position(11, 6), MouseEventKind.Drag, KeyModifiers.None)),
        Event.Mouse(MouseEvent(Position(11, 6), MouseEventKind.Up, KeyModifiers.None)),
        Event.Mouse(MouseEvent(Position(11, 6), MouseEventKind.ScrollUp, KeyModifiers.None, MouseButton.Unknown)),
        key(KeyCode.Char('k')),
      )
    )

  test("legacy X10 mouse: two clicks then a keystroke stay aligned"):
    val click   = (x: Int, y: Int) => Seq(Esc, '['.toInt, 'M'.toInt, 32, 32 + x, 32 + y)
    val session = click(10, 4) ++ click(11, 5) ++ text("q")
    assert(
      replay(session) == List(
        Event.Mouse(MouseEvent(Position(9, 3), MouseEventKind.Down, KeyModifiers.None)),
        Event.Mouse(MouseEvent(Position(10, 4), MouseEventKind.Down, KeyModifiers.None)),
        key(KeyCode.Char('q')),
      )
    )

  test("bracketed paste: payload with an embedded sequence, then normal typing resumes"):
    val payload = "line1\nESC-[A here\nline3"
    val session = text("a") ++ csi("200~") ++ text(payload) ++ csi("201~") ++ text("b")
    assert(replay(session) == List(key(KeyCode.Char('a')), Event.Paste(payload), key(KeyCode.Char('b'))))

  test("kitty: disambiguated keys, an astral code point and a functional key in one stream"):
    val session = csi("27u") ++ csi("13u") ++ csi("97;5u") ++ csi("128512u") ++ csi("57414u") ++ csi("57441u")
    assert(
      replay(session) == List(
        key(KeyCode.Escape),
        key(KeyCode.Enter),
        key(KeyCode.Char('a'), KeyModifiers.Ctrl),
        key(KeyCode.Char(0x1f600)),
        key(KeyCode.Enter), // KP_ENTER
        // LEFT_SHIFT produces nothing: a bare modifier press is not a key event in this model
      )
    )

  test("focus reporting interleaves with input without disturbing it"):
    val session = csi("I") ++ text("h") ++ csi("O") ++ text("i")
    assert(
      replay(session) == List(
        Event.FocusGained,
        key(KeyCode.Char('h')),
        Event.FocusLost,
        key(KeyCode.Char('i')),
      )
    )

  test("a UTF-8 emoji typed between two letters survives as one key"):
    val session = text("a") ++ text("😀") ++ text("b")
    assert(
      replay(session) == List(key(KeyCode.Char('a')), key(KeyCode.Char(0x1f600)), key(KeyCode.Char('b')))
    )

  test("a paste past the size cap consumes its terminator instead of leaking its tail as keystrokes"):
    // the cap is a memory guard, not a place to stop reading: whatever the decoder does not consume is dispatched
    // into the focused widget, where a `q` quits and an Enter submits
    val limit   = 1 << 20 // InputDecoder.PasteLimit
    val payload = "x" * (limit + 64)
    val session = text("a") ++ csi("200~") ++ text(payload) ++ csi("201~") ++ text("q")
    assert(replay(session) == List(key(KeyCode.Char('a')), Event.Paste("x" * limit), key(KeyCode.Char('q'))))

  test("a runaway CSI parameter string resynchronizes on the final byte"):
    // a verbose primary-DA reply can exceed the parameter budget; the remainder must not become 435 keypresses
    val session = text("a") ++ csi("1" * 500 + "A") ++ text("q")
    assert(replay(session) == List(key(KeyCode.Char('a')), key(KeyCode.Char('q'))))

  test("an OSC reply left in the buffer by another program is consumed whole"):
    // glyphora never asks for these, but tmux, a shell prompt, or a program run under `suspend` shares the tty
    val osc11   = Seq(Esc, ']'.toInt) ++ text("11;rgb:1a1a/1b1b/1c1c") ++ Seq(Esc, '\\'.toInt)
    val osc0    = Seq(Esc, ']'.toInt) ++ text("0;a window title") ++ Seq(7) // BEL-terminated form
    val session = text("a") ++ osc11 ++ text("b") ++ osc0 ++ text("c")
    assert(replay(session) == List(key(KeyCode.Char('a')), key(KeyCode.Char('b')), key(KeyCode.Char('c'))))

  test("DCS and APC replies are consumed whole"):
    val dcs     = Seq(Esc, 'P'.toInt) ++ text("1$r0m") ++ Seq(Esc, '\\'.toInt)   // DECRQSS status reply
    val apc     = Seq(Esc, '_'.toInt) ++ text("Gi=1;OK") ++ Seq(Esc, '\\'.toInt) // kitty graphics reply
    val session = text("a") ++ dcs ++ text("b") ++ apc ++ text("c")
    assert(replay(session) == List(key(KeyCode.Char('a')), key(KeyCode.Char('b')), key(KeyCode.Char('c'))))

  test("a horizontal wheel swipe between keystrokes stays aligned with the typing around it"):
    val session = text("a") ++ csi("<66;5;5M") ++ csi("<67;5;5M") ++ text("b")
    assert(
      replay(session) == List(
        key(KeyCode.Char('a')),
        Event.Mouse(MouseEvent(Position(4, 4), MouseEventKind.ScrollLeft, KeyModifiers.None, MouseButton.Unknown)),
        Event.Mouse(MouseEvent(Position(4, 4), MouseEventKind.ScrollRight, KeyModifiers.None, MouseButton.Unknown)),
        key(KeyCode.Char('b')),
      )
    )

  test("alt-modified control keys interleave with typing"):
    val session = text("a") ++ Seq(Esc, 0x7f) ++ text("b") ++ Seq(Esc, 0x0d) ++ text("c")
    assert(
      replay(session) == List(
        key(KeyCode.Char('a')),
        key(KeyCode.Backspace, KeyModifiers.Alt),
        key(KeyCode.Char('b')),
        key(KeyCode.Enter, KeyModifiers.Alt),
        key(KeyCode.Char('c')),
      )
    )
