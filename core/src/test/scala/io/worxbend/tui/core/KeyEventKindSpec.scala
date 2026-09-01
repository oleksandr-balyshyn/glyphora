package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** [[KeyEvent.kind]] and the compatibility promises around it.
  *
  * The field was added to a published value that applications pattern-match on constantly, so half of what is pinned
  * here is that nothing written before it existed had to change: the two-argument constructor, the two-argument
  * pattern, and the printed form of an ordinary press.
  */
final class KeyEventKindSpec extends AnyFunSuite:

  test("an event built without a kind is a press"):
    assert(KeyEvent(KeyCode.Enter, KeyModifiers.None).kind == KeyEventKind.Press)
    assert(KeyEvent.char('q').isPress)
    assert(KeyEvent.of(KeyCode.Tab).isPress)
    assert(KeyEvent.parse("ctrl+s").map(_.kind) == Right(KeyEventKind.Press))

  test("the two-argument pattern still matches, and ignores the kind"):
    // the reason `KeyEvent.unapply` is written out by hand: every application and all ten examples pattern-match this
    // way, and a synthesised three-position extractor would have turned every one of them into a compile error
    val release = KeyEvent(KeyCode.Char('q'), KeyModifiers.None, KeyEventKind.Release)
    val matched = release match
      case KeyEvent(KeyCode.Char(c), modifiers) => (c, modifiers)
      case other                                => fail(s"expected the two-argument pattern to match $other")
    assert(matched == ('q'.toInt, KeyModifiers.None))

  test("a press prints as it always did and anything else says so"):
    assert(KeyEvent.char('q').toString == "KeyEvent(Char(q), None)")
    assert(KeyEvent(KeyCode.Char('q'), KeyModifiers.None, KeyEventKind.Release).toString.contains("Release"))
    assert(KeyEvent(KeyCode.Char('q'), KeyModifiers.None, KeyEventKind.Repeat).toString.contains("Repeat"))

  test("events differing only in kind are different events"):
    val press = KeyEvent(KeyCode.Char('q'), KeyModifiers.None)
    assert(press != press.copy(kind = KeyEventKind.Release))
    assert(!press.copy(kind = KeyEventKind.Release).isPress)
    assert(press.copy(kind = KeyEventKind.Release).isRelease)
    assert(!press.copy(kind = KeyEventKind.Repeat).isRelease)
