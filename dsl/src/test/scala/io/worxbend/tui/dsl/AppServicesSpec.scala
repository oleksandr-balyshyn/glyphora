package io.worxbend.tui.dsl

import io.worxbend.tui.core.Size
import io.worxbend.tui.runtime.RunnerConfig
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot
import io.worxbend.tui.widgets.TextInputState

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.DurationInt

/** End-to-end coverage for the 0.4.0 app services: screen stack, toasts, and the command palette. */
final class AppServicesSpec extends AnyFunSuite:

  private final class NavApp extends TuiApp:
    val baseField                          = TextInputState()
    val modalField                         = TextInputState()
    override def bindings: KeyBindings     = KeyBindings(
      binding("ctrl+o", "open modal")(openModal()),
      binding("ctrl+q", "quit")(quit()),
    )
    def view(using ReactiveScope): Element =
      column(text("base screen"), input(baseField))

    private def openModal(): Unit = pushScreen(Screen {
      centered(20, 3) {
        panel("Modal")(input(modalField)).onKeyEvent {
          case KeyEvent(KeyCode.Escape, _) =>
            popScreen()
            true
          case _                           => false
        }
      }
    })

  test("a modal screen renders over the base and traps focus; pop restores"):
    val backend = HeadlessBackend(Size(30, 8))
    val app     = NavApp()
    val pilot   = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    pilot.typeText("a").waitForIdle()
    assert(app.baseField.value == "a")
    pilot.pressKey(KeyCode.Char('o'), KeyModifiers.Ctrl).waitForIdle()
    assert(pilot.screenText.contains("Modal"))
    assert(pilot.screenText.contains("base screen")) // still visible beneath
    pilot.typeText("m").waitForIdle()
    assert(app.modalField.value == "m")
    assert(app.baseField.value == "a")               // base input no longer focused
    pilot.pressKey(KeyCode.Escape).waitForIdle()
    assert(!pilot.screenText.contains("Modal"))
    pilot.typeText("b").waitForIdle()
    assert(app.baseField.value == "ab")
    pilot.pressKey(KeyCode.Char('q'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())

  test("a full screen replaces the base view and pop restores it"):
    val backend = HeadlessBackend(Size(30, 5))
    val app     = new TuiApp:
      override def bindings: KeyBindings     = KeyBindings(
        binding("ctrl+f", "forward")(pushScreen(Screen.full(text("second screen")))),
        binding("ctrl+b", "back")(popScreen()),
        binding("ctrl+q", "quit")(quit()),
      )
      def view(using ReactiveScope): Element = text("base screen")
    val pilot   = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    assert(pilot.screenText.contains("base screen"))
    pilot.pressKey(KeyCode.Char('f'), KeyModifiers.Ctrl).waitForIdle()
    assert(pilot.screenText.contains("second screen"))
    assert(!pilot.screenText.contains("base screen"))
    pilot.pressKey(KeyCode.Char('b'), KeyModifiers.Ctrl).waitForIdle()
    assert(pilot.screenText.contains("base screen"))
    pilot.pressKey(KeyCode.Char('q'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())

  test("toasts appear on notify and age out with ticks"):
    val backend  = HeadlessBackend(Size(40, 6))
    val app      = new TuiApp:
      override def config: RunnerConfig      = RunnerConfig(tickRate = Some(10.millis))
      override def bindings: KeyBindings     = KeyBindings(
        binding("n", "notify me")(notify("saved ok", ToastLevel.Success, ttlTicks = 40)),
        binding("ctrl+q", "quit")(quit()),
      )
      def view(using ReactiveScope): Element = text("content")
    val pilot    = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    pilot.pressKey(KeyCode.Char('n')).waitForIdle()
    assert(pilot.screenText.contains("saved ok"))
    val deadline = System.nanoTime() + 3.seconds.toNanos
    while pilot.screenText.contains("saved ok") && System.nanoTime() < deadline do Thread.sleep(20)
    assert(!pilot.screenText.contains("saved ok"))
    pilot.pressKey(KeyCode.Char('q'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())

  test("ctrl+p opens the palette, typing filters, enter runs the command"):
    val backend  = HeadlessBackend(Size(60, 16))
    var deployed = false
    val app      = new TuiApp:
      override def bindings: KeyBindings     = KeyBindings(
        binding("d", "deploy to production") { deployed = true },
        binding("r", "restart service")(()),
        binding("ctrl+q", "quit")(quit()),
      )
      def view(using ReactiveScope): Element = text("content")
    val pilot    = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    pilot.pressKey(KeyCode.Char('p'), KeyModifiers.Ctrl).waitForIdle()
    assert(pilot.screenText.contains("Commands"))
    assert(pilot.screenText.contains("restart service"))
    pilot.typeText("deploy").waitForIdle()
    assert(!pilot.screenText.contains("restart service"))
    assert(pilot.screenText.contains("deploy to production"))
    pilot.pressKey(KeyCode.Enter).waitForIdle()
    assert(deployed)
    assert(!pilot.screenText.contains("Commands"))
    pilot.pressKey(KeyCode.Char('q'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())

  test("copyToClipboard reaches the backend through the runner"):
    val backend = HeadlessBackend(Size(30, 5))
    val app     = new TuiApp:
      override def bindings: KeyBindings     = KeyBindings(
        binding("c", "copy")(copyToClipboard("clipboard payload")),
        binding("ctrl+q", "quit")(quit()),
      )
      def view(using ReactiveScope): Element = text("content")
    val pilot   = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    assert(backend.clipboardContents.isEmpty)
    pilot.pressKey(KeyCode.Char('c')).waitForIdle()
    assert(backend.clipboardContents.contains("clipboard payload"))
    pilot.pressKey(KeyCode.Char('q'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())

  test("escape closes the palette without running anything"):
    val backend = HeadlessBackend(Size(60, 16))
    var fired   = false
    val app     = new TuiApp:
      override def bindings: KeyBindings     = KeyBindings(
        binding("x", "dangerous action") { fired = true },
        binding("ctrl+q", "quit")(quit()),
      )
      def view(using ReactiveScope): Element = text("content")
    val pilot   = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    pilot.pressKey(KeyCode.Char('p'), KeyModifiers.Ctrl).waitForIdle()
    assert(pilot.screenText.contains("Commands"))
    pilot.pressKey(KeyCode.Escape).waitForIdle()
    assert(!pilot.screenText.contains("Commands"))
    assert(!fired)
    pilot.pressKey(KeyCode.Char('q'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())
