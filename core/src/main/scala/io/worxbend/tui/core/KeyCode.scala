package io.worxbend.tui.core

/** The key a key event reports: a printable character, a named editing/navigation key, or a function key.
  *
  * `Char` carries a Unicode **code point**, not a UTF-16 code unit, so keys outside the Basic Multilingual Plane
  * (emoji, historic scripts) survive input decoding intact. `KeyCode.Char('q')` still compiles — a `scala.Char` widens
  * to `Int` in both expression and pattern position — but code that *binds* the payload receives an `Int`; use [[text]]
  * or `Character.toString(codePoint)` to turn it back into printable text.
  *
  * The last group — the three lock keys, `PrintScreen`, `Pause` and `Menu` — reaches an application only from a
  * terminal speaking the kitty keyboard protocol, with the one exception that `Menu` (the context-menu key beside the
  * right-hand Ctrl) also arrives from xterm as `CSI 29~`. Bind them as a convenience, never as the only route to a
  * command, because on an ordinary terminal they never arrive at all. The lock keys report the key *being pressed*;
  * glyphora has no notion of whether Caps Lock is currently on, and there is no event when the light changes on its
  * own. `text` is empty for all six, as it is for every other named key.
  */
enum KeyCode:
  case Char(codePoint: Int)
  case Enter, Escape, Backspace, Tab, Delete, Insert, Home, End, PageUp, PageDown
  case Up, Down, Left, Right
  case F(n: Int)
  case CapsLock, ScrollLock, NumLock, PrintScreen, Pause, Menu

  /** The text this key produces: the code point as a string for [[Char]], empty for every named key. */
  def text: String = this match
    case Char(codePoint) => Character.toString(codePoint)
    case _               => ""
