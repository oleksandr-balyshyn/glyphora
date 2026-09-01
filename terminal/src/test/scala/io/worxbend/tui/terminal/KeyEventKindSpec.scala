package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Event, KeyCode, KeyEvent, KeyEventKind, KeyModifiers}

import org.scalatest.funsuite.AnyFunSuite

/** Press, auto-repeat and release: the field only a kitty-protocol terminal can fill in, and only when the application
  * asked for it.
  *
  * Two halves are pinned here. The decoder must read the event type out of the sub-parameter where kitty writes it,
  * without disturbing anything already parsed from the main parameters; and the request for that reporting must stay
  * off unless a caller turns it on, because an application written for press-only input would act on every keystroke
  * twice.
  */
final class KeyEventKindSpec extends AnyFunSuite:

  private val Esc = 0x1b

  private def csi(body: String): Seq[Int] = Esc +: '['.toInt +: body.map(_.toInt)

  private def decoded(chars: Seq[Int]): Event =
    val iterator = chars.iterator
    InputDecoder(_ => if iterator.hasNext then iterator.next() else -2)
      .decode(10)
      .getOrElse(fail(s"expected an event from ${chars.mkString(",")}"))

  private def keyOf(body: String): KeyEvent =
    decoded(csi(body)) match
      case Event.Key(event) => event
      case other            => fail(s"expected a key event, got $other")

  test("the event type sub-parameter names the moment of the keystroke"):
    assert(keyOf("97;1:1u").kind == KeyEventKind.Press)
    assert(keyOf("97;1:2u").kind == KeyEventKind.Repeat)
    assert(keyOf("97;1:3u").kind == KeyEventKind.Release)

  test("a report with no event type is a press"):
    // the ordinary case: a terminal only sends the field to an application that pushed enhancement flag 2
    assert(keyOf("97;5u") == KeyEvent(KeyCode.Char('a'), KeyModifiers.Ctrl))
    assert(keyOf("97;5u").isPress)

  test("reading the event type leaves the modifiers where they were"):
    // the sub-parameter lives inside the modifier field, so a decoder that split on ':' too early would read the
    // event type as the modifier bitset and report Ctrl+a as a plain 'a'
    val release = keyOf("97;5:3u")
    assert(release.code == KeyCode.Char('a'))
    assert(release.modifiers == KeyModifiers.Ctrl)
    assert(release.isRelease)

  test("the shift fold still runs, with the kind attached to its result"):
    // kitty reports the unshifted key plus a Shift bit; that is folded to the legacy uppercase encoding, and the
    // release must survive the fold rather than being replaced by a fresh press
    val folded = keyOf("97;4:3u")
    assert(folded.code == KeyCode.Char('A'))
    assert(!folded.modifiers.hasAny(KeyModifiers.Shift))
    assert(folded.kind == KeyEventKind.Release)

  test("an unknown event type is read as a press rather than dropping the key"):
    assert(keyOf("97;1:9u").kind == KeyEventKind.Press)

  test("the push sequence asks for event types only when told to"):
    assert(AnsiSequences.pushKittyKeyboard(reportEventTypes = false) == s"${Esc.toChar}[>1u")
    assert(AnsiSequences.pushKittyKeyboard(reportEventTypes = true) == s"${Esc.toChar}[>3u")
    // the constant every existing caller uses stays press-only, which is what keeps the feature opt-in
    assert(AnsiSequences.PushKittyKeyboard == AnsiSequences.pushKittyKeyboard(reportEventTypes = false))
    // and whatever was pushed is popped by the same sequence, so the opt-in needs no matching teardown of its own
    assert(AnsiSequences.RestoreAll.contains(AnsiSequences.PopKittyKeyboard))
