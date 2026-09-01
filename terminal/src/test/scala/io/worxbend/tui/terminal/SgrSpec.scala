package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Color, Style}

import org.scalatest.funsuite.AnyFunSuite

/** Pins [[Sgr.sgr]], the absolute form: a whole style restated from a reset. */
final class SgrSpec extends AnyFunSuite:

  private val Esc = "\u001b"

  test("sgr for the default style is a bare reset"):
    assert(Sgr.sgr(Style.Default) == s"$Esc[0m")

  test("sgr encodes named foreground and background colors"):
    val style = Style.Default.withFg(Color.Cyan).withBg(Color.Black)
    assert(Sgr.sgr(style) == s"$Esc[0;36;40m")

  test("sgr encodes indexed and rgb colors"):
    assert(Sgr.sgr(Style.Default.withFg(Color.Indexed(208))) == s"$Esc[0;38;5;208m")
    assert(Sgr.sgr(Style.Default.withBg(Color.Rgb(1, 2, 3))) == s"$Esc[0;48;2;1;2;3m")

  test("sgr encodes modifiers after colors"):
    val style = Style.Default.withFg(Color.Red).bold.underline
    assert(Sgr.sgr(style) == s"$Esc[0;31;1;4m")

  test("sgr emits SGR 5 for blink and SGR 6 for rapid blink"):
    // the two are separate escape codes, so a single Blink flag could never reach SGR 6 whatever the API spelling
    assert(Sgr.sgr(Style.Default.blink) == s"$Esc[0;5m")
    assert(Sgr.sgr(Style.Default.rapidBlink) == s"$Esc[0;6m")
    assert(Sgr.sgr(Style.Default.blink.rapidBlink) == s"$Esc[0;5;6m")

  test("sgr encodes a styled underline via the colon SGR-4 extension"):
    import io.worxbend.tui.core.UnderlineStyle
    assert(Sgr.sgr(Style.Default.curlyUnderline) == s"$Esc[0;4:3m")
    assert(Sgr.sgr(Style.Default.withUnderlineStyle(UnderlineStyle.Double)) == s"$Esc[0;4:2m")

  test("sgr encodes a separate underline color with SGR 58"):
    assert(Sgr.sgr(Style.Default.withUnderlineColor(Color.Indexed(200))) == s"$Esc[0;58:5:200m")
    assert(Sgr.sgr(Style.Default.withUnderlineColor(Color.Rgb(1, 2, 3))) == s"$Esc[0;58:2::1:2:3m")

  test("plain underline modifier still emits bare 4, styled underline adds the 4:n selector"):
    assert(Sgr.sgr(Style.Default.underline) == s"$Esc[0;4m")
    assert(Sgr.sgr(Style.Default.underline.curlyUnderline) == s"$Esc[0;4;4:3m")
