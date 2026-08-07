package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class StyleModifierSpec extends AnyFunSuite:

  test("without clears a specific modifier while leaving others"):
    val style = Style.Default.bold.italic
    assert(style.modifiers.has(Modifiers.Bold))
    val plain = style.notBold
    assert(!plain.modifiers.has(Modifiers.Bold))
    assert(plain.modifiers.has(Modifiers.Italic))

  test("Modifiers.without clears the requested flags at the bitset level"):
    val both = Modifiers.Bold | Modifiers.Underline
    assert(both.without(Modifiers.Bold) == Modifiers.Underline)

  test("withoutFg / withoutBg restore the terminal default color"):
    val style = Style.Default.withFg(Color.Red).withBg(Color.Blue)
    assert(style.withoutFg.fg.isEmpty)
    assert(style.withoutBg.bg.isEmpty)
    assert(style.withoutFg.bg.contains(Color.Blue))

  test("removing a modifier is idempotent on a style that lacks it"):
    assert(Style.Default.notReverse == Style.Default)

  test("has is an ANY test, not an ALL test, when given more than one flag"):
    // documented rather than changed: the name and the `Modifiers`-typed parameter read as "has all of these", but
    // the implementation is a non-zero bitwise AND. Changing it would silently flip the meaning of every existing
    // call site, so it is pinned here instead.
    val boldItalic = Modifiers.Bold | Modifiers.Italic
    assert(boldItalic.has(Modifiers.Bold | Modifiers.Underline))
    assert(!boldItalic.has(Modifiers.Underline | Modifiers.Blink))
    assert(!boldItalic.has(Modifiers.None))
