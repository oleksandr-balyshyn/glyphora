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
    val base = Style.Default.withFg(Color.Red).bold
    val patched = base.patch(Style.Default.withFg(Color.Green).italic)
    assert(patched.fg.contains(Color.Green))
    assert(patched.modifiers.has(Modifiers.Bold))
    assert(patched.modifiers.has(Modifiers.Italic))

  test("patch keeps this style's colors where the other is silent"):
    val base = Style.Default.withFg(Color.Red).withBg(Color.Blue)
    val patched = base.patch(Style.Default.bold)
    assert(patched.fg.contains(Color.Red))
    assert(patched.bg.contains(Color.Blue))
