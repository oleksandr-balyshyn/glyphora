package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Color, Modifiers, Span, Style}
import io.worxbend.tui.testsupport.BufferAssertions.rendered

import org.scalatest.funsuite.AnyFunSuite

/** The one-word styling shorthands on a string and on the `Span` a string becomes.
  *
  * Each is defined as the corresponding `styled(...)` call, so the tests compare the two spellings rather than
  * restating the styles: a shorthand that drifted away from its long form would fail here.
  */
final class StylizeShorthandSpec extends AnyFunSuite:

  test("a shorthand is the long form"):
    assert("q".bold == "q".styled(_.bold))
    assert("q".dim == "q".styled(_.dim))
    assert("q".italic == "q".styled(_.italic))
    assert("q".underline == "q".styled(_.underline))
    assert("q".reverse == "q".styled(_.reverse))
    assert("q".crossedOut == "q".styled(_.crossedOut))
    assert("q".fg(Color.Red) == "q".styled(_.withFg(Color.Red)))
    assert("q".bg(Color.Red) == "q".styled(_.withBg(Color.Red)))

  test("the content is left alone"):
    assert("héllo 現代".fg(Color.Red).content == "héllo 現代")
    // a Span measures in terminal columns, so the two-column CJK characters count twice
    assert("現代".bold.width == 4)

  test("shorthands chain, because a shorthand gives back a Span"):
    val span = "q".bold.fg(Color.Cyan).underline
    assert(span.style.fg.contains(Color.Cyan))
    assert(span.style.modifiers.hasAll(Modifiers.Bold | Modifiers.Underline))

  test("chaining layers onto the span's own style instead of restarting from the default"):
    val themed = Span("q", Style.Default.withBg(Color.Blue))
    val marked = themed.bold
    assert(marked.style.bg.contains(Color.Blue), "the span's background was thrown away")
    assert(marked.style.modifiers.hasAny(Modifiers.Bold))

  test("a shorthand span draws with the style it was given"):
    val buffer = rendered(line("press ", "q".fg(Color.Red).bold, " to quit").widget, 20, 1)
    val cell   = buffer.get(6, 0)
    assert(cell.symbol == "q")
    assert(cell.style.fg.contains(Color.Red))
    assert(cell.style.modifiers.hasAny(Modifiers.Bold))

  test("notBold on a child turns off a bold an ancestor set"):
    val bolded = rendered(text("hi").bold.widget, 4, 1)
    assert(bolded.get(0, 0).style.modifiers.hasAny(Modifiers.Bold))

    val cleared = rendered(text("hi").bold.notBold.widget, 4, 1)
    assert(!cleared.get(0, 0).style.modifiers.hasAny(Modifiers.Bold))

  test("a cleared attribute stays off when the styles are layered"):
    // `notBold` records "off", which a style that merely says nothing about bold cannot do: patching a bold style over
    // a silent one turns bold on, over a cleared one it does not
    val silent  = Style.Default
    val cleared = Style.Default.notBold
    assert(Style.Default.bold.patch(silent).modifiers.hasAny(Modifiers.Bold))
    assert(!Style.Default.bold.patch(cleared).modifiers.hasAny(Modifiers.Bold))
