package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Event, KeyCode, KeyEvent, KeyModifiers}

import org.scalatest.funsuite.AnyFunSuite

/** The kitty keyboard protocol's event-type sub-parameter: what glyphora asks for, and what it makes of the answer. */
final class KeyReleaseSpec extends AnyFunSuite:

  private def decoderFor(chars: Int*): InputDecoder =
    val iterator = chars.iterator
    InputDecoder(_ => if iterator.hasNext then iterator.next() else -2)

  private def csi(body: String): Seq[Int] = 0x1b +: '['.toInt +: body.map(_.toInt)

  private def decoded(body: String): Option[Event] = decoderFor(csi(body)*).decode(10)

  // ------------------------------------------------------------------ the request

  test("the default push asks for disambiguation only, and the opt-in push adds event types"):
    assert(AnsiSequences.PushKittyKeyboard == "[>1u")
    assert(AnsiSequences.PushKittyKeyboardEvents == "[>3u")

  /** The flags are a bitmask, so the opt-in push has to be a superset of the default one rather than a replacement:
    * asking for releases must not switch disambiguation back off.
    */
  test("the opt-in push keeps every flag the default push asked for"):
    assert((AnsiSequences.KittyDisambiguate | AnsiSequences.KittyReportEventTypes) == 3)
    assert(AnsiSequences.pushKittyKeyboard(AnsiSequences.KittyDisambiguate) == AnsiSequences.PushKittyKeyboard)

  /** Whatever was pushed, one pop takes it off, which is why the teardown string never had to know what was asked for —
    * and why `enableKeyEventTypes` pops before it pushes rather than stacking a second entry.
    */
  test("the teardown string still pops the keyboard mode"):
    assert(AnsiSequences.RestoreAll.contains(AnsiSequences.PopKittyKeyboard))

  // ------------------------------------------------------------------ the reply

  test("a release sub-parameter produces a release event, and nothing else does"):
    assert(decoded("97;5:3u").contains(Event.KeyRelease(KeyEvent(KeyCode.Char('a'), KeyModifiers.Ctrl))))
    assert(decoded("97;5:1u").contains(Event.Key(KeyEvent(KeyCode.Char('a'), KeyModifiers.Ctrl))))
    assert(decoded("97;5u").contains(Event.Key(KeyEvent(KeyCode.Char('a'), KeyModifiers.Ctrl))))

  /** Auto-repeat is deliberately an ordinary press: a held key on a legacy terminal already arrives as a stream of
    * presses, so reporting it otherwise would make held-arrow scrolling depend on which terminal the app started in.
    */
  test("an auto-repeat is reported as the press it is indistinguishable from elsewhere"):
    assert(decoded("97;5:2u") == decoded("97;5u"))

  test("an unknown event type falls back to a press rather than being dropped"):
    assert(decoded("97;5:9u") == decoded("97;5u"))
    assert(decoded("97;5:u") == decoded("97;5u"))

  /** The sub-parameter must not shift the positional parameters after it: the modifier is still the second *parameter*,
    * not the second colon-separated field.
    */
  test("a sub-parameter changes the kind and nothing else about the key"):
    val released = decoded("97;5:3u")
    val pressed  = decoded("97;5u")
    assert(released.collect { case Event.KeyRelease(key) => key } == pressed.collect { case Event.Key(key) => key })

  test("a functional key reports its release too"):
    assert(decoded("57376;1:3u").contains(Event.KeyRelease(KeyEvent.of(KeyCode.F(13)))))

  /** Shift folding happens before the kind is decided, so `"A"` names the key on both the way down and the way up. */
  test("the shifted-key folding applies to a release exactly as it does to a press"):
    assert(decoded("97;2:3u").contains(Event.KeyRelease(KeyEvent.of(KeyCode.Char('A')))))

  // ------------------------------------------------------------------ the backend seam

  test("a backend that cannot report event types answers success and emits no releases"):
    assert(HeadlessBackend(io.worxbend.tui.core.Size(10, 3)).enableKeyEventTypes() == Right(()))
