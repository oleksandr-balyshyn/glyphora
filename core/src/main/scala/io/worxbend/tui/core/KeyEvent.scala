package io.worxbend.tui.core

/** A key press: which key, plus the modifier keys held.
  *
  * A standalone case class rather than an `Event` enum case so handler signatures like `KeyEvent => Boolean`
  * can take exactly the key payload without partially matching an `Event` (SPEC.md §3.1).
  */
final case class KeyEvent(code: KeyCode, modifiers: KeyModifiers)

object KeyEvent:
  def char(c: Char): KeyEvent = KeyEvent(KeyCode.Char(c), KeyModifiers.None)

  def of(code: KeyCode): KeyEvent = KeyEvent(code, KeyModifiers.None)
