package io.worxbend.tui.core

/** The key a key event reports: a printable character, a named editing/navigation key, or a function key.
  *
  * `Char` carries a Unicode **code point**, not a UTF-16 code unit, so keys outside the Basic Multilingual Plane
  * (emoji, historic scripts) survive input decoding intact. `KeyCode.Char('q')` still compiles — a `scala.Char` widens
  * to `Int` in both expression and pattern position — but code that *binds* the payload receives an `Int`; use [[text]]
  * or `Character.toString(codePoint)` to turn it back into printable text.
  */
enum KeyCode:
  case Char(codePoint: Int)
  case Enter, Escape, Backspace, Tab, Delete, Insert, Home, End, PageUp, PageDown
  case Up, Down, Left, Right
  case F(n: Int)

  /** The text this key produces: the code point as a string for [[Char]], empty for every named key. */
  def text: String = this match
    case Char(codePoint) => Character.toString(codePoint)
    case _               => ""
