package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Color, Style}

import org.scalatest.funsuite.AnyFunSuite

final class AnsiSequencesSpec extends AnyFunSuite:

  private val Esc = ""

  test("moveTo converts zero-based coordinates to one-based ANSI"):
    assert(AnsiSequences.moveTo(0, 0) == s"$Esc[1;1H")
    assert(AnsiSequences.moveTo(9, 4) == s"$Esc[5;10H")

  test("sgr for the default style is a bare reset"):
    assert(AnsiSequences.sgr(Style.Default) == s"$Esc[0m")

  test("sgr encodes named foreground and background colors"):
    val style = Style.Default.withFg(Color.Cyan).withBg(Color.Black)
    assert(AnsiSequences.sgr(style) == s"$Esc[0;36;40m")

  test("sgr encodes indexed and rgb colors"):
    assert(AnsiSequences.sgr(Style.Default.withFg(Color.Indexed(208))) == s"$Esc[0;38;5;208m")
    assert(AnsiSequences.sgr(Style.Default.withBg(Color.Rgb(1, 2, 3))) == s"$Esc[0;48;2;1;2;3m")

  test("sgr encodes modifiers after colors"):
    val style = Style.Default.withFg(Color.Red).bold.underline
    assert(AnsiSequences.sgr(style) == s"$Esc[0;31;1;4m")
