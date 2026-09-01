package io.worxbend.tui.core

import java.util.Locale

/** A key event: which key, which modifier keys were held, and which moment of the keystroke this is.
  *
  * A standalone case class rather than an `Event` enum case so handler signatures like `KeyEvent => Boolean` can take
  * exactly the key payload without partially matching an `Event`.
  *
  * `kind` defaults to [[KeyEventKind.Press]] because that is what every terminal but a kitty-protocol one can report,
  * and because an application that never asked for release reporting never receives anything else — see
  * [[KeyEventKind]] for the whole of that story.
  */
final case class KeyEvent(code: KeyCode, modifiers: KeyModifiers, kind: KeyEventKind = KeyEventKind.Press):

  /** Whether this is a key going down, or a report from a terminal that cannot distinguish one. */
  def isPress: Boolean = kind == KeyEventKind.Press

  /** Whether this is a key coming back up. Only ever true against a terminal asked to report event types. */
  def isRelease: Boolean = kind == KeyEventKind.Release

  /** The derived `toString` prints two integers — the code point behind [[KeyCode.Char]] and the raw bitset behind
    * [[KeyModifiers]] — so `KeyEvent(KeyCode.Char('q'), KeyModifiers.Ctrl)` reads as `KeyEvent(Char(113),2)`. Key
    * events are the payload of nearly every DSL assertion, so both are spelled out here instead.
    */
  override def toString: String =
    // the kind is printed only when it is not a plain press, so the overwhelmingly common event keeps the shorter,
    // more readable spelling and every assertion message written before kinds existed still reads the same
    val kindText = if kind == KeyEventKind.Press then "" else s", $kind"
    s"KeyEvent($codeText, ${modifiers.show}$kindText)"

  /** The key itself, with a [[KeyCode.Char]]'s code point rendered as the character it stands for. */
  private def codeText: String = code match
    case KeyCode.Char(codePoint) => s"Char(${Character.toString(codePoint)})"
    case other                   => other.toString

object KeyEvent:

  /** Matches a key event on the two things nearly every handler asks about — `case KeyEvent(KeyCode.Enter, mods)`.
    *
    * Written out rather than left to the compiler so that [[KeyEvent.kind]] could be added without rewriting every
    * pattern in every application: a synthesised extractor would have grown a third position and turned every existing
    * `case KeyEvent(code, modifiers)` into a compile error. A handler that cares which moment of the keystroke it is
    * looking at reads `event.kind`, [[KeyEvent.isPress]] or [[KeyEvent.isRelease]] from the value itself.
    *
    * Ignoring the kind here is safe by construction: a terminal only reports repeats and releases to an application
    * that explicitly asked for them, so a pattern written before this existed cannot start matching twice per key.
    */
  def unapply(event: KeyEvent): (KeyCode, KeyModifiers) = (event.code, event.modifiers)

  /** A bare printable character with no modifiers.
    *
    * `Char` is a UTF-16 code unit, so this constructor can only name keys in the Basic Multilingual Plane. Use
    * [[charAt]] for anything above it — an emoji, say — which a terminal delivers as one key event carrying one code
    * point.
    */
  def char(c: Char): KeyEvent = charAt(c.toInt)

  /** A bare printable character named by its Unicode **code point**, with no modifiers.
    *
    * Named `charAt` rather than being an overload of [[char]] because `char(0x1F600)` would otherwise be an ambiguous
    * call that silently picks whichever alternative widens: an `Int` literal in `Char` range compiles as a `Char`, and
    * one above it as an `Int`, so the same spelling would mean two different things depending on the value.
    */
  def charAt(codePoint: Int): KeyEvent = KeyEvent(KeyCode.Char(codePoint), KeyModifiers.None)

  def of(code: KeyCode): KeyEvent = KeyEvent(code, KeyModifiers.None)

  /** Parses `"ctrl+shift+x"`-style specs: any of `ctrl`/`alt`/`shift` prefixes plus a key name.
    *
    * This is the single door between the string vocabulary an application writes (`binding("ctrl+s", "save")`, a key
    * name in the docs) and the [[KeyEvent]] ADT the `InputDecoder` produces. It lives in `tui-core` so applications,
    * the DSL's `binding` factory and `Pilot.press` in test-support all reach the same parser: a test that presses
    * `"ctrl+s"` exercises the same spelling the app declared, rather than a hand-translated ADT value that can drift
    * away from it.
    *
    * `+` is both the separator and a key in its own right, so it is only a separator when it terminates a modifier
    * name: modifiers are stripped from the front one `ctrl+`/`alt+`/`shift+` prefix at a time and whatever remains is
    * the key name. That makes `"+"` the plus key and `"ctrl++"` Ctrl+plus, rather than an empty spec.
    *
    * Modifier names and named keys are case-insensitive, but a single-character key keeps the case it was written in,
    * because that case is what the terminal reports: pressing Shift+G delivers `KeyCode.Char('G')`, so `"G"` is the
    * spec that matches it and `"shift+g"` is not. That holds on every terminal, not only the legacy ones:
    * `InputDecoder` folds a kitty-protocol `Shift`+letter back to the same uppercase-with-no-Shift encoding, so one
    * spec matches both. The one exception is Ctrl, which folds its key to lower case — a terminal cannot distinguish
    * Ctrl+S from Ctrl+Shift+S, and reports both as `Char('s')` with [[KeyModifiers.Ctrl]].
    *
    * Surrounding whitespace is stripped as a formatting convenience, with the same caveat `+` has: the space bar is a
    * key, so `" "` names it and so does the tail of `"ctrl+ "`. `"space"` remains the readable spelling.
    *
    * One key name is an alias rather than a key of its own: `"backtab"` is the widely used name for Shift+Tab, and
    * parses to exactly what `"shift+tab"` parses to — see [[backTabAlias]].
    *
    * Five Ctrl combinations are rejected rather than parsed — see [[UnreachableCtrlKeys]].
    *
    * @return
    *   the event the spec names, or `Left` with a message naming what is wrong with it. The message is meant to be
    *   shown to whoever wrote the spec, so callers should include it verbatim rather than replacing it.
    */
  def parse(spec: String): Either[String, KeyEvent] =
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
      else
        backTabAlias(keyName, modifiers) match
          case Some(event) => Right(event)
          case None        =>
            keyCodeFor(keyName).flatMap { code =>
              val folded = foldCtrl(code, modifiers)
              unreachable(folded, modifiers).toLeft(KeyEvent(folded, modifiers))
            }

  /** `backtab` is the name terminals and most other toolkits use for Shift+Tab — the key that moves focus backwards.
    *
    * glyphora reports that key as [[KeyCode.Tab]] with [[KeyModifiers.Shift]], which `"shift+tab"` already names, so
    * this is an alias and not a new key. It exists so that a reader who knows the key by its other name writes a spec
    * that fires rather than one the parser rejects as an unknown key.
    *
    * It is resolved here, in [[parse]], rather than in [[keyCodeFor]], because it names a code *and* a modifier and a
    * `KeyCode` result cannot carry the modifier half. Any modifiers written in front of it are kept, so
    * `"ctrl+backtab"` is Ctrl+Shift+Tab — exactly what `"ctrl+shift+tab"` already produces.
    */
  private def backTabAlias(keyName: String, modifiers: KeyModifiers): Option[KeyEvent] =
    Option.when(keyName.equalsIgnoreCase("backtab"))(KeyEvent(KeyCode.Tab, modifiers | KeyModifiers.Shift))

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
      case KeyCode.Char(codePoint) if modifiers.hasAny(KeyModifiers.Ctrl) =>
        KeyCode.Char(Character.toLowerCase(codePoint))
      case other                                                          => other

  /** The rejection message for a Ctrl spec no terminal can deliver, or `None` when the spec is fine. */
  private def unreachable(code: KeyCode, modifiers: KeyModifiers): Option[String] =
    if !modifiers.hasAny(KeyModifiers.Ctrl) then None
    else
      code match
        case KeyCode.Char(codePoint) =>
          UnreachableCtrlKeys.get(codePoint).map { (arrivesAs, insteadSpec) =>
            val spelled = String.valueOf(Character.toChars(codePoint))
            s"'ctrl+$spelled' is indistinguishable from $arrivesAs on terminals without the kitty keyboard " +
              s"protocol; bind \"$insteadSpec\" instead"
          }
        case _                       => None

  private def keyCodeFor(name: String): Either[String, KeyCode] =
    // ROOT, not the default locale: in a Turkish locale `"Insert".toLowerCase` is `"ınsert"`, which matches nothing.
    name.toLowerCase(Locale.ROOT) match
      case "enter"                                => Right(KeyCode.Enter)
      case "esc" | "escape"                       => Right(KeyCode.Escape)
      case "tab"                                  => Right(KeyCode.Tab)
      case "space"                                => Right(KeyCode.Char(' '))
      case "backspace"                            => Right(KeyCode.Backspace)
      case "delete" | "del"                       => Right(KeyCode.Delete)
      case "insert"                               => Right(KeyCode.Insert)
      case "home"                                 => Right(KeyCode.Home)
      case "end"                                  => Right(KeyCode.End)
      case "pageup" | "pgup"                      => Right(KeyCode.PageUp)
      case "pagedown" | "pgdn"                    => Right(KeyCode.PageDown)
      case "up"                                   => Right(KeyCode.Up)
      case "down"                                 => Right(KeyCode.Down)
      case "left"                                 => Right(KeyCode.Left)
      case "right"                                => Right(KeyCode.Right)
      case "capslock"                             => Right(KeyCode.CapsLock)
      case "scrolllock"                           => Right(KeyCode.ScrollLock)
      case "numlock"                              => Right(KeyCode.NumLock)
      case "printscreen" | "prtsc"                => Right(KeyCode.PrintScreen)
      case "pause"                                => Right(KeyCode.Pause)
      case "menu"                                 => Right(KeyCode.Menu)
      // the hardware transport and volume keys. `"pause"` above is the Pause/Break key, so the media one needs its own
      // spelling: `"mediapause"`. `"playpause"` is the single toggle button most keyboards actually have.
      case "play"                                 => Right(KeyCode.Media(MediaKey.Play))
      case "mediapause"                           => Right(KeyCode.Media(MediaKey.Pause))
      case "playpause"                            => Right(KeyCode.Media(MediaKey.PlayPause))
      case "reverse"                              => Right(KeyCode.Media(MediaKey.Reverse))
      case "stop"                                 => Right(KeyCode.Media(MediaKey.Stop))
      case "fastforward"                          => Right(KeyCode.Media(MediaKey.FastForward))
      case "rewind"                               => Right(KeyCode.Media(MediaKey.Rewind))
      case "next" | "tracknext"                   => Right(KeyCode.Media(MediaKey.TrackNext))
      case "prev" | "trackprev" | "trackprevious" => Right(KeyCode.Media(MediaKey.TrackPrevious))
      case "record"                               => Right(KeyCode.Media(MediaKey.Record))
      case "volumedown" | "voldown"               => Right(KeyCode.Media(MediaKey.LowerVolume))
      case "volumeup" | "volup"                   => Right(KeyCode.Media(MediaKey.RaiseVolume))
      case "mute"                                 => Right(KeyCode.Media(MediaKey.MuteVolume))
      case f if f.startsWith("f") && f.drop(1).toIntOption.exists(n => n >= 1 && n <= MaxFunctionKey) =>
        Right(KeyCode.F(f.drop(1).toInt))
      // There is deliberately no spelling for a bare modifier key here — no "shift", no "leftctrl" — even though
      // `KeyCode.Modifier` exists and the decoder can produce it. A binding on a bare modifier would fire while the
      // user was part-way through typing every chord that starts with it, which is never what the author meant. An
      // application that genuinely wants "Ctrl is being held" reads `KeyCode.Modifier` from its own key handler.
      case _ if name.codePointCount(0, name.length) == 1 => Right(KeyCode.Char(name.codePointAt(0)))
      case _                                             => Left(s"unknown key '$name'")

  /** `InputDecoder` reports the kitty protocol's F13-F35 block, so a spec must be able to name those keys too. */
  private val MaxFunctionKey = 35

  private val ModifierNames: Map[String, KeyModifiers] =
    Map("ctrl" -> KeyModifiers.Ctrl, "alt" -> KeyModifiers.Alt, "shift" -> KeyModifiers.Shift)

  /** The Ctrl combinations a terminal cannot report as themselves, keyed by the character's code point: the name the
    * key actually arrives under, and the spec to bind instead.
    *
    * ASCII gives these control codes two meanings and the older ones win. `InputDecoder.decodeControl` matches the
    * named keys before the Ctrl+letter range, so `0x09` is `Tab`, `0x0d`/`0x0a` are `Enter`, `0x7f`/`0x08` are
    * `Backspace` and `0x1b` starts an escape sequence — this table mirrors exactly those cases. A terminal speaking the
    * kitty keyboard protocol *can* tell them apart, but a binding that only works there is worse than no binding: it
    * still shows up in the status-bar hints and the command palette, advertising a key that does nothing.
    */
  private val UnreachableCtrlKeys: Map[Int, (String, String)] = Map(
    'i'.toInt -> ("Tab", "tab"),
    'm'.toInt -> ("Enter", "enter"),
    'j'.toInt -> ("Enter", "enter"),
    'h'.toInt -> ("Backspace", "backspace"),
    '['.toInt -> ("Escape", "esc"),
  )
