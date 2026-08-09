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

  /** One `notX` per `x`, so the negative half of the builder set is not missing arbitrary members. Each is checked
    * against a style carrying *every* modifier, which also pins that clearing one leaves the other seven alone.
    */
  test("every modifier builder has a matching clearing builder"):
    val all      = Style.Default.bold.dim.italic.underline.blink.reverse.hidden.crossedOut
    val cleared  = List(
      Modifiers.Bold       -> all.notBold,
      Modifiers.Dim        -> all.notDim,
      Modifiers.Italic     -> all.notItalic,
      Modifiers.Underline  -> all.notUnderline,
      Modifiers.Blink      -> all.notBlink,
      Modifiers.Reverse    -> all.notReverse,
      Modifiers.Hidden     -> all.notHidden,
      Modifiers.CrossedOut -> all.notCrossedOut,
    )
    val everyOne = List(
      Modifiers.Bold,
      Modifiers.Dim,
      Modifiers.Italic,
      Modifiers.Underline,
      Modifiers.Blink,
      Modifiers.Reverse,
      Modifiers.Hidden,
      Modifiers.CrossedOut,
    )
    cleared.foreach { (flag, style) =>
      assert(!style.modifiers.has(flag))
      everyOne.filterNot(_ == flag).foreach(other => assert(style.modifiers.has(other)))
    }

  test("has is an ANY test, not an ALL test, when given more than one flag"):
    // documented rather than changed: the name and the `Modifiers`-typed parameter read as "has all of these", but
    // the implementation is a non-zero bitwise AND. Changing it would silently flip the meaning of every existing
    // call site, so it is pinned here instead.
    val boldItalic = Modifiers.Bold | Modifiers.Italic
    assert(boldItalic.has(Modifiers.Bold | Modifiers.Underline))
    assert(!boldItalic.has(Modifiers.Underline | Modifiers.Blink))
    assert(!boldItalic.has(Modifiers.None))
