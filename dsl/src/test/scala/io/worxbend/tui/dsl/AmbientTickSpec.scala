package io.worxbend.tui.dsl

import io.worxbend.tui.core.Size
import io.worxbend.tui.runtime.Signal
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/** An app that renders an animation gets the ticks that animation needs, without configuring a `tickRate`.
  *
  * The assertion throughout is that the *drawn glyph* changes over time, because that is what a frozen spinner looks
  * like from the outside and it is what the negotiation exists to prevent. Each test bounds its wait, so a failure is
  * an assertion rather than a hung suite.
  */
final class AmbientTickSpec extends AnyFunSuite:

  /** Waits until `condition` holds, up to `within`; answers whether it did. */
  private def eventually(within: FiniteDuration)(condition: => Boolean): Boolean =
    val deadline = System.nanoTime() + within.toNanos
    while !condition && System.nanoTime() < deadline do Thread.sleep(10)
    condition

  private def quitApp(pilot: Pilot): Unit =
    pilot.pressKey(KeyCode.Char('q'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())

  test("a spinner animates in an app that configured no tick rate"):
    val backend = HeadlessBackend(Size(20, 3))
    val app     = new TuiApp:
      override def bindings: KeyBindings            = KeyBindings(binding("ctrl+q", "quit")(quit()))
      def view(using ReactiveScope, Theme): Element = spinner()
    val pilot   = Pilot.start(backend) { app.runWith(backend) }
    pilot.waitForIdle()
    val first   = pilot.screenText
    assert(eventually(3.seconds)(pilot.screenText != first), s"the frame never changed; it stayed at '$first'")
    quitApp(pilot)

  /** The other half of the bargain: a frame with nothing animated on it must not leave a ticker running. The app is
    * driven into an animated state and back out of it, and the assertion is that the frame then stops changing.
    */
  test("an app whose frame stops animating stops ticking"):
    val backend = HeadlessBackend(Size(20, 3))
    val busy    = Signal(true)
    val app     = new TuiApp:
      override def bindings: KeyBindings            = KeyBindings(
        binding("s", "stop animating")(busy.set(false)),
        binding("ctrl+q", "quit")(quit()),
      )
      def view(using ReactiveScope, Theme): Element =
        if busy.get then spinner() else text("idle")
    val pilot   = Pilot.start(backend) { app.runWith(backend) }
    pilot.waitForIdle()
    assert(eventually(3.seconds)(pilot.screenText.trim.nonEmpty))
    pilot.pressKey(KeyCode.Char('s')).waitForIdle()
    assert(pilot.screenText.contains("idle"))
    // let several tick intervals go by; a ticker still running would keep asking for frames
    val settled = pilot.screenText
    Thread.sleep(400)
    assert(pilot.screenText == settled)
    quitApp(pilot)

  /** An app that *did* configure a tick rate must be driven by the runner exactly as before — the ambient ticker stays
    * out of the way entirely, and `onTick` keeps being the thing a configured tick rate delivers.
    */
  test("a configured tick rate still drives onTick, and the ambient path does not double up"):
    val backend = HeadlessBackend(Size(20, 3))
    var ticks   = 0
    val app     = new TuiApp:
      override def config: RunnerConfig             = RunnerConfig(tickRate = Some(10.millis))
      override def onTick(): Unit                   = ticks += 1
      override def bindings: KeyBindings            = KeyBindings(binding("ctrl+q", "quit")(quit()))
      def view(using ReactiveScope, Theme): Element = spinner()
    val pilot   = Pilot.start(backend) { app.runWith(backend) }
    pilot.waitForIdle()
    assert(eventually(3.seconds)(ticks > 3), s"onTick ran $ticks times")
    quitApp(pilot)

  /** `onTick` is documented as needing a `config.tickRate`. The ambient ticker deliberately does not call it, so an app
    * that never asked for ticks does not silently start receiving them.
    */
  test("the ambient ticker does not call onTick"):
    val backend = HeadlessBackend(Size(20, 3))
    var ticks   = 0
    val app     = new TuiApp:
      override def onTick(): Unit                   = ticks += 1
      override def bindings: KeyBindings            = KeyBindings(binding("ctrl+q", "quit")(quit()))
      def view(using ReactiveScope, Theme): Element = spinner()
    val pilot   = Pilot.start(backend) { app.runWith(backend) }
    pilot.waitForIdle()
    val first   = pilot.screenText
    assert(eventually(3.seconds)(pilot.screenText != first)) // it really is animating
    assert(ticks == 0)
    quitApp(pilot)

  /** Toasts age in wall-clock time and are not part of the tree, so they ask for ticks on their own account — an app
    * with no animation and no configured tick rate must still see a toast disappear.
    */
  test("a toast ages out in an app that configured no tick rate"):
    val backend = HeadlessBackend(Size(40, 6))
    val app     = new TuiApp:
      override def bindings: KeyBindings            = KeyBindings(
        binding("n", "notify")(notify("saved ok", duration = 300.millis)),
        binding("ctrl+q", "quit")(quit()),
      )
      def view(using ReactiveScope, Theme): Element = text("content")
    val pilot   = Pilot.start(backend) { app.runWith(backend) }
    pilot.waitForIdle()
    pilot.pressKey(KeyCode.Char('n')).waitForIdle()
    assert(pilot.screenText.contains("saved ok"))
    assert(eventually(3.seconds)(!pilot.screenText.contains("saved ok")), "the toast never aged out")
    quitApp(pilot)
