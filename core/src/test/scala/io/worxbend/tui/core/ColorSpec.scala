package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class ColorSpec extends AnyFunSuite:

  test("hex parses #rrggbb"):
    assert(Color.hex("#ff8800") == Some(Color.Rgb(255, 136, 0)))

  test("hex parses without the leading hash and is case-insensitive"):
    assert(Color.hex("FF8800") == Some(Color.Rgb(255, 136, 0)))

  test("hex expands the #rgb short form nibble-by-nibble"):
    assert(Color.hex("#f80") == Some(Color.Rgb(255, 136, 0)))

  test("hex rejects malformed input"):
    assert(Color.hex("#12").isEmpty)
    assert(Color.hex("nothex").isEmpty)
    assert(Color.hex("#12345g").isEmpty)

  test("rgb clamps channels to 0..255"):
    assert(Color.rgb(300, -5, 128) == Color.Rgb(255, 0, 128))

  test("lighten moves toward white, darken toward black"):
    assert(Color.lighten(Color.Black, 1.0) == Color.Rgb(255, 255, 255))
    assert(Color.darken(Color.White, 1.0) == Color.Rgb(0, 0, 0))
    assert(Color.lighten(Color.Red, 0.0) == Color.Rgb(205, 49, 49)) // unchanged at amount 0

  test("mix blends two colors in RGB space"):
    val black = Color.Rgb(0, 0, 0)
    val white = Color.Rgb(255, 255, 255)
    assert(Color.mix(black, white, 0.5) == Color.Rgb(128, 128, 128))
    assert(Color.mix(black, white, 0.0) == black)
    assert(Color.mix(black, white, 1.0) == white)

  test("blend composites foreground over background at an opacity"):
    assert(Color.blend(Color.Rgb(255, 255, 255), Color.Rgb(0, 0, 0), 0.25) == Color.Rgb(64, 64, 64))

  test("bright variants approximate to their conventional RGB"):
    assert(Color.approximateRgb(Color.BrightRed) == (255, 0, 0))
    assert(Color.approximateRgb(Color.BrightWhite) == (255, 255, 255))
    assert(Color.approximateRgb(Color.BrightBlack) == (127, 127, 127))
