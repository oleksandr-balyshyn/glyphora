package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Color, Style}

import org.scalatest.funsuite.AnyFunSuite

final class ColorDepthSpec extends AnyFunSuite:

  test("detection prefers COLORTERM, falls back to TERM, then 16 colors"):
    assert(ColorDepth.detect(Map("COLORTERM" -> "truecolor")) == ColorDepth.TrueColor)
    assert(ColorDepth.detect(Map("COLORTERM" -> "24bit", "TERM" -> "xterm")) == ColorDepth.TrueColor)
    assert(ColorDepth.detect(Map("TERM" -> "xterm-256color")) == ColorDepth.Ansi256)
    assert(ColorDepth.detect(Map("TERM" -> "vt100")) == ColorDepth.Ansi16)
    assert(ColorDepth.detect(Map.empty) == ColorDepth.Ansi16)

  test("NO_COLOR disables color regardless of terminal capability"):
    assert(ColorDepth.detect(Map("NO_COLOR" -> "1", "COLORTERM" -> "truecolor")) == ColorDepth.NoColor)
    assert(ColorDepth.detect(Map("NO_COLOR" -> "anything")) == ColorDepth.NoColor)

  test("an empty NO_COLOR does not disable color (per the no-color.org convention)"):
    assert(ColorDepth.detect(Map("NO_COLOR" -> "", "COLORTERM" -> "truecolor")) == ColorDepth.TrueColor)

  test("CLICOLOR_FORCE overrides NO_COLOR"):
    assert(
      ColorDepth.detect(
        Map("NO_COLOR" -> "1", "CLICOLOR_FORCE" -> "1", "COLORTERM" -> "truecolor")
      ) == ColorDepth.TrueColor
    )
    assert(ColorDepth.detect(Map("NO_COLOR" -> "1", "CLICOLOR_FORCE" -> "0")) == ColorDepth.NoColor)

  test("NoColor drops color codes from SGR but keeps text attributes"):
    val style = Style.Default.withFg(Color.Rgb(255, 0, 0)).withBg(Color.Blue).bold.underline
    assert(AnsiSequences.sgr(style, ColorDepth.NoColor) == "[0;1;4m")

  test("truecolor passes rgb through; 256 maps rgb into the palette"):
    val red = Color.Rgb(255, 0, 0)
    assert(ColorDepth.downsample(red, ColorDepth.TrueColor) == red)
    assert(ColorDepth.downsample(red, ColorDepth.Ansi256) == Color.Indexed(196))
    assert(ColorDepth.downsample(Color.Rgb(128, 128, 128), ColorDepth.Ansi256) == Color.Indexed(244))

  test("16-color terminals get the nearest named color"):
    assert(ColorDepth.downsample(Color.Rgb(255, 0, 0), ColorDepth.Ansi16) == Color.Red)
    assert(ColorDepth.downsample(Color.Rgb(0, 0, 0), ColorDepth.Ansi16) == Color.Black)
    assert(ColorDepth.downsample(Color.Indexed(196), ColorDepth.Ansi16) == Color.Red)
    assert(ColorDepth.downsample(Color.Cyan, ColorDepth.Ansi16) == Color.Cyan)

  test("sgr downsampling changes the emitted codes"):
    val style = Style.Default.withFg(Color.Rgb(255, 0, 0))
    assert(AnsiSequences.sgr(style, ColorDepth.TrueColor).contains("38;2;255;0;0"))
    assert(AnsiSequences.sgr(style, ColorDepth.Ansi256).contains("38;5;196"))
    assert(AnsiSequences.sgr(style, ColorDepth.Ansi16).contains("31"))
