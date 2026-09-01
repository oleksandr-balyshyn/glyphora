package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Color, Line, Modifiers, Span, Style}
import io.worxbend.tui.testsupport.BufferAssertions
import io.worxbend.tui.widgets as w

import org.scalatest.funsuite.AnyFunSuite

/** `list` takes the same `String | Line` items its widget takes.
  *
  * Before this, the DSL node fixed `Seq[String]`, so a row that needed its own colour — a failed service in red, a
  * disabled entry dimmed — could only be drawn by dropping down to `widget(w.ListView(...))`, which loses the built-in
  * key and mouse handling. These tests pin the widened items and the `highlightSymbol` override that goes with them.
  */
final class StyledListItemsSpec extends AnyFunSuite:

  private val Red = Style.Default.withFg(Color.Red)

  /** Renders a `ListElement` on its own, with no app around it: the node exposes a `widget`, so a buffer is all it
    * takes to see what a frame would show.
    */
  private def render(element: ListElement, width: Int, height: Int) =
    BufferAssertions.rendered(element.widget, width, height)

  test("a plain string and a styled Line can be mixed in one list"):
    val state  = w.ListState()
    val items  = Seq[String | Line]("ok", Line(Seq(Span("failed", Red))))
    val buffer = render(list(items, state), 12, 2)

    assert(BufferAssertions.trimmedLines(buffer) == Seq("  ok", "  failed"))
    // the gutter is two columns wide, so the text of both rows starts at x = 2
    assert(buffer.get(2, 0).style == Style.Default, "the plain row picked up a style it was never given")
    assert(buffer.get(2, 1).style.fg.contains(Color.Red), "the styled row lost its own colour")

  test("a styled row keeps its own colour under the selection highlight"):
    val state  = w.ListState(selected = Some(1))
    val items  = Seq[String | Line]("ok", Line(Seq(Span("failed", Red))))
    // the DSL passes the element's focus style through as the highlight style, which defaults to `reverse`
    val buffer = render(list(items, state), 12, 2)

    val cell = buffer.get(2, 1)
    assert(cell.style.fg.contains(Color.Red), "the row's own colour was replaced rather than patched")
    assert(cell.style.modifiers.hasAny(Modifiers.Reverse), "the selection cue is missing")

  test("highlightSymbol replaces the marker and reserves its display width on every row"):
    val state  = w.ListState(selected = Some(0))
    // "▶" is a one-column glyph; "現" below is two columns, which is what proves the padding is counted in columns
    val buffer = render(list(Seq("現代", "b"), state).highlightSymbol("▶ "), 10, 2)

    assert(BufferAssertions.line(buffer, 0).startsWith("▶ 現代"))
    // the unselected row is indented by the same two columns the marker takes, so no text shifts sideways
    assert(BufferAssertions.line(buffer, 1).startsWith("  b"))

  test("a marker wider than the list writes nothing past the area"):
    val state  = w.ListState(selected = Some(0))
    val buffer = render(list(Seq("hello"), state).highlightSymbol("====>"), 3, 1)

    assert(BufferAssertions.line(buffer, 0) == "===")

  test("the built-in key handler still counts mixed items"):
    val state = w.ListState()
    val items = Seq[String | Line]("a", Line(Seq(Span("b", Red))), "c")
    val node  = list(items, state)

    val handler = node.builtinKeyHandler.getOrElse(fail("a list has built-in selection keys"))
    handler(KeyEvent(KeyCode.Down, KeyModifiers.None))
    handler(KeyEvent(KeyCode.End, KeyModifiers.None))
    assert(state.selected.contains(2), "End did not reach the last of the three mixed items")
