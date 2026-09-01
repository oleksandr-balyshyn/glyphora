package io.worxbend.tui.dsl

import io.worxbend.tui.core.Size
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot
import io.worxbend.tui.widgets as w

import org.scalatest.funsuite.AnyFunSuite

/** A list's navigation behaviour driven end to end through [[Pilot]], because routing is the half of any of this the
  * user actually touches. For now: the bottom-anchored list, which must draw against the floor of its area and still
  * answer every key the ordinary way-up list does.
  */
final class ListNavigationSpec extends AnyFunSuite:

  private val Width  = 20
  private val Height = 5

  private def startApp(view0: ReactiveScope ?=> Element): Pilot =
    val backend = HeadlessBackend(Size(Width, Height))
    val testApp = new TuiApp:
      override def bindings: KeyBindings            = KeyBindings(binding("ctrl+q", "quit")(quit()))
      def view(using ReactiveScope, Theme): Element = view0
    Pilot.start(backend) { testApp.runWith(backend) }.waitForIdle()

  private def quitApp(pilot: Pilot): Unit =
    pilot.pressKey(KeyCode.Char('q'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())

  test("a bottom-anchored list draws against the floor and answers the same keys"):
    val state = w.ListState()
    val pilot = startApp(list(Seq("first", "second"), state).bottomToTop)
    // two items in a five-row screen: the empty rows are above them, and item 0 is on the bottom row
    assert(pilot.screenLines.last.contains("first"))
    assert(pilot.screenLines.head.trim.isEmpty)
    pilot.pressKey(KeyCode.Down)
    val _ = pilot.waitForIdle()
    pilot.pressKey(KeyCode.Down)
    val _ = pilot.waitForIdle()
    assert(state.selected.contains(1))
    quitApp(pilot)

  test("bottomToTop rebuilds the node rather than mutating it"):
    val state  = w.ListState()
    val plain  = Element.list(Seq("a"), state)
    val flowed = plain.bottomToTop
    assert(plain.direction == w.ListDirection.TopToBottom)
    assert(flowed.direction == w.ListDirection.BottomToTop)
