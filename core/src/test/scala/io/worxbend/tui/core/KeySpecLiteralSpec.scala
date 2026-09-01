package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** `key"…"` must answer exactly what `KeyEvent.parse` answers for a spec it accepts, and must fail the build for one it
  * does not. The positive cases below deliberately mirror the parser's own suite: the point of the interpolator is that
  * the compile-time vocabulary and the run-time one cannot drift apart.
  */
final class KeySpecLiteralSpec extends AnyFunSuite:

  test("a literal spec produces the event the parser produces"):
    assert(key"q" == KeyEvent(KeyCode.Char('q'), KeyModifiers.None))
    assert(key"ctrl+s" == KeyEvent(KeyCode.Char('s'), KeyModifiers.Ctrl))
    assert(key"f5" == KeyEvent(KeyCode.F(5), KeyModifiers.None))
    assert(key"esc" == KeyEvent(KeyCode.Escape, KeyModifiers.None))
    assert(key"space" == KeyEvent(KeyCode.Char(' '), KeyModifiers.None))
    assert(key"+" == KeyEvent(KeyCode.Char('+'), KeyModifiers.None))
    assert(key"ctrl++" == KeyEvent(KeyCode.Char('+'), KeyModifiers.Ctrl))
    assert(key"G" == KeyEvent(KeyCode.Char('G'), KeyModifiers.None))
    assert(key"volumeup" == KeyEvent(KeyCode.Media(MediaKey.RaiseVolume), KeyModifiers.None))

  test("several modifiers are all carried through"):
    val chord = key"ctrl+alt+shift+delete"
    assert(chord.code == KeyCode.Delete)
    assert(chord.modifiers.hasAll(KeyModifiers.Ctrl | KeyModifiers.Alt | KeyModifiers.Shift))

  test("the alias and the folding rules are the parser's, not a second set"):
    assert(key"backtab" == KeyEvent.parse("shift+tab").toOption.get)
    // Ctrl folds its letter to lower case, so `ctrl+S` names the same event `ctrl+s` does
    assert(key"ctrl+S" == key"ctrl+s")

  test("a misspelt modifier does not compile"):
    assertDoesNotCompile("""key"ctlr+s"""")

  test("an unknown key name does not compile"):
    assertDoesNotCompile("""key"nosuchkey"""")

  test("a Ctrl combination no terminal can deliver does not compile"):
    assertDoesNotCompile("""key"ctrl+i"""")

  test("modifiers with no key do not compile"):
    assertDoesNotCompile("""key"ctrl+"""")

  test("the compile error repeats the parser's own wording"):
    val message = scala.compiletime.testing.typeCheckErrors("""key"nosuchkey"""").map(_.message).mkString("; ")
    assert(message.contains("nosuchkey"), s"the message does not name the offending spec: $message")
    assert(message.contains("unknown key"), s"the message is not the parser's own: $message")

  test("a spec that is not a literal does not compile"):
    assertDoesNotCompile("""
      val fromConfig = "ctrl+s"
      key"$fromConfig"
    """)
