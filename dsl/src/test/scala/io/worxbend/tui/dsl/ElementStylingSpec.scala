package io.worxbend.tui.dsl

import io.worxbend.tui.core.Color

import org.scalatest.funsuite.AnyFunSuite

/** The element styling extensions reached five of the eight text attributes a `Style` carries and offered no way to
  * turn any of them off. These tests pin the full set, both directions.
  */
final class ElementStylingSpec extends AnyFunSuite:

  private def modifiers(element: Element): Modifiers = element.props.style.modifiers

  test("every text attribute a Style carries is reachable from an element"):
    assert(modifiers(text("x").bold).hasAll(Modifiers.Bold))
    assert(modifiers(text("x").dim).hasAll(Modifiers.Dim))
    assert(modifiers(text("x").italic).hasAll(Modifiers.Italic))
    assert(modifiers(text("x").underline).hasAll(Modifiers.Underline))
    assert(modifiers(text("x").reverse).hasAll(Modifiers.Reverse))
    assert(modifiers(text("x").blink).hasAll(Modifiers.Blink))
    assert(modifiers(text("x").hidden).hasAll(Modifiers.Hidden))
    assert(modifiers(text("x").crossedOut).hasAll(Modifiers.CrossedOut))

  test("a negative builder clears an attribute an ancestor set rather than being ignored"):
    val boldParent = Style.Default.bold
    val optedOut   = text("x").notBold.props.style
    assert(!boldParent.patch(optedOut).modifiers.hasAny(Modifiers.Bold))
    // A style that merely never set bold does not clear it, which is the difference being tested.
    assert(boldParent.patch(Style.Default).modifiers.hasAll(Modifiers.Bold))

  test("every attribute has a negative form"):
    assert(!modifiers(text("x").bold.notBold).hasAny(Modifiers.Bold))
    assert(!modifiers(text("x").dim.notDim).hasAny(Modifiers.Dim))
    assert(!modifiers(text("x").italic.notItalic).hasAny(Modifiers.Italic))
    assert(!modifiers(text("x").underline.notUnderline).hasAny(Modifiers.Underline))
    assert(!modifiers(text("x").reverse.notReverse).hasAny(Modifiers.Reverse))
    assert(!modifiers(text("x").blink.notBlink).hasAny(Modifiers.Blink))
    assert(!modifiers(text("x").hidden.notHidden).hasAny(Modifiers.Hidden))
    assert(!modifiers(text("x").crossedOut.notCrossedOut).hasAny(Modifiers.CrossedOut))

  test("clearing one attribute leaves its neighbours alone"):
    val style = text("x").bold.italic.notBold.props.style
    assert(!style.modifiers.hasAny(Modifiers.Bold) && style.modifiers.hasAll(Modifiers.Italic))

  test("withoutFg and withoutBg return to the terminal's own colours"):
    assert(text("x").fg(Color.Red).withoutFg.props.style.fg.contains(Color.Reset))
    assert(text("x").bg(Color.Red).withoutBg.props.style.bg.contains(Color.Reset))

  test("the builders keep the element's own type, so they chain in any order"):
    val styled: TextElement = text("x").blink.notBlink.crossedOut.withoutBg
    assert(styled.content == "x")
