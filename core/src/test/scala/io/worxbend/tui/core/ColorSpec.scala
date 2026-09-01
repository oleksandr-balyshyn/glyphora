package io.worxbend.tui.core

import java.util.Locale

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

  test("the first sixteen palette indices approximate to their named colors"):
    assert(Color.approximateRgb(Color.Indexed(0)) == Color.approximateRgb(Color.Black))
    assert(Color.approximateRgb(Color.Indexed(1)) == Color.approximateRgb(Color.Red))
    assert(Color.approximateRgb(Color.Indexed(7)) == Color.approximateRgb(Color.White))
    assert(Color.approximateRgb(Color.Indexed(9)) == Color.approximateRgb(Color.BrightRed))
    assert(Color.approximateRgb(Color.Indexed(15)) == Color.approximateRgb(Color.BrightWhite))

  test("a palette index outside 0..255 clamps instead of failing"):
    assert(Color.approximateRgb(Color.Indexed(-1)) == Color.approximateRgb(Color.Black))
    assert(Color.approximateRgb(Color.Indexed(999)) == Color.approximateRgb(Color.Indexed(255)))

  test("gradient yields evenly spaced stops from first to last inclusive"):
    val stops = Color.gradient(Color.Rgb(0, 0, 0), Color.Rgb(0, 0, 100), 5)
    assert(stops.size == 5)
    assert(stops.head == Color.Rgb(0, 0, 0))
    assert(stops.last == Color.Rgb(0, 0, 100))
    assert(stops(2) == Color.Rgb(0, 0, 50))

  test("a single-step gradient is just the start color"):
    assert(Color.gradient(Color.Red, Color.Blue, 1) == Seq(Color.mix(Color.Red, Color.Blue, 0)))

  test("a NaN interpolation factor yields the start color, not black"):
    // clamping NaN left it NaN, and math.round(NaN) is 0, so a NaN from a divide-by-zero animation clock painted
    // every affected cell black instead of leaving the color alone
    assert(Color.mix(Color.Red, Color.Blue, Double.NaN) == Color.mix(Color.Red, Color.Blue, 0.0))
    assert(Color.lighten(Color.Red, Double.NaN) == Color.Rgb(205, 49, 49))
    assert(Color.darken(Color.Red, Double.NaN) == Color.Rgb(205, 49, 49))
    assert(Color.blend(Color.White, Color.Black, Double.NaN) == Color.Rgb(0, 0, 0))

  test("a non-positive step count yields no gradient stops"):
    // a caller sizing a gradient from an empty list got one stop back and painted a band that should not exist
    assert(Color.gradient(Color.Red, Color.Blue, 0).isEmpty)
    assert(Color.gradient(Color.Red, Color.Blue, -3).isEmpty)

  test("derived colors stay inside 0..255 even from an unclamped Rgb"):
    // the Rgb case class does not clamp its channels — only Color.rgb does — so an out-of-range literal used to
    // propagate through lighten/mix and reach the SGR encoder as a malformed escape
    assert(Color.lighten(Color.Rgb(999, -4, 0), 0.5) == Color.Rgb(255, 128, 128))
    assert(Color.approximateRgb(Color.Rgb(999, -4, 0)) == (255, 0, 0))
    assert(Color.mix(Color.Rgb(999, 0, 0), Color.Rgb(999, 0, 0), 1.0) == Color.Rgb(255, 0, 0))

  test("the fluent transformations agree with the functions they delegate to"):
    assert(Color.Red.lighten(0.5) == Color.lighten(Color.Red, 0.5))
    assert(Color.Red.darken(0.5) == Color.darken(Color.Red, 0.5))
    assert(Color.Red.mixedWith(Color.Blue, 0.25) == Color.mix(Color.Red, Color.Blue, 0.25))
    assert(Color.Red.over(Color.Black, 0.5) == Color.blend(Color.Red, Color.Black, 0.5))
    assert(Color.Red.gradientTo(Color.Blue, 3) == Color.gradient(Color.Red, Color.Blue, 3))

  test("a chain of transformations reads in the order it happens"):
    val chained = Color.Green.mixedWith(Color.Cyan, 0.3).darken(0.1)
    assert(chained == Color.darken(Color.mix(Color.Green, Color.Cyan, 0.3), 0.1))

  test("fromInt reads a packed 0x00RRGGBB literal"):
    assert(Color.fromInt(0xff8800) == Color.Rgb(255, 136, 0))
    assert(Color.fromInt(0x000000) == Color.Rgb(0, 0, 0))
    assert(Color.fromInt(0xffffff) == Color.Rgb(255, 255, 255))

  test("fromInt ignores the top byte rather than sign-extending it"):
    // `>>` keeps the sign bit, so a literal with the high bit set would give a negative red channel if the mask
    // were missing; every Int has to name a color for this to back a table of palette constants
    assert(Color.fromInt(0xffff8800) == Color.Rgb(255, 136, 0))
    assert(Color.fromInt(-1) == Color.Rgb(255, 255, 255))

  test("fromInt agrees with the text path for the same color"):
    assert(Color.hex("#ff8800").contains(Color.fromInt(0xff8800)))

  test("toInt packs a color back, clamping through approximateRgb"):
    assert(Color.toInt(Color.fromInt(0x123456)) == 0x123456)
    assert(Color.toInt(Color.Rgb(999, 0, 0)) == 0xff0000)
    assert(Color.toInt(Color.Black) == 0x000000)
    assert(Color.Rgb(255, 136, 0).packed == 0xff8800)

  test("parse reads every plain color name, whatever its case"):
    assert(Color.parse("reset") == Right(Color.Reset))
    assert(Color.parse("black") == Right(Color.Black))
    assert(Color.parse("RED") == Right(Color.Red))
    assert(Color.parse("Green") == Right(Color.Green))
    assert(Color.parse("yellow") == Right(Color.Yellow))
    assert(Color.parse("blue") == Right(Color.Blue))
    assert(Color.parse("magenta") == Right(Color.Magenta))
    assert(Color.parse("cyan") == Right(Color.Cyan))
    assert(Color.parse("white") == Right(Color.White))

  test("parse treats spaces, hyphens and underscores in a name as noise"):
    val spellings = Seq("BrightRed", "bright red", "bright-red", "BRIGHT_RED", "brightred", " light red ", "LightRed")
    assert(spellings.forall(spelling => Color.parse(spelling) == Right(Color.BrightRed)))

  test("parse reads light and bright as the same prefix for all eight variants"):
    assert(Color.parse("light black") == Right(Color.BrightBlack))
    assert(Color.parse("bright green") == Right(Color.BrightGreen))
    assert(Color.parse("light yellow") == Right(Color.BrightYellow))
    assert(Color.parse("light blue") == Right(Color.BrightBlue))
    assert(Color.parse("bright magenta") == Right(Color.BrightMagenta))
    assert(Color.parse("light cyan") == Right(Color.BrightCyan))
    assert(Color.parse("bright white") == Right(Color.BrightWhite))

  test("parse accepts the grey spellings a theme file is likely to use"):
    assert(Color.parse("grey") == Right(Color.BrightBlack))
    assert(Color.parse("gray") == Right(Color.BrightBlack))
    assert(Color.parse("dark grey") == Right(Color.BrightBlack))
    assert(Color.parse("dark-gray") == Right(Color.BrightBlack))
    assert(Color.parse("light grey") == Right(Color.White))
    assert(Color.parse("bright gray") == Right(Color.White))
    assert(Color.parse("silver") == Right(Color.White))

  test("parse reads hex colors in both lengths, with or without the hash"):
    assert(Color.parse("#1e88e5") == Right(Color.Rgb(30, 136, 229)))
    assert(Color.parse(" #F80 ") == Right(Color.Rgb(255, 136, 0)))
    assert(Color.parse("1e88e5") == Right(Color.Rgb(30, 136, 229)))

  test("parse reads a bare decimal run as a palette index, never as hex"):
    // `120` is both a valid #rgb color and a valid palette index; a configuration file's author who means the color
    // writes the `#`, so the index reading wins and the ambiguity is pinned here rather than left to a reader
    assert(Color.parse("0") == Right(Color.Indexed(0)))
    assert(Color.parse("120") == Right(Color.Indexed(120)))
    assert(Color.parse("255") == Right(Color.Indexed(255)))
    assert(Color.parse("#120") == Right(Color.Rgb(17, 34, 0)))
    assert(Color.parse("123456") == Right(Color.Rgb(18, 52, 86)))

  test("parse rejects what it cannot read, naming the offending input"):
    val rejected = Seq("256", "-1", "999", "puce", "#12345g", "#12", "light purple")
    rejected.foreach: input =>
      val message = Color.parse(input).swap.getOrElse(fail(s"expected '$input' to be rejected"))
      assert(message.contains(input), s"message for '$input' should name it: $message")

  test("parse rejects an empty or blank string"):
    assert(Color.parse("").isLeft)
    assert(Color.parse("   ").isLeft)

  test("parse folds case with the root locale, not the platform default"):
    // in a Turkish locale `"BLACK".toLowerCase` lowercases the I with no dot, so a default-locale fold would make a
    // perfectly ordinary theme file unreadable on those machines
    val previous = Locale.getDefault
    try
      Locale.setDefault(Locale.forLanguageTag("tr"))
      assert(Color.parse("BLACK") == Right(Color.Black))
      assert(Color.parse("BRIGHT WHITE") == Right(Color.BrightWhite))
    finally Locale.setDefault(previous)

  test("render writes each named color as the name this enum gives it"):
    assert(Color.Reset.render == "Reset")
    assert(Color.Red.render == "Red")
    assert(Color.BrightBlue.render == "BrightBlue")
    assert(Color.BrightWhite.render == "BrightWhite")

  test("render writes an Rgb as lowercase #rrggbb, clamping out-of-range channels"):
    assert(Color.Rgb(255, 136, 0).render == "#ff8800")
    assert(Color.Rgb(0, 0, 0).render == "#000000")
    assert(Color.Rgb(999, -5, 0).render == "#ff0000")

  test("render writes an Indexed as its bare decimal index, clamped"):
    assert(Color.Indexed(196).render == "196")
    assert(Color.Indexed(0).render == "0")
    assert(Color.Indexed(999).render == "255")

  test("every color round-trips through render and parse"):
    // `Color.values` does not exist here: the enum has parameterised cases, so the named ones are listed by hand
    val named  = Seq(
      Color.Reset,
      Color.Black,
      Color.Red,
      Color.Green,
      Color.Yellow,
      Color.Blue,
      Color.Magenta,
      Color.Cyan,
      Color.White,
      Color.BrightBlack,
      Color.BrightRed,
      Color.BrightGreen,
      Color.BrightYellow,
      Color.BrightBlue,
      Color.BrightMagenta,
      Color.BrightCyan,
      Color.BrightWhite,
    )
    val colors = named ++ Seq(Color.Rgb(1, 2, 3), Color.Indexed(0), Color.Indexed(42), Color.Indexed(255))
    colors.foreach: color =>
      assert(Color.parse(color.render) == Right(color), s"round trip failed for $color")
