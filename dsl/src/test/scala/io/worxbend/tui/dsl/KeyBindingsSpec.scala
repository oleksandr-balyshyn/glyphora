package io.worxbend.tui.dsl

import io.worxbend.tui.core.{KeyCode, KeyEvent, KeyModifiers}

import org.scalatest.funsuite.AnyFunSuite

import java.util.Locale

final class KeyBindingsSpec extends AnyFunSuite:

  test("parseKey handles plain characters, named keys, and function keys"):
    assert(KeyBindings.parseKey("q") == Right(KeyEvent.of(KeyCode.Char('q'))))
    assert(KeyBindings.parseKey("enter") == Right(KeyEvent.of(KeyCode.Enter)))
    assert(KeyBindings.parseKey("esc") == Right(KeyEvent.of(KeyCode.Escape)))
    assert(KeyBindings.parseKey("space") == Right(KeyEvent.of(KeyCode.Char(' '))))
    assert(KeyBindings.parseKey("f5") == Right(KeyEvent.of(KeyCode.F(5))))
    assert(KeyBindings.parseKey("pgdn") == Right(KeyEvent.of(KeyCode.PageDown)))

  test("parseKey composes modifiers"):
    assert(KeyBindings.parseKey("ctrl+s") == Right(KeyEvent(KeyCode.Char('s'), KeyModifiers.Ctrl)))
    assert(KeyBindings.parseKey("shift+tab") == Right(KeyEvent(KeyCode.Tab, KeyModifiers.Shift)))
    assert(
      KeyBindings.parseKey("ctrl+alt+x") ==
        Right(KeyEvent(KeyCode.Char('x'), KeyModifiers.Ctrl | KeyModifiers.Alt))
    )

  test("parseKey reads a separator '+' as the plus key"):
    assert(KeyBindings.parseKey("+") == Right(KeyEvent.of(KeyCode.Char('+'))))
    assert(KeyBindings.parseKey("-") == Right(KeyEvent.of(KeyCode.Char('-'))))
    assert(KeyBindings.parseKey("ctrl++") == Right(KeyEvent(KeyCode.Char('+'), KeyModifiers.Ctrl)))
    assert(
      KeyBindings.parseKey("ctrl+shift++") ==
        Right(KeyEvent(KeyCode.Char('+'), KeyModifiers.Ctrl | KeyModifiers.Shift))
    )

  test("a '+' binding declares and fires like any other key"):
    var count    = 0
    val declared = KeyBindings(binding("+", "increment")(count += 1))
    assert(declared.handle(Key.char('+')))
    assert(!declared.handle(Key.char('-')))
    assert(count == 1)
    assert(declared.hints == Seq(("+", "increment")))

  /** A terminal reports Shift+G as `Char('G')` with no modifier, so lower-casing the spec would declare a binding that
    * can never fire — the vim-style `G`/`?` vocabulary depends on this.
    */
  test("parseKey keeps the case of a single-character key"):
    assert(KeyBindings.parseKey("G") == Right(KeyEvent.of(KeyCode.Char('G'))))
    assert(KeyBindings.parseKey("g") == Right(KeyEvent.of(KeyCode.Char('g'))))
    assert(KeyBindings.parseKey("G") != KeyBindings.parseKey("g"))
    assert(KeyBindings.parseKey("alt+G") == Right(KeyEvent(KeyCode.Char('G'), KeyModifiers.Alt)))

  test("parseKey is case-insensitive for modifier names and named keys"):
    assert(KeyBindings.parseKey("Enter") == Right(KeyEvent.of(KeyCode.Enter)))
    assert(KeyBindings.parseKey("ESC") == Right(KeyEvent.of(KeyCode.Escape)))
    assert(KeyBindings.parseKey("F5") == Right(KeyEvent.of(KeyCode.F(5))))
    assert(KeyBindings.parseKey("Ctrl+Shift+Tab") == parseCtrlShiftTab)
    assert(KeyBindings.parseKey("CTRL+ALT+delete") == parseCtrlAltDelete)

  /** Ctrl+letter reaches the decoder as a control code, which carries no case: it is always reported lower-case. */
  test("parseKey folds a ctrl-modified character to lower case"):
    assert(KeyBindings.parseKey("ctrl+S") == Right(KeyEvent(KeyCode.Char('s'), KeyModifiers.Ctrl)))
    assert(KeyBindings.parseKey("ctrl+S") == KeyBindings.parseKey("ctrl+s"))

  /** `InputDecoder` maps the kitty functional-key block onto `KeyCode.F(13)`…`F(35)`, so specs must reach that far. */
  test("parseKey names the whole function-key range the decoder can emit"):
    assert(KeyBindings.parseKey("f1") == Right(KeyEvent.of(KeyCode.F(1))))
    assert(KeyBindings.parseKey("f12") == Right(KeyEvent.of(KeyCode.F(12))))
    assert(KeyBindings.parseKey("f13") == Right(KeyEvent.of(KeyCode.F(13))))
    assert(KeyBindings.parseKey("f35") == Right(KeyEvent.of(KeyCode.F(35))))
    assert(KeyBindings.parseKey("f0").isLeft)
    assert(KeyBindings.parseKey("f36").isLeft)

  /** `KeyCode.Char` holds a code point and the decoder recombines surrogate pairs into one, so a spec written as an
    * astral character must parse as that one code point rather than as two stray UTF-16 units.
    */
  test("parseKey accepts an astral character as one key"):
    val partyPopper = "🎉"
    assert(partyPopper.length == 2)
    assert(KeyBindings.parseKey(partyPopper) == Right(KeyEvent.of(KeyCode.Char(partyPopper.codePointAt(0)))))
    assert(KeyBindings.parseKey("é") == Right(KeyEvent.of(KeyCode.Char('é'))))

  /** Named keys must fold the same way on every machine: `"Insert".toLowerCase` is `"ınsert"` under a Turkish locale,
    * so a default-locale fold would make those specs throw on some users' machines and not others.
    */
  test("parseKey resolves named keys the same way in every locale"):
    val original = Locale.getDefault
    try
      Locale.setDefault(Locale.forLanguageTag("tr"))
      assert(KeyBindings.parseKey("Insert") == Right(KeyEvent.of(KeyCode.Insert)))
      assert(KeyBindings.parseKey("INSERT") == Right(KeyEvent.of(KeyCode.Insert)))
      assert(KeyBindings.parseKey("Ctrl+Insert") == Right(KeyEvent(KeyCode.Insert, KeyModifiers.Ctrl)))
      assert(KeyBindings.parseKey("PageUp") == Right(KeyEvent.of(KeyCode.PageUp)))
    finally Locale.setDefault(original)

  /** The space bar is a key, so whitespace cannot be pure spec syntax any more than `+` could be pure separator. */
  test("parseKey reads padding whitespace as the space key when that is all there is"):
    assert(KeyBindings.parseKey(" ") == Right(KeyEvent.of(KeyCode.Char(' '))))
    assert(KeyBindings.parseKey(" ") == KeyBindings.parseKey("space"))
    assert(KeyBindings.parseKey("ctrl+ ") == Right(KeyEvent(KeyCode.Char(' '), KeyModifiers.Ctrl)))
    assert(KeyBindings.parseKey("ctrl+ ") == KeyBindings.parseKey("ctrl+space"))
    assert(KeyBindings.parseKey("  q  ") == Right(KeyEvent.of(KeyCode.Char('q'))))
    assert(KeyBindings.parseKey(" ctrl+s ") == Right(KeyEvent(KeyCode.Char('s'), KeyModifiers.Ctrl)))

  test("parseKey rejects nonsense"):
    assert(KeyBindings.parseKey("").isLeft)
    assert(KeyBindings.parseKey("   ").isLeft)
    assert(KeyBindings.parseKey("ctrl+").isLeft)
    assert(KeyBindings.parseKey("banana").isLeft)
    assert(KeyBindings.parseKey("a+b").isLeft)
    assert(KeyBindings.parseKey("ab").isLeft)

  private val parseCtrlShiftTab  = Right(KeyEvent(KeyCode.Tab, KeyModifiers.Ctrl | KeyModifiers.Shift))
  private val parseCtrlAltDelete = Right(KeyEvent(KeyCode.Delete, KeyModifiers.Ctrl | KeyModifiers.Alt))

  test("a malformed binding spec throws at declaration time"):
    assertThrows[IllegalArgumentException](binding("not-a-key", "boom")(()))

  test("handle fires the first matching binding only"):
    var fired    = List.empty[String]
    val bindings = KeyBindings(
      binding("q", "quit") { fired = "quit" :: fired },
      binding("ctrl+s", "save") { fired = "save" :: fired },
    )
    assert(bindings.handle(KeyEvent(KeyCode.Char('s'), KeyModifiers.Ctrl)))
    assert(!bindings.handle(KeyEvent.of(KeyCode.Char('x'))))
    assert(fired == List("save"))

  test("hints expose labels and descriptions in declaration order"):
    val bindings = KeyBindings(
      binding("q", "quit")(()),
      binding("?", "help")(()).copy(showInHints = false),
      binding("ctrl+s", "save")(()),
    )
    assert(bindings.hints == Seq("q" -> "quit", "ctrl+s" -> "save"))
