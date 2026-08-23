package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** The bitset carried by every [[KeyEvent]] and [[MouseEvent]]. It is the sibling of [[Modifiers]] and is expected to
  * offer the same operations, which is what these cases pin.
  */
final class KeyModifiersSpec extends AnyFunSuite:

  test("without clears the requested modifier and keeps the rest"):
    val held = KeyModifiers.Ctrl | KeyModifiers.Shift | KeyModifiers.Alt
    val kept = held.without(KeyModifiers.Shift)
    assert(!kept.hasAny(KeyModifiers.Shift))
    assert(kept.hasAll(KeyModifiers.Ctrl | KeyModifiers.Alt))

  test("without a modifier that is not held changes nothing"):
    val held = KeyModifiers.Ctrl
    assert(held.without(KeyModifiers.Alt) == held)

  test("without every held modifier empties the set"):
    val held = KeyModifiers.Ctrl | KeyModifiers.Alt
    assert(held.without(KeyModifiers.Ctrl | KeyModifiers.Alt).isEmpty)
