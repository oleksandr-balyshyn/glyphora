package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** Pins the text [[Style.asSource]] and [[Color.asSource]] produce.
  *
  * The promise these two make is narrow and easy to break by accident: the string must be Scala that compiles and
  * evaluates back to an equal value. Every expectation below is written out twice — once as the expected string, once
  * as the real expression next to it — so the file itself proves the text compiles, and an assertion proves the
  * expression it names is the value it came from.
  */
final class StyleSourceSpec extends AnyFunSuite:

  test("a style with nothing set is the expression that builds it"):
    assert(Style.Default.asSource == "Style.Default")

  test("a color prints qualified so it compiles where it is pasted"):
    // the derived toString omits the `Color.` qualifier, which is the one thing that stops it from compiling
    assert(Color.Red.asSource == "Color.Red")
    assert(Color.Rgb(1, 2, 3).asSource == "Color.Rgb(1,2,3)")
    assert(Color.Indexed(9).asSource == "Color.Indexed(9)")
    assert(Color.BrightMagenta.asSource == "Color.BrightMagenta")

  test("foreground and background print as their builder calls"):
    val style = Style.Default.withFg(Color.Cyan).withBg(Color.Rgb(1, 2, 3))
    assert(style.asSource == "Style.Default.withFg(Color.Cyan).withBg(Color.Rgb(1,2,3))")
    assert(Style.Default.withFg(Color.Cyan).withBg(Color.Rgb(1, 2, 3)) == style)

  test("an explicitly reset color prints as withoutFg/withoutBg, the builders documented for it"):
    // Some(Color.Reset) and withoutFg build the same value, but the latter is the idiom for "no color, and I mean it"
    val style = Style.Default.withoutFg.withoutBg
    assert(style.asSource == "Style.Default.withoutFg.withoutBg")
    assert(Style.Default.withoutFg.withoutBg == style)

  test("every set modifier prints as its builder, in bit order"):
    val style = Style.Default.bold.dim.italic.underline.blink.reverse.hidden.crossedOut
    assert(style.asSource == "Style.Default.bold.dim.italic.underline.blink.reverse.hidden.crossedOut")
    assert(Style.Default.bold.dim.italic.underline.blink.reverse.hidden.crossedOut == style)

  test("every cleared modifier prints as its not* builder, in bit order"):
    val style = Style.Default.notBold.notDim.notItalic.notUnderline.notBlink.notReverse.notHidden.notCrossedOut
    val expected =
      "Style.Default.notBold.notDim.notItalic.notUnderline.notBlink.notReverse.notHidden.notCrossedOut"
    assert(style.asSource == expected)
    assert(Style.Default.notBold.notDim.notItalic.notUnderline.notBlink.notReverse.notHidden.notCrossedOut == style)

  test("cleared modifiers print after set ones so evaluating the text keeps the clear"):
    // `bold` withdraws an earlier `notBold`, so printing the clear first would produce text that loses it
    val style = Style.Default.notItalic.bold
    assert(style.asSource == "Style.Default.bold.notItalic")
    assert(Style.Default.bold.notItalic == style)

  test("a withdrawn clear is not printed at all"):
    val style = Style.Default.notBold.bold
    assert(style.asSource == "Style.Default.bold")
    assert(Style.Default.bold == style)

  test("underline style, underline color and link print after the modifiers"):
    val style = Style.Default.bold.curlyUnderline.withUnderlineColor(Color.Red).withLink("https://example.com")
    val expected =
      "Style.Default.bold" +
        ".withUnderlineStyle(UnderlineStyle.Curly)" +
        ".withUnderlineColor(Color.Red)" +
        ".withLink(\"https://example.com\")"
    assert(style.asSource == expected)
    assert(
      Style.Default.bold
        .withUnderlineStyle(UnderlineStyle.Curly)
        .withUnderlineColor(Color.Red)
        .withLink("https://example.com") == style
    )

  test("a link containing a quote or a backslash is escaped so it pastes back as the same string"):
    val url   = """https://example.com/a"b\c"""
    val style = Style.Default.withLink(url)
    assert(style.asSource == """Style.Default.withLink("https://example.com/a\"b\\c")""")
    assert(Style.Default.withLink("""https://example.com/a"b\c""") == style)

  test("two equal styles built in different orders print identically"):
    // this is the property that makes the output pasteable: the text is a function of the value, not of the chain
    assert(Style.Default.bold.italic.asSource == Style.Default.italic.bold.asSource)
    assert(
      Style.Default.withFg(Color.Red).bold.asSource == Style.Default.bold.withFg(Color.Red).asSource
    )

  test("asSource is stable across calls"):
    val samples = Seq(
      Style.Default,
      Style.Default.withFg(Color.Green),
      Style.Default.withoutBg.notDim,
      Style.Default.bold.dashedUnderline.withLink("x"),
      Style.Default.withBg(Color.Indexed(200)).reverse.notBold,
    )
    for style <- samples do assert(style.asSource == style.asSource, style.toString)

  test("asSource and toString stay two different formatters"):
    // toString is prose for a failure message; asSource is code. Guard against them drifting into each other.
    val style = Style.Default.withFg(Color.Cyan).bold
    assert(style.toString == "Style(fg=Cyan, modifiers=Bold)")
    assert(style.asSource == "Style.Default.withFg(Color.Cyan).bold")

  test("builderNames lower-cases the flag names and stays in bit order"):
    assert((Modifiers.Bold | Modifiers.CrossedOut).builderNames == Seq("bold", "crossedOut"))
    assert(Modifiers.None.builderNames.isEmpty)
