package io.worxbend.tui.dsl

import io.worxbend.tui.core.{
  Buffer,
  KeyCode,
  KeyEvent,
  KeyModifiers,
  Modifiers,
  MouseEvent,
  MouseEventKind,
  Position,
  Rect,
}
import io.worxbend.tui.runtime.ReactiveScope
import io.worxbend.tui.testsupport.BufferAssertions.trimmedLines

import org.scalatest.funsuite.AnyFunSuite

/** The render-and-dispatch engine on its own, with no `TuiApp`, no runner and no terminal.
  *
  * That is the whole point of the class, so these tests drive it the way an embedding host would: build a `Buffer`,
  * render a view into it, deliver events by hand.
  */
final class ElementHostSpec extends AnyFunSuite:

  // nothing here tests reactivity, so the views read their signals — if they had any — without subscribing anything
  private given ReactiveScope = ReactiveScope.untracked

  private def buffer(width: Int, height: Int): Buffer = Buffer(Rect(0, 0, width, height))

  private def press(host: ElementHost, code: KeyCode): Boolean =
    host.dispatchKey(KeyEvent(code, KeyModifiers.None))

  test("a view renders into a plain buffer with no runner at all"):
    val host  = ElementHost()
    val paint = buffer(10, 3)
    host.render(Rect(0, 0, 10, 3), paint, Theme.Dark, panel(text("hi")))

    assert(trimmedLines(paint).exists(_.contains("hi")))

  test("the responsive pass picks the branch for the area actually being painted"):
    val view: View = responsive(size => if size.width < 40 then text("narrow") else text("wide"))
    val host       = ElementHost()

    val small = buffer(20, 3)
    host.render(Rect(0, 0, 20, 3), small, Theme.Dark, view)
    assert(trimmedLines(small).head.trim == "narrow")

    val large = buffer(80, 3)
    host.render(Rect(0, 0, 80, 3), large, Theme.Dark, view)
    assert(trimmedLines(large).head.trim == "wide")

  test("a key reaches the focused element and an unhandled one comes back unconsumed"):
    var pressed    = 0
    val view: View = column(button("first")(pressed += 1), button("second")(()))
    val host       = ElementHost()
    host.render(Rect(0, 0, 20, 4), buffer(20, 4), Theme.Dark, view)

    assert(press(host, KeyCode.Enter), "the focused button did not take Enter")
    assert(pressed == 1)
    assert(!press(host, KeyCode.F(12)), "an unbound key was reported as consumed")

  test("dispatchKey does not move focus: traversal is the host's policy"):
    val view: View = column(button("first")(()), button("second")(()))
    val host       = ElementHost()
    host.render(Rect(0, 0, 20, 4), buffer(20, 4), Theme.Dark, view)

    val before = host.tracker.focusedIndex
    val _      = host.dispatchKey(KeyEvent(KeyCode.Tab, KeyModifiers.None))
    assert(host.tracker.focusedIndex == before, "the engine moved focus on Tab, which is TuiApp's decision")

  test("focusNext and focusPrevious cycle the focusables"):
    val view: View = column(
      button("first")(()).key("first"),
      button("second")(()).key("second"),
      button("third")(()).key("third"),
    )
    val host       = ElementHost()
    host.render(Rect(0, 0, 20, 4), buffer(20, 4), Theme.Dark, view)
    assert(host.focusedKey.contains("first"))

    assert(host.focusNext())
    host.render(Rect(0, 0, 20, 4), buffer(20, 4), Theme.Dark, view)
    assert(host.focusedKey.contains("second"))

    assert(host.focusPrevious())
    host.render(Rect(0, 0, 20, 4), buffer(20, 4), Theme.Dark, view)
    assert(host.focusedKey.contains("first"))

    assert(host.focusToKey("third"))
    host.render(Rect(0, 0, 20, 4), buffer(20, 4), Theme.Dark, view)
    assert(host.focusedKey.contains("third"))

  test("the focused element is the one drawn with the theme's focus cue"):
    val view: View = column(button("first")(()), button("second")(()))
    val host       = ElementHost()
    val paint      = buffer(20, 4)
    host.render(Rect(0, 0, 20, 4), paint, Theme.Dark, view)

    val rows                    = trimmedLines(paint)
    val focusedRow              = rows.indexWhere(_.contains("first"))
    val unfocusedRow            = rows.indexWhere(_.contains("second"))
    def hasCue(y: Int): Boolean =
      (0 until 20).exists(x => paint.get(x, y).style.modifiers.hasAny(Modifiers.Reverse))

    assert(hasCue(focusedRow), "the focused button carries no cue")
    assert(!hasCue(unfocusedRow), "an unfocused button carries the cue")

  test("a press inside another focusable moves focus, one on empty space does not"):
    val view: View = column(
      button("first")(()).key("first").length(1),
      button("second")(()).key("second").length(1),
      spacer,
    )
    val host       = ElementHost()
    host.render(Rect(0, 0, 20, 6), buffer(20, 6), Theme.Dark, view)

    val onSecond = MouseEvent(Position(2, 1), MouseEventKind.Down, KeyModifiers.None)
    assert(host.dispatchMouse(onSecond), "clicking the second button did nothing")
    // the key of the focused element is read off the last frame, so the move shows up once the next one is painted
    host.render(Rect(0, 0, 20, 6), buffer(20, 6), Theme.Dark, view)
    assert(host.focusedKey.contains("second"))

    val onNothing = MouseEvent(Position(2, 5), MouseEventKind.Down, KeyModifiers.None)
    assert(!host.dispatchMouse(onNothing), "a click on empty space was reported as handled")
    host.render(Rect(0, 0, 20, 6), buffer(20, 6), Theme.Dark, view)
    assert(host.focusedKey.contains("second"), "a click on empty space moved focus")

  test("nothing is dispatched before the first frame"):
    val host = ElementHost()
    assert(!press(host, KeyCode.Enter))
    assert(!host.dispatchPaste("hello"))

  test("clearFocus leaves nothing focused, so keys pass straight through"):
    var pressed    = 0
    val view: View = button("only")(pressed += 1)
    val host       = ElementHost()
    host.render(Rect(0, 0, 20, 3), buffer(20, 3), Theme.Dark, view)

    host.clearFocus()
    // events are routed against the tree the last frame painted, so the cleared focus bites from the next frame on
    host.render(Rect(0, 0, 20, 3), buffer(20, 3), Theme.Dark, view)
    assert(!press(host, KeyCode.Enter), "a key was consumed with nothing focused")
    assert(pressed == 0)
