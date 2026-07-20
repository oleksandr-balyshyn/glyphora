package io.worxbend.tui.dsl

import io.worxbend.tui.core.Size
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot

import org.scalatest.funsuite.AnyFunSuite

final class SuspendSpec extends AnyFunSuite:

  test("suspend hands the terminal back during the body and restores the app screen afterward"):
    val backend          = HeadlessBackend(Size(20, 4))
    var screenDuringBody = true
    val app              = new TuiApp:
      override def bindings: KeyBindings     = KeyBindings(
        binding("e", "edit")(suspend { screenDuringBody = backend.isAlternateScreen }),
        binding("ctrl+q", "quit")(quit()),
      )
      def view(using ReactiveScope): Element = text("editor host")
    val pilot            = Pilot.start(backend) { val _ = app.runWith(backend) }.waitForIdle()
    assert(backend.isAlternateScreen) // the app runs on the alternate screen
    pilot.typeText("e").waitForIdle()
    assert(backend.suspendCount == 1)
    assert(!screenDuringBody)         // during the body the terminal was handed back (primary screen)
    assert(backend.isAlternateScreen) // and the app screen was restored afterward
    pilot.pressKey(KeyCode.Char('q'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())

  test("printAbove records durable lines above the app"):
    val backend = HeadlessBackend(Size(20, 4))
    val app     = new TuiApp:
      override def bindings: KeyBindings     = KeyBindings(
        binding("l", "log")(printAbove("build ok", "deployed ✓")),
        binding("ctrl+q", "quit")(quit()),
      )
      def view(using ReactiveScope): Element = text("app")
    val pilot   = Pilot.start(backend) { val _ = app.runWith(backend) }.waitForIdle()
    pilot.typeText("l").waitForIdle()
    assert(backend.printedAbove == Seq("build ok", "deployed ✓"))
    pilot.pressKey(KeyCode.Char('q'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())
