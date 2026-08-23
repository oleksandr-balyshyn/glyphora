package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class StyleSpec extends AnyFunSuite:

  test("the default style has no colors and no modifiers"):
    assert(Style.Default.fg.isEmpty)
    assert(Style.Default.bg.isEmpty)
    assert(Style.Default.modifiers.isEmpty)

  test("withFg and withBg set the colors"):
    val style = Style.Default.withFg(Color.Cyan).withBg(Color.Black)
    assert(style.fg.contains(Color.Cyan))
    assert(style.bg.contains(Color.Black))

  test("modifier builders accumulate"):
    val style = Style.Default.bold.italic
    assert(style.modifiers.hasAny(Modifiers.Bold))
    assert(style.modifiers.hasAny(Modifiers.Italic))
    assert(!style.modifiers.hasAny(Modifiers.Dim))

  test("each modifier flag is distinct"):
    val all = Seq(
      Modifiers.Bold,
      Modifiers.Dim,
      Modifiers.Italic,
      Modifiers.Underline,
      Modifiers.Blink,
      Modifiers.Reverse,
      Modifiers.Hidden,
      Modifiers.CrossedOut,
    )
    all.foreach { flag =>
      val others = all.filterNot(_ == flag).foldLeft(Modifiers.None)(_ | _)
      assert(!others.hasAny(flag))
    }

  test("patch overlays the other style's explicit choices"):
    val base    = Style.Default.withFg(Color.Red).bold
    val patched = base.patch(Style.Default.withFg(Color.Green).italic)
    assert(patched.fg.contains(Color.Green))
    assert(patched.modifiers.hasAny(Modifiers.Bold))
    assert(patched.modifiers.hasAny(Modifiers.Italic))

  test("patch clears a modifier the other style deliberately turned off"):
    // `without` used to be erased by the first `patch`, because a Style recorded only the flags it set and never the
    // ones it cleared: the caller wrote `notBold` against a bold theme and got bold pixels with no error anywhere
    val patched = Style.Default.bold.patch(Style.Default.notBold)
    assert(!patched.modifiers.hasAny(Modifiers.Bold))

  test("a later builder overrides an earlier clear of the same flag, and the other way round"):
    assert(Style.Default.bold.patch(Style.Default.notBold.bold).modifiers.hasAny(Modifiers.Bold))
    assert(!Style.Default.bold.patch(Style.Default.bold.notBold).modifiers.hasAny(Modifiers.Bold))

  test("patching is associative down a theme to element to span chain"):
    val theme   = Style.Default.withFg(Color.Red).bold.italic
    val element = Style.Default.notBold.underline
    val span    = Style.Default.withFg(Color.Green).bold
    assert(theme.patch(element).patch(span) == theme.patch(element.patch(span)))
    val result  = theme.patch(element).patch(span)
    assert(result.fg.contains(Color.Green))
    assert(result.modifiers.hasAny(Modifiers.Bold))   // the span asked for it back
    assert(result.modifiers.hasAny(Modifiers.Italic)) // nobody below the theme spoke about it
    assert(result.modifiers.hasAny(Modifiers.Underline)) // the element added it

  test("patch keeps this style's colors where the other is silent"):
    val base    = Style.Default.withFg(Color.Red).withBg(Color.Blue)
    val patched = base.patch(Style.Default.bold)
    assert(patched.fg.contains(Color.Red))
    assert(patched.bg.contains(Color.Blue))

  test("underline color and style are independent fields, defaulting to unset"):
    assert(Style.Default.underlineColor.isEmpty)
    assert(Style.Default.underlineStyle == UnderlineStyle.None)
    val styled = Style.Default.withUnderlineColor(Color.Red).curlyUnderline
    assert(styled.underlineColor.contains(Color.Red))
    assert(styled.underlineStyle == UnderlineStyle.Curly)

  test("patch layers the other style's underline color and non-None underline style on top"):
    val base    = Style.Default.withUnderlineColor(Color.Red).doubleUnderline
    val patched = base.patch(Style.Default.dashedUnderline)
    assert(patched.underlineColor.contains(Color.Red))      // other left it unset → base kept
    assert(patched.underlineStyle == UnderlineStyle.Dashed) // other set it → other wins
    val kept = base.patch(Style.Default)
    assert(kept.underlineStyle == UnderlineStyle.Double) // other None → base kept

  test("toString spells out the modifier bitsets and elides default fields"):
    // the derived toString printed `Style(None,None,5,None,None,None)`; a failed cell assertion is the main place a
    // Style is ever read, so the bitsets are named and everything left at its default is left out
    assert(Style.Default.toString == "Style.Default")
    assert(Style.Default.bold.italic.toString == "Style(modifiers=Bold|Italic)")
    assert(Style.Default.withFg(Color.Red).notBold.toString == "Style(fg=Red, cleared=Bold)")
