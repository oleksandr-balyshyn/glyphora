package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

import java.util.Locale

/** The key-spec vocabulary. It lives in core because everything that speaks it — an application's `binding("ctrl+s",
  * …)`, the docs, and `Pilot.press("ctrl+s")` in a test — goes through this one parser.
  */
final class KeyEventParseSpec extends AnyFunSuite:

  test("parse handles plain characters, named keys, and function keys"):
    assert(KeyEvent.parse("q") == Right(KeyEvent.of(KeyCode.Char('q'))))
    assert(KeyEvent.parse("enter") == Right(KeyEvent.of(KeyCode.Enter)))
    assert(KeyEvent.parse("esc") == Right(KeyEvent.of(KeyCode.Escape)))
    assert(KeyEvent.parse("space") == Right(KeyEvent.of(KeyCode.Char(' '))))
    assert(KeyEvent.parse("f5") == Right(KeyEvent.of(KeyCode.F(5))))
    assert(KeyEvent.parse("pgdn") == Right(KeyEvent.of(KeyCode.PageDown)))

  test("parse composes modifiers"):
    assert(KeyEvent.parse("ctrl+s") == Right(KeyEvent(KeyCode.Char('s'), KeyModifiers.Ctrl)))
    assert(KeyEvent.parse("shift+tab") == Right(KeyEvent(KeyCode.Tab, KeyModifiers.Shift)))
    assert(
      KeyEvent.parse("ctrl+alt+x") ==
        Right(KeyEvent(KeyCode.Char('x'), KeyModifiers.Ctrl | KeyModifiers.Alt))
    )

  test("parse reads a separator '+' as the plus key"):
    assert(KeyEvent.parse("+") == Right(KeyEvent.of(KeyCode.Char('+'))))
    assert(KeyEvent.parse("-") == Right(KeyEvent.of(KeyCode.Char('-'))))
    assert(KeyEvent.parse("ctrl++") == Right(KeyEvent(KeyCode.Char('+'), KeyModifiers.Ctrl)))
    assert(
      KeyEvent.parse("ctrl+shift++") ==
        Right(KeyEvent(KeyCode.Char('+'), KeyModifiers.Ctrl | KeyModifiers.Shift))
    )

  /** A terminal reports Shift+G as `Char('G')` with no modifier, so lower-casing the spec would declare a binding that
    * can never fire — the vim-style `G`/`?` vocabulary depends on this.
    */
  test("parse keeps the case of a single-character key"):
    assert(KeyEvent.parse("G") == Right(KeyEvent.of(KeyCode.Char('G'))))
    assert(KeyEvent.parse("g") == Right(KeyEvent.of(KeyCode.Char('g'))))
    assert(KeyEvent.parse("G") != KeyEvent.parse("g"))
    assert(KeyEvent.parse("alt+G") == Right(KeyEvent(KeyCode.Char('G'), KeyModifiers.Alt)))

  test("parse is case-insensitive for modifier names and named keys"):
    assert(KeyEvent.parse("Enter") == Right(KeyEvent.of(KeyCode.Enter)))
    assert(KeyEvent.parse("ESC") == Right(KeyEvent.of(KeyCode.Escape)))
    assert(KeyEvent.parse("F5") == Right(KeyEvent.of(KeyCode.F(5))))
    assert(KeyEvent.parse("Ctrl+Shift+Tab") == parseCtrlShiftTab)
    assert(KeyEvent.parse("CTRL+ALT+delete") == parseCtrlAltDelete)

  /** Ctrl+letter reaches the decoder as a control code, which carries no case: it is always reported lower-case. */
  test("parse folds a ctrl-modified character to lower case"):
    assert(KeyEvent.parse("ctrl+S") == Right(KeyEvent(KeyCode.Char('s'), KeyModifiers.Ctrl)))
    assert(KeyEvent.parse("ctrl+S") == KeyEvent.parse("ctrl+s"))

  /** `InputDecoder` maps the kitty functional-key block onto `KeyCode.F(13)`…`F(35)`, so specs must reach that far. */
  test("parse names the whole function-key range the decoder can emit"):
    assert(KeyEvent.parse("f1") == Right(KeyEvent.of(KeyCode.F(1))))
    assert(KeyEvent.parse("f12") == Right(KeyEvent.of(KeyCode.F(12))))
    assert(KeyEvent.parse("f13") == Right(KeyEvent.of(KeyCode.F(13))))
    assert(KeyEvent.parse("f35") == Right(KeyEvent.of(KeyCode.F(35))))
    assert(KeyEvent.parse("f0").isLeft)
    assert(KeyEvent.parse("f36").isLeft)

  /** `KeyCode.Char` holds a code point and the decoder recombines surrogate pairs into one, so a spec written as an
    * astral character must parse as that one code point rather than as two stray UTF-16 units.
    */
  test("parse accepts an astral character as one key"):
    val partyPopper = "🎉"
    assert(partyPopper.length == 2)
    assert(KeyEvent.parse(partyPopper) == Right(KeyEvent.of(KeyCode.Char(partyPopper.codePointAt(0)))))
    assert(KeyEvent.parse("é") == Right(KeyEvent.of(KeyCode.Char('é'))))

  /** The point of one shared vocabulary: the spec string and the constructor have to name the same event, including
    * above the Basic Multilingual Plane, where `KeyEvent.char(c: Char)` cannot reach at all.
    */
  test("parse and charAt name the same key for an astral character"):
    val grinningFace = 0x1f600
    assert(KeyEvent.parse("😀") == Right(KeyEvent.charAt(grinningFace)))
    assert(KeyEvent.charAt('q'.toInt) == KeyEvent.char('q'))

  /** Named keys must fold the same way on every machine: `"Insert".toLowerCase` is `"ınsert"` under a Turkish locale,
    * so a default-locale fold would make those specs throw on some users' machines and not others.
    */
  test("parse resolves named keys the same way in every locale"):
    val original = Locale.getDefault
    try
      Locale.setDefault(Locale.forLanguageTag("tr"))
      assert(KeyEvent.parse("Insert") == Right(KeyEvent.of(KeyCode.Insert)))
      assert(KeyEvent.parse("INSERT") == Right(KeyEvent.of(KeyCode.Insert)))
      assert(KeyEvent.parse("Ctrl+Insert") == Right(KeyEvent(KeyCode.Insert, KeyModifiers.Ctrl)))
      assert(KeyEvent.parse("PageUp") == Right(KeyEvent.of(KeyCode.PageUp)))
    finally Locale.setDefault(original)

  /** The space bar is a key, so whitespace cannot be pure spec syntax any more than `+` could be pure separator. */
  test("parse reads padding whitespace as the space key when that is all there is"):
    assert(KeyEvent.parse(" ") == Right(KeyEvent.of(KeyCode.Char(' '))))
    assert(KeyEvent.parse(" ") == KeyEvent.parse("space"))
    assert(KeyEvent.parse("ctrl+ ") == Right(KeyEvent(KeyCode.Char(' '), KeyModifiers.Ctrl)))
    assert(KeyEvent.parse("ctrl+ ") == KeyEvent.parse("ctrl+space"))
    assert(KeyEvent.parse("  q  ") == Right(KeyEvent.of(KeyCode.Char('q'))))
    assert(KeyEvent.parse(" ctrl+s ") == Right(KeyEvent(KeyCode.Char('s'), KeyModifiers.Ctrl)))

  /** `InputDecoder.decodeControl` matches the named keys before the Ctrl+letter range, so on a terminal without the
    * kitty keyboard protocol these five control codes arrive as Tab/Enter/Backspace/Escape and the Ctrl spelling can
    * never fire. Accepting it would put a dead key in the status-bar hints and the command palette.
    */
  test("parse rejects the ctrl specs a terminal reports as another key"):
    val rejected = Seq(
      "ctrl+i" -> ("Tab", "tab"),
      "ctrl+m" -> ("Enter", "enter"),
      "ctrl+j" -> ("Enter", "enter"),
      "ctrl+h" -> ("Backspace", "backspace"),
      "ctrl+[" -> ("Escape", "esc"),
    )
    rejected.foreach { case (spec, (arrivesAs, replacement)) =>
      KeyEvent.parse(spec) match
        case Right(event)  => fail(s"'$spec' should have been rejected, but parsed as $event")
        case Left(problem) =>
          assert(problem.contains(arrivesAs), s"'$spec' should name $arrivesAs: $problem")
          assert(problem.contains(s"\"$replacement\""), s"'$spec' should suggest \"$replacement\": $problem")
    }

  test("the rejection covers the spellings that fold onto the same key, and nothing else"):
    assert(KeyEvent.parse("ctrl+I").isLeft)       // folds to ctrl+i
    assert(KeyEvent.parse("ctrl+shift+i").isLeft) // still a Ctrl combination
    assert(KeyEvent.parse("i").isRight)           // no Ctrl, no collision
    assert(KeyEvent.parse("alt+i").isRight)
    assert(KeyEvent.parse("ctrl+k").isRight)
    assert(KeyEvent.parse("tab").isRight)

  test("parse rejects nonsense"):
    assert(KeyEvent.parse("").isLeft)
    assert(KeyEvent.parse("   ").isLeft)
    assert(KeyEvent.parse("ctrl+").isLeft)
    assert(KeyEvent.parse("banana").isLeft)
    assert(KeyEvent.parse("a+b").isLeft)
    assert(KeyEvent.parse("ab").isLeft)

  test("a bare modifier key has no spec spelling"):
    // KeyCode.Modifier exists and the input decoder can produce it, but binding one would fire part-way through
    // every chord that starts with that modifier. An application that wants "Ctrl is held" reads the key event.
    assert(KeyEvent.parse("shift").isLeft)
    assert(KeyEvent.parse("ctrl").isLeft)
    assert(KeyEvent.parse("leftshift").isLeft)

  test("a bare modifier key produces no text and prints readably"):
    val event = KeyEvent.of(KeyCode.Modifier(ModifierKey.LeftControl))
    assert(event.code.text.isEmpty)
    assert(event.toString == "KeyEvent(Modifier(LeftControl), None)")

  private val parseCtrlShiftTab  = Right(KeyEvent(KeyCode.Tab, KeyModifiers.Ctrl | KeyModifiers.Shift))
  private val parseCtrlAltDelete = Right(KeyEvent(KeyCode.Delete, KeyModifiers.Ctrl | KeyModifiers.Alt))

  test("the lock and system keys have spec names"):
    val expected = Seq(
      "capslock"    -> KeyCode.CapsLock,
      "scrolllock"  -> KeyCode.ScrollLock,
      "numlock"     -> KeyCode.NumLock,
      "printscreen" -> KeyCode.PrintScreen,
      "prtsc"       -> KeyCode.PrintScreen,
      "pause"       -> KeyCode.Pause,
      "menu"        -> KeyCode.Menu,
    )
    for (spec, code) <- expected do assert(KeyEvent.parse(spec) == Right(KeyEvent(code, KeyModifiers.None)))

  test("the lock and system key names are case-insensitive like every other named key"):
    assert(KeyEvent.parse("Menu") == KeyEvent.parse("menu"))
    assert(KeyEvent.parse("PrtSc") == KeyEvent.parse("printscreen"))
    assert(KeyEvent.parse("CapsLock") == KeyEvent.parse("capslock"))

  test("the lock and system keys take modifiers and produce no text"):
    assert(KeyEvent.parse("ctrl+menu") == Right(KeyEvent(KeyCode.Menu, KeyModifiers.Ctrl)))
    assert(KeyCode.Menu.text.isEmpty)
    assert(KeyCode.CapsLock.text.isEmpty)

  test("'backtab' names the same event as 'shift+tab'"):
    assert(KeyEvent.parse("backtab") == Right(KeyEvent(KeyCode.Tab, KeyModifiers.Shift)))
    assert(KeyEvent.parse("backtab") == KeyEvent.parse("shift+tab"))

  test("'backtab' is case-insensitive and unchanged by an explicit shift"):
    assert(KeyEvent.parse("BackTab") == KeyEvent.parse("backtab"))
    assert(KeyEvent.parse("shift+backtab") == KeyEvent.parse("backtab"))

  test("'ctrl+backtab' keeps ctrl alongside the shift the alias implies"):
    assert(KeyEvent.parse("ctrl+backtab") == Right(KeyEvent(KeyCode.Tab, KeyModifiers.Ctrl | KeyModifiers.Shift)))
    assert(KeyEvent.parse("ctrl+backtab") == KeyEvent.parse("ctrl+shift+tab"))

  test("'backtab' is an alias, not a new key: nothing else changes about tab"):
    assert(KeyEvent.parse("tab") == Right(KeyEvent(KeyCode.Tab, KeyModifiers.None)))
    assert(KeyEvent.parse("backtabx").isLeft)

  test("every media key has a spec name"):
    val expected = Seq(
      "play"          -> MediaKey.Play,
      "mediapause"    -> MediaKey.Pause,
      "playpause"     -> MediaKey.PlayPause,
      "reverse"       -> MediaKey.Reverse,
      "stop"          -> MediaKey.Stop,
      "fastforward"   -> MediaKey.FastForward,
      "rewind"        -> MediaKey.Rewind,
      "tracknext"     -> MediaKey.TrackNext,
      "next"          -> MediaKey.TrackNext,
      "trackprevious" -> MediaKey.TrackPrevious,
      "trackprev"     -> MediaKey.TrackPrevious,
      "prev"          -> MediaKey.TrackPrevious,
      "record"        -> MediaKey.Record,
      "volumedown"    -> MediaKey.LowerVolume,
      "voldown"       -> MediaKey.LowerVolume,
      "volumeup"      -> MediaKey.RaiseVolume,
      "volup"         -> MediaKey.RaiseVolume,
      "mute"          -> MediaKey.MuteVolume,
    )
    for (spec, key) <- expected do
      assert(KeyEvent.parse(spec) == Right(KeyEvent(KeyCode.Media(key), KeyModifiers.None)), s"spec '$spec'")

  test("media key names are case-insensitive and take modifiers"):
    assert(KeyEvent.parse("PlayPause") == KeyEvent.parse("playpause"))
    assert(KeyEvent.parse("ctrl+mute") == Right(KeyEvent(KeyCode.Media(MediaKey.MuteVolume), KeyModifiers.Ctrl)))

  test("'pause' stays the Pause/Break key, and the transport pause is a different spec"):
    // two physically different keys: Pause/Break above the arrows, and the transport pause on the media row
    assert(KeyEvent.parse("pause") == Right(KeyEvent.of(KeyCode.Pause)))
    assert(KeyEvent.parse("mediapause") == Right(KeyEvent.of(KeyCode.Media(MediaKey.Pause))))
    assert(KeyEvent.parse("pause") != KeyEvent.parse("mediapause"))

  test("a media key produces no text"):
    assert(KeyCode.Media(MediaKey.PlayPause).text.isEmpty)
