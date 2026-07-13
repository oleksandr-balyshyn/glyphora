package io.worxbend.tui.dsl

import io.worxbend.tui.core.Size
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot
import io.worxbend.tui.widgets.ScrollViewState

import org.scalatest.funsuite.AnyFunSuite

final class MouseInteractionSpec extends AnyFunSuite:

  private def startApp(view0: ReactiveScope ?=> Element): Pilot =
    val backend = HeadlessBackend(Size(40, 8))
    val testApp = new TuiApp:
      override def bindings: KeyBindings     = KeyBindings(binding("ctrl+q", "quit")(quit()))
      def view(using ReactiveScope): Element = view0
    Pilot.start(backend) { val _ = testApp.runWith(backend) }.waitForIdle()

  private def quitApp(pilot: Pilot): Unit =
    pilot.pressKey(KeyCode.Char('q'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())

  test("clicking a button activates it"):
    var pressed = 0
    val pilot   = startApp(column(button("OK") { pressed += 1 }, text("below")))
    pilot.click(5, 0).waitForIdle()
    assert(pressed == 1)
    quitApp(pilot)

  test("clicking a checkbox toggles its signal"):
    val checked = Signal(false)
    val pilot   = startApp(column(checkbox("opt in", checked), text("x")))
    pilot.click(2, 0).waitForIdle()
    assert(checked.peek)
    pilot.click(2, 0).waitForIdle()
    assert(!checked.peek)
    quitApp(pilot)

  test("the scroll wheel scrolls a scrollView under the pointer"):
    val state   = ScrollViewState()
    val content = column((0 until 9).map(n => text(s"row $n").fill)*)
    val pilot   = startApp(scrollView(content, contentHeight = 9, state))
    assert(pilot.screenLines.head.startsWith("row 0"))
    pilot.backend.postEvent(
      io.worxbend.tui.core.Event.Mouse(
        io.worxbend.tui.core.MouseEvent(3, 3, io.worxbend.tui.core.MouseEventKind.ScrollDown, KeyModifiers.None)
      )
    )
    pilot.waitForIdle()
    assert(pilot.screenLines.head.startsWith("row 1"))
    quitApp(pilot)

  test("clicking positions a slider knob"):
    val value = Signal(0)
    val pilot = startApp(column(slider(value), text("x")))
    pilot.click(38, 0).waitForIdle() // near the right end of a 40-wide track
    assert(value.peek == 100)
    pilot.click(20, 0).waitForIdle()
    assert(value.peek > 30 && value.peek < 70)
    quitApp(pilot)

  test("dragging moves the splitPane divider"):
    val split = Signal(50)
    val pilot = startApp(splitPane(text("L").fill, text("R").fill, split))
    pilot.backend.postEvent(
      io.worxbend.tui.core.Event.Mouse(
        io.worxbend.tui.core.MouseEvent(10, 2, io.worxbend.tui.core.MouseEventKind.Drag, KeyModifiers.None)
      )
    )
    pilot.waitForIdle()
    assert(split.peek == 25) // 10 of 40 columns
    quitApp(pilot)
