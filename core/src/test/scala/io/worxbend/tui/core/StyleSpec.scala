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
    assert(style.modifiers.has(Modifiers.Bold))
    assert(style.modifiers.has(Modifiers.Italic))
    assert(!style.modifiers.has(Modifiers.Dim))

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
      assert(!others.has(flag))
    }

  test("patch overlays the other style's explicit choices"):
    val base    = Style.Default.withFg(Color.Red).bold
    val patched = base.patch(Style.Default.withFg(Color.Green).italic)
    assert(patched.fg.contains(Color.Green))
    assert(patched.modifiers.has(Modifiers.Bold))
    assert(patched.modifiers.has(Modifiers.Italic))

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
