package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** Covers the rapid-blink flag, which is a separate SGR code from the ordinary blink rather than a spelling of it. */
final class RapidBlinkSpec extends AnyFunSuite:

  test("rapid blink is its own bit, not the ordinary blink under another name"):
    assert(!Style.Default.rapidBlink.modifiers.hasAny(Modifiers.Blink))
    assert(!Style.Default.blink.modifiers.hasAny(Modifiers.RapidBlink))
    assert(Style.Default.rapidBlink.modifiers.hasAny(Modifiers.RapidBlink))

  test("the two blinks can be carried at once without disturbing each other"):
    // nothing stops a caller setting both; the encoder emits both codes and the terminal picks what it supports
    val both = Style.Default.blink.rapidBlink
    assert(both.modifiers.hasAll(Modifiers.Blink | Modifiers.RapidBlink))
    assert(both.notBlink.modifiers.hasAny(Modifiers.RapidBlink))
    assert(both.notRapidBlink.modifiers.hasAny(Modifiers.Blink))

  test("the new bit does not collide with any existing flag"):
    val existing = Seq(
      Modifiers.Bold,
      Modifiers.Dim,
      Modifiers.Italic,
      Modifiers.Underline,
      Modifiers.Blink,
      Modifiers.Reverse,
      Modifiers.Hidden,
      Modifiers.CrossedOut,
    )
    for flag <- existing do assert(!Modifiers.RapidBlink.hasAny(flag), flag.show)

  test("rapid blink appears in the name table, so it shows up in every rendering of a bitset"):
    assert(Modifiers.RapidBlink.names == Seq("RapidBlink"))
    assert((Modifiers.Bold | Modifiers.RapidBlink).show == "Bold|RapidBlink")
    assert((Modifiers.Bold | Modifiers.RapidBlink).builderNames == Seq("bold", "rapidBlink"))

  test("clearing rapid blink survives being layered onto a style that sets it"):
    val base    = Style.Default.rapidBlink
    val patched = base.patch(Style.Default.notRapidBlink)
    assert(!patched.modifiers.hasAny(Modifiers.RapidBlink))
    assert(patched.clearedModifiers.hasAny(Modifiers.RapidBlink))

  test("the last call in a chain wins, as it does for every other flag"):
    assert(Style.Default.notRapidBlink.rapidBlink.modifiers.hasAny(Modifiers.RapidBlink))
    assert(!Style.Default.rapidBlink.notRapidBlink.modifiers.hasAny(Modifiers.RapidBlink))

  test("a rapid-blinking style prints and pastes back like any other"):
    assert(Style.Default.rapidBlink.toString == "Style(modifiers=RapidBlink)")
    assert(Style.Default.rapidBlink.asSource == "Style.Default.rapidBlink")
