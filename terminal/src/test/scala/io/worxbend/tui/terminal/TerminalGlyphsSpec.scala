package io.worxbend.tui.terminal

import io.worxbend.tui.core.GlyphSupport

import org.scalatest.funsuite.AnyFunSuite

import java.util.Locale

final class TerminalGlyphsSpec extends AnyFunSuite:

  private val Xterm = Map("TERM" -> "xterm-256color")

  private def withDefaultLocale[A](locale: Locale)(body: => A): A =
    val previous = Locale.getDefault
    try
      Locale.setDefault(locale)
      body
    finally Locale.setDefault(previous)

  test("a UTF-8 locale on a modern terminal allows every glyph"):
    assert(TerminalGlyphs.detect(Xterm + ("LANG" -> "en_US.UTF-8")) == GlyphSupport.Full)
    assert(TerminalGlyphs.detect(Xterm + ("LANG" -> "en_US.utf8")) == GlyphSupport.Full)
    assert(TerminalGlyphs.detect(Xterm + ("LC_ALL" -> "C.UTF-8")) == GlyphSupport.Full)

  test("a non-UTF-8 or absent locale falls all the way back to ASCII"):
    assert(TerminalGlyphs.detect(Xterm + ("LANG" -> "C")) == GlyphSupport.Ascii)
    assert(TerminalGlyphs.detect(Xterm + ("LANG" -> "POSIX")) == GlyphSupport.Ascii)
    assert(TerminalGlyphs.detect(Xterm + ("LANG" -> "en_US.ISO-8859-1")) == GlyphSupport.Ascii)
    assert(TerminalGlyphs.detect(Xterm) == GlyphSupport.Ascii)

  test("LC_ALL beats LC_CTYPE, which beats LANG"):
    assert(TerminalGlyphs.detect(Xterm + ("LC_ALL" -> "C") + ("LANG" -> "en_US.UTF-8")) == GlyphSupport.Ascii)
    assert(TerminalGlyphs.detect(Xterm + ("LC_CTYPE" -> "C") + ("LANG" -> "en_US.UTF-8")) == GlyphSupport.Ascii)
    assert(TerminalGlyphs.detect(Xterm + ("LC_ALL" -> "en_US.UTF-8") + ("LC_CTYPE" -> "C")) == GlyphSupport.Full)

  /** An empty value is not a setting. POSIX shells export `LC_ALL=` when a user unsets it that way, and reading that as
    * "no UTF-8" would drop a perfectly capable terminal to ASCII because of a variable nobody meant to set.
    */
  test("an empty locale variable is skipped rather than read as a non-UTF-8 one"):
    assert(TerminalGlyphs.detect(Xterm + ("LC_ALL" -> "") + ("LANG" -> "en_US.UTF-8")) == GlyphSupport.Full)

  test("locale matching is case-insensitive regardless of what the LANG value itself names"):
    assert(TerminalGlyphs.detect(Xterm + ("LANG" -> "TR_TR.UTF-8")) == GlyphSupport.Full)
    assert(TerminalGlyphs.detect(Xterm + ("LANG" -> "en_US.Utf8")) == GlyphSupport.Full)

  /** The real Turkish-locale hazard here is not in the value being matched (no literal this file compares against
    * contains an `I`) but in `TERM` containing one: under the JVM's own *default* locale, `"LINUX".toLowerCase` folds
    * to `"lınux"` (a dotless ı), which `startsWith("linux")` never matches. `consoleFont` folds with `Locale.ROOT`
    * instead, so this must hold no matter what the process's default locale is set to.
    */
  test("an uppercase console-font TERM is still recognised under a Turkish default locale"):
    withDefaultLocale(Locale.forLanguageTag("tr")):
      assert(TerminalGlyphs.detect(Map("TERM" -> "LINUX", "LANG" -> "en_US.UTF-8")) == GlyphSupport.BoxDrawing)

  test("a console font gets box drawing but nothing above it"):
    assert(TerminalGlyphs.detect(Map("TERM" -> "linux", "LANG" -> "en_US.UTF-8")) == GlyphSupport.BoxDrawing)
    assert(TerminalGlyphs.detect(Map("TERM" -> "vt220", "LANG" -> "en_US.UTF-8")) == GlyphSupport.BoxDrawing)
    assert(TerminalGlyphs.detect(Map("TERM" -> "dumb", "LANG" -> "en_US.UTF-8")) == GlyphSupport.BoxDrawing)
    assert(TerminalGlyphs.detect(Map("LANG" -> "en_US.UTF-8")) == GlyphSupport.BoxDrawing)

  test("GLYPHORA_ASCII overrides a fully capable environment"):
    val capable = Xterm + ("LANG" -> "en_US.UTF-8")
    assert(TerminalGlyphs.detect(capable + ("GLYPHORA_ASCII" -> "1")) == GlyphSupport.Ascii)
    assert(TerminalGlyphs.detect(capable + ("GLYPHORA_ASCII" -> "yes")) == GlyphSupport.Ascii)

  test("GLYPHORA_ASCII set to 0 or to nothing at all is not a request for ASCII"):
    val capable = Xterm + ("LANG" -> "en_US.UTF-8")
    assert(TerminalGlyphs.detect(capable + ("GLYPHORA_ASCII" -> "0")) == GlyphSupport.Full)
    assert(TerminalGlyphs.detect(capable + ("GLYPHORA_ASCII" -> "")) == GlyphSupport.Full)
