package io.worxbend.tui.dsl

import io.worxbend.tui.core.{KeyCode, KeyEvent, KeyModifiers}

import java.util.Locale

/** One declared application key: the trigger, the short label shown in status-bar hints, a description for the help
  * overlay, and the action.
  */
final case class KeyBinding(
    trigger: KeyEvent,
    label: String,
    description: String,
    action: () => Unit,
    showInHints: Boolean = true,
)

/** Declares one binding from a key spec string: `"q"`, `"ctrl+s"`, `"shift+tab"`, `"esc"`, `"f2"`, `"up"`, `"+"`… A
  * malformed spec is a programmer error and throws at construction (bindings are static app declarations).
  */
def binding(key: String, description: String)(action: => Unit): KeyBinding =
  KeyBindings.parseKey(key) match
    case Right(trigger) => KeyBinding(trigger, key, description, () => action)
    case Left(problem)  => throw IllegalArgumentException(s"bad key spec '$key': $problem")

/** The application's declared keys (bubbles' `key`+`help` pattern): one declaration drives dispatch, the status-bar
  * hints, and the help overlay. `TuiApp` consults these for events no element consumed.
  */
final class KeyBindings private (val bindings: Seq[KeyBinding]):

  /** Runs the first matching binding; `true` if one fired. */
  def handle(event: KeyEvent): Boolean =
    bindings.find(_.trigger == event).exists { bound =>
      bound.action()
      true
    }

  /** `(label, description)` pairs for the status bar, in declaration order. */
  def hints: Seq[(String, String)] =
    bindings.filter(_.showInHints).map(bound => (bound.label, bound.description))

  def ++(other: KeyBindings): KeyBindings = new KeyBindings(bindings ++ other.bindings)

object KeyBindings:

  val empty: KeyBindings = new KeyBindings(Seq.empty)

  def apply(declared: KeyBinding*): KeyBindings = new KeyBindings(declared)

  /** Parses `"ctrl+shift+x"`-style specs: any of `ctrl`/`alt`/`shift` prefixes plus a key name.
    *
    * `+` is both the separator and a key in its own right, so it is only a separator when it terminates a modifier
    * name: modifiers are stripped from the front one `ctrl+`/`alt+`/`shift+` prefix at a time and whatever remains is
    * the key name. That makes `"+"` the plus key and `"ctrl++"` Ctrl+plus, rather than an empty spec.
    *
    * Modifier names and named keys are case-insensitive, but a single-character key keeps the case it was written in,
    * because that case is what the terminal reports: pressing Shift+G delivers `KeyCode.Char('G')`, so `"G"` is the
    * spec that matches it and `"shift+g"` is not. The one exception is Ctrl, which folds its key to lower case — a
    * terminal cannot distinguish Ctrl+S from Ctrl+Shift+S, and reports both as `Char('s')` with [[KeyModifiers.Ctrl]].
    *
    * Surrounding whitespace is stripped as a formatting convenience, with the same caveat `+` has: the space bar is a
    * key, so `" "` names it and so does the tail of `"ctrl+ "`. `"space"` remains the readable spelling.
    */
  def parseKey(spec: String): Either[String, KeyEvent] =
    @annotation.tailrec
    def stripModifiers(rest: String, modifiers: KeyModifiers): (String, KeyModifiers) =
      ModifierNames.collectFirst {
        case (name, modifier) if rest.regionMatches(true, 0, s"$name+", 0, name.length + 1) => (name, modifier)
      } match
        case Some((name, modifier)) => stripModifiers(rest.drop(name.length + 1), modifiers | modifier)
        case None                   => (rest, modifiers)

    if spec.isEmpty then Left("empty spec")
    else
      val (rawKeyName, modifiers) = stripModifiers(dropPadding(spec, leadingOnly = true), KeyModifiers.None)
      val keyName                 = dropPadding(rawKeyName, leadingOnly = false)
      if keyName.isEmpty then Left("no key name (only modifiers)")
      else keyCodeFor(keyName).map(code => KeyEvent(foldCtrl(code, modifiers), modifiers))

  /** Strips a spec's whitespace padding — but never all of it, because a value that is *only* whitespace is the space
    * key rather than padding. Leading padding comes off before the modifier scan so `"ctrl+ "` keeps its trailing space
    * for the key name; the key name itself is then padded on both sides.
    */
  private def dropPadding(text: String, leadingOnly: Boolean): String =
    if text.isBlank then text
    else if leadingOnly then text.dropWhile(_.isWhitespace)
    else text.trim

  /** Ctrl+letter arrives as a control code, which carries no case, so the decoder reports it lower-case. Folding here
    * keeps `"ctrl+S"` from declaring a binding that can never fire.
    */
  private def foldCtrl(code: KeyCode, modifiers: KeyModifiers): KeyCode =
    code match
      case KeyCode.Char(codePoint) if modifiers.has(KeyModifiers.Ctrl) =>
        KeyCode.Char(Character.toLowerCase(codePoint))
      case other                                                       => other

  private def keyCodeFor(name: String): Either[String, KeyCode] =
    // ROOT, not the default locale: in a Turkish locale `"Insert".toLowerCase` is `"ınsert"`, which matches nothing.
    name.toLowerCase(Locale.ROOT) match
      case "enter"             => Right(KeyCode.Enter)
      case "esc" | "escape"    => Right(KeyCode.Escape)
      case "tab"               => Right(KeyCode.Tab)
      case "space"             => Right(KeyCode.Char(' '))
      case "backspace"         => Right(KeyCode.Backspace)
      case "delete" | "del"    => Right(KeyCode.Delete)
      case "insert"            => Right(KeyCode.Insert)
      case "home"              => Right(KeyCode.Home)
      case "end"               => Right(KeyCode.End)
      case "pageup" | "pgup"   => Right(KeyCode.PageUp)
      case "pagedown" | "pgdn" => Right(KeyCode.PageDown)
      case "up"                => Right(KeyCode.Up)
      case "down"              => Right(KeyCode.Down)
      case "left"              => Right(KeyCode.Left)
      case "right"             => Right(KeyCode.Right)
      case f if f.startsWith("f") && f.drop(1).toIntOption.exists(n => n >= 1 && n <= MaxFunctionKey) =>
        Right(KeyCode.F(f.drop(1).toInt))
      case _ if name.codePointCount(0, name.length) == 1 => Right(KeyCode.Char(name.codePointAt(0)))
      case _                                             => Left(s"unknown key '$name'")

  /** `InputDecoder` reports the kitty protocol's F13-F35 block, so a spec must be able to name those keys too. */
  private val MaxFunctionKey = 35

  private val ModifierNames: Map[String, KeyModifiers] =
    Map("ctrl" -> KeyModifiers.Ctrl, "alt" -> KeyModifiers.Alt, "shift" -> KeyModifiers.Shift)
