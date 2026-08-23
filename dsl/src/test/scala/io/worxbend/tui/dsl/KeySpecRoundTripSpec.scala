package io.worxbend.tui.dsl

import io.worxbend.tui.core.{KeyCode, KeyEvent, KeyModifiers}

import org.scalatest.funsuite.AnyFunSuite

/** The gap the `binding("+", …)` bug slipped through: a key spec string and the bytes a terminal sends are two
  * vocabularies that have to agree, and a unit test on either side alone passes while they disagree. `InputDecoder` is
  * `private[terminal]` and cannot be called from here, so this suite pins the spec side of the contract against the
  * exact [[KeyEvent]] values `InputDecoderSpec`/`InputFixtureSpec` pin the byte side against. Each case names the
  * sequence it corresponds to; change one side and the other fails.
  *
  * Equality is the whole contract: `KeyBindings.handle` matches its triggers with `==`, so a spec that parses to an
  * event the decoder never emits is a binding that silently never fires.
  *
  * `KeyEvent.parse` now lives in `tui-core`, so `InputDecoderRegressionSpec` can assert the two sides against each
  * other directly for the cases it covers. This suite stays because it covers the whole spec vocabulary, including the
  * sequences that spec has no byte fixture for here.
  */
final class KeySpecRoundTripSpec extends AnyFunSuite:

  /** Asserts the spec parses, and parses to precisely the event the decoder produces for `sentAs`. */
  private def roundTrip(spec: String, sentAs: String, expected: KeyEvent): Unit =
    KeyEvent.parse(spec) match
      case Right(parsed) => assert(parsed == expected, s"spec '$spec' does not match what the terminal sends ($sentAs)")
      case Left(problem) => fail(s"spec '$spec' did not parse: $problem")

  test("printable keys match the bytes a terminal sends"):
    roundTrip("q", "0x71", KeyEvent.of(KeyCode.Char('q')))
    roundTrip("+", "0x2b", KeyEvent.of(KeyCode.Char('+')))
    roundTrip("-", "0x2d", KeyEvent.of(KeyCode.Char('-')))
    roundTrip("?", "0x3f", KeyEvent.of(KeyCode.Char('?')))
    roundTrip("space", "0x20", KeyEvent.of(KeyCode.Char(' ')))

  /** Shift+letter reaches the process as the upper-case character with no modifier bit — `InputDecoder.printable`
    * passes the byte through untouched — which is why the parser has to preserve the case it was given.
    */
  test("a shifted letter matches the upper-case character the terminal sends"):
    roundTrip("G", "0x47", KeyEvent.of(KeyCode.Char('G')))
    roundTrip("Z", "0x5a", KeyEvent.of(KeyCode.Char('Z')))
    assert(KeyEvent.parse("G") != KeyEvent.parse("g"))

  test("named keys match their sequences"):
    roundTrip("enter", "0x0d", KeyEvent.of(KeyCode.Enter))
    roundTrip("tab", "0x09", KeyEvent.of(KeyCode.Tab))
    roundTrip("backspace", "0x7f", KeyEvent.of(KeyCode.Backspace))
    roundTrip("esc", "0x1b", KeyEvent.of(KeyCode.Escape))
    roundTrip("up", "CSI A", KeyEvent.of(KeyCode.Up))
    roundTrip("down", "CSI B", KeyEvent.of(KeyCode.Down))
    roundTrip("right", "CSI C", KeyEvent.of(KeyCode.Right))
    roundTrip("left", "CSI D", KeyEvent.of(KeyCode.Left))
    roundTrip("home", "CSI 1~", KeyEvent.of(KeyCode.Home))
    roundTrip("end", "CSI 4~", KeyEvent.of(KeyCode.End))
    roundTrip("pageup", "CSI 5~", KeyEvent.of(KeyCode.PageUp))
    roundTrip("pagedown", "CSI 6~", KeyEvent.of(KeyCode.PageDown))
    roundTrip("insert", "CSI 2~", KeyEvent.of(KeyCode.Insert))
    roundTrip("delete", "CSI 3~", KeyEvent.of(KeyCode.Delete))

  test("modified keys match their sequences"):
    roundTrip("ctrl+c", "0x03", KeyEvent(KeyCode.Char('c'), KeyModifiers.Ctrl))
    roundTrip("ctrl+s", "0x13", KeyEvent(KeyCode.Char('s'), KeyModifiers.Ctrl))
    // a control code carries no case, so the decoder always reports the lower-case letter
    roundTrip("ctrl+S", "0x13", KeyEvent(KeyCode.Char('s'), KeyModifiers.Ctrl))
    roundTrip("alt+x", "0x1b 0x78", KeyEvent(KeyCode.Char('x'), KeyModifiers.Alt))
    roundTrip("alt+G", "0x1b 0x47", KeyEvent(KeyCode.Char('G'), KeyModifiers.Alt))
    roundTrip("shift+tab", "CSI Z", KeyEvent(KeyCode.Tab, KeyModifiers.Shift))

  /** An ESC-prefixed control byte used to decode to the raw control character (`Char(127)`+Alt for Alt+Backspace)
    * rather than the named key, so these specs parsed fine and then matched nothing a terminal ever sent.
    */
  test("alt-modified named keys match their sequences"):
    roundTrip("alt+backspace", "0x1b 0x7f", KeyEvent(KeyCode.Backspace, KeyModifiers.Alt))
    roundTrip("alt+enter", "0x1b 0x0d", KeyEvent(KeyCode.Enter, KeyModifiers.Alt))
    roundTrip("alt+tab", "0x1b 0x09", KeyEvent(KeyCode.Tab, KeyModifiers.Alt))

  /** Ctrl+Space arrives as NUL and Ctrl+`\`/`]`/`^`/`_` as 0x1c-0x1f; all five used to decode to a bare `Char` with no
    * modifier, which both corrupted text inputs and left these specs unable to fire.
    */
  test("control keys outside the letter range match their bytes"):
    roundTrip("ctrl+space", "0x00", KeyEvent(KeyCode.Char(' '), KeyModifiers.Ctrl))
    roundTrip("ctrl+\\", "0x1c", KeyEvent(KeyCode.Char('\\'), KeyModifiers.Ctrl))
    roundTrip("ctrl+]", "0x1d", KeyEvent(KeyCode.Char(']'), KeyModifiers.Ctrl))
    roundTrip("ctrl+^", "0x1e", KeyEvent(KeyCode.Char('^'), KeyModifiers.Ctrl))
    roundTrip("ctrl+_", "0x1f", KeyEvent(KeyCode.Char('_'), KeyModifiers.Ctrl))

  test("the whole function-key range matches its sequences"):
    roundTrip("f1", "SS3 P", KeyEvent.of(KeyCode.F(1)))
    roundTrip("f4", "SS3 S", KeyEvent.of(KeyCode.F(4)))
    roundTrip("f5", "CSI 15~", KeyEvent.of(KeyCode.F(5)))
    roundTrip("f12", "CSI 24~", KeyEvent.of(KeyCode.F(12)))
    roundTrip("f13", "CSI 57376u", KeyEvent.of(KeyCode.F(13)))
    roundTrip("f35", "CSI 57398u", KeyEvent.of(KeyCode.F(35)))

  /** `InputDecoder.printable` recombines a surrogate pair into one code point, so a spec written as that character has
    * to become the same single `KeyCode.Char` rather than two stray UTF-16 units.
    */
  test("an astral character matches the recombined code point"):
    val partyPopper = "🎉"
    roundTrip(partyPopper, "0xd83c 0xdf89", KeyEvent.of(KeyCode.Char(partyPopper.codePointAt(0))))

  /** The self-maintaining half of the contract. Every `KeyCode` is something `InputDecoder` can emit, so every one of
    * them must be nameable in a spec — a code with no spec name is a key that simply cannot be bound through `binding`,
    * which is the exact shape of the original `"+"` defect.
    *
    * `KeyCode` has parameterized cases, so it has no `values` array to sweep; this match is the guard instead. It is
    * deliberately exhaustive and the module compiles with `-Werror`, so adding a case to the enum breaks this file
    * until that case is given a spec name (or explicitly declared self-naming).
    */
  private def specNameFor(code: KeyCode): Option[String] = code match
    case KeyCode.Enter     => Some("enter")
    case KeyCode.Escape    => Some("esc")
    case KeyCode.Backspace => Some("backspace")
    case KeyCode.Tab       => Some("tab")
    case KeyCode.Delete    => Some("delete")
    case KeyCode.Insert    => Some("insert")
    case KeyCode.Home      => Some("home")
    case KeyCode.End       => Some("end")
    case KeyCode.PageUp    => Some("pageup")
    case KeyCode.PageDown  => Some("pagedown")
    case KeyCode.Up        => Some("up")
    case KeyCode.Down      => Some("down")
    case KeyCode.Left      => Some("left")
    case KeyCode.Right     => Some("right")
    case KeyCode.Char(cp)  => Some(Character.toString(cp)) // a printable key names itself
    case KeyCode.F(n)      => Some(s"f$n")

  test("every named KeyCode the decoder can emit is nameable in a spec"):
    val named = Seq(
      KeyCode.Enter,
      KeyCode.Escape,
      KeyCode.Backspace,
      KeyCode.Tab,
      KeyCode.Delete,
      KeyCode.Insert,
      KeyCode.Home,
      KeyCode.End,
      KeyCode.PageUp,
      KeyCode.PageDown,
      KeyCode.Up,
      KeyCode.Down,
      KeyCode.Left,
      KeyCode.Right,
    )
    named.foreach: code =>
      val spec = specNameFor(code).getOrElse(fail(s"no spec name for $code"))
      assert(KeyEvent.parse(spec) == Right(KeyEvent.of(code)), s"spec '$spec' does not name $code")

  /** A printable key names itself, including the ones that are also spec syntax or need no shift-key ceremony. */
  test("every printable KeyCode names itself"):
    val printable = Seq('+', '-', '?', '/', 'a', 'G', ' ', '=', ':', ';', '<', '>', '[', ']', '\\', '*', '#')
    printable.foreach: char =>
      val code = KeyCode.Char(char)
      val spec = specNameFor(code).getOrElse(fail(s"no spec name for $code"))
      assert(KeyEvent.parse(spec) == Right(KeyEvent.of(code)), s"spec '$spec' does not name $code")

  /** `F(n)` is parameterized, so it is absent from `KeyCode.values` and needs its own sweep across the range
    * `InputDecoder` maps from the kitty functional-key block.
    */
  test("every function key the decoder can emit is nameable in a spec"):
    (1 to 35).foreach: n =>
      assert(KeyEvent.parse(s"f$n") == Right(KeyEvent.of(KeyCode.F(n))), s"spec 'f$n' does not name F($n)")

  /** The README's counter app, end to end: every spec it declares matches the bytes a terminal sends for that key. */
  test("every spec the documented counter app declares matches a real keypress"):
    roundTrip("+", "0x2b", KeyEvent.of(KeyCode.Char('+')))
    roundTrip("-", "0x2d", KeyEvent.of(KeyCode.Char('-')))
    roundTrip("q", "0x71", KeyEvent.of(KeyCode.Char('q')))
