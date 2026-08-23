package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class StyleModifierSpec extends AnyFunSuite:

  test("without clears a specific modifier while leaving others"):
    val style = Style.Default.bold.italic
    assert(style.modifiers.hasAny(Modifiers.Bold))
    val plain = style.notBold
    assert(!plain.modifiers.hasAny(Modifiers.Bold))
    assert(plain.modifiers.hasAny(Modifiers.Italic))

  test("Modifiers.without clears the requested flags at the bitset level"):
    val both = Modifiers.Bold | Modifiers.Underline
    assert(both.without(Modifiers.Bold) == Modifiers.Underline)

  test("withoutFg / withoutBg select the terminal default color explicitly"):
    val style = Style.Default.withFg(Color.Red).withBg(Color.Blue)
    assert(style.withoutFg.fg.contains(Color.Reset))
    assert(style.withoutBg.bg.contains(Color.Reset))
    assert(style.withoutFg.bg.contains(Color.Blue))

  test("a cleared color survives being layered onto a colored style"):
    // the reason `withoutFg` records `Color.Reset` instead of `None`: `None` is "silent about the foreground", which
    // `patch` resolves by falling back to the layer underneath, so the color the caller dropped would come back
    assert(Style.Default.withFg(Color.Red).patch(Style.Default.withoutFg).fg.contains(Color.Reset))
    assert(Style.Default.withBg(Color.Blue).patch(Style.Default.withoutBg).bg.contains(Color.Reset))

  test("clearing a modifier a style lacks changes nothing it renders, but is still recorded"):
    // the rendered attributes are untouched; what the call adds is the record that makes the clear survive `patch`,
    // which is the whole reason `without` exists
    assert(Style.Default.notReverse.modifiers == Style.Default.modifiers)
    assert(Style.Default.notReverse.clearedModifiers.hasAny(Modifiers.Reverse))

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
      assert(!style.modifiers.hasAny(flag))
      everyOne.filterNot(_ == flag).foreach(other => assert(style.modifiers.hasAny(other)))
    }

  test("hasAny is an ANY test, not an ALL test, when given more than one flag"):
    // the implementation is a non-zero bitwise AND, so a multi-flag argument asks "is at least one of these set?".
    // The method used to be called `has`, which read as "has all of these" right next to `hasAll` and meant the
    // opposite; the name now says which of the two it is.
    val boldItalic = Modifiers.Bold | Modifiers.Italic
    assert(boldItalic.hasAny(Modifiers.Bold | Modifiers.Underline))
    assert(!boldItalic.hasAny(Modifiers.Underline | Modifiers.Blink))
    assert(!boldItalic.hasAny(Modifiers.None))

  test("hasAll is the every-of test hasAny is not"):
    val boldItalic = Modifiers.Bold | Modifiers.Italic
    assert(boldItalic.hasAll(Modifiers.Bold | Modifiers.Italic))
    assert(!boldItalic.hasAll(Modifiers.Bold | Modifiers.Underline))
    assert(boldItalic.hasAll(Modifiers.None)) // no flag is required, so every style satisfies it

  test("show names the set flags in bit order"):
    assert((Modifiers.Italic | Modifiers.Bold).show == "Bold|Italic")
    assert(Modifiers.None.show == "None")
    assert((Modifiers.Bold | Modifiers.Italic).names == Seq("Bold", "Italic"))
