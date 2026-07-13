package io.worxbend.tui.dsl

import io.worxbend.tui.core.{KeyCode, Size}
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.DurationInt

final class SplashAndEffectsSpec extends AnyFunSuite:

  private final class SplashApp extends TuiApp:
    override def splash: Option[SplashScreen] = Some(
      SplashScreen(
        centered(20, 1)(text("LOADING")),
        effect = Effect.fadeIn(100.millis),
        minimumDuration = 300.millis,
      )
    )
    override def bindings: KeyBindings        = KeyBindings(binding("q", "quit")(quit()))
    def view(using ReactiveScope): Element    = text("main view")

  test("the splash shows first, then transitions to the main view"):
    val backend  = HeadlessBackend(Size(30, 5))
    val app      = SplashApp()
    val pilot    = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    assert(pilot.screenText.contains("LOADING"))
    assert(!pilot.screenText.contains("main view"))
    val deadline = System.nanoTime() + 3.seconds.toNanos
    while !pilot.screenText.contains("main view") && System.nanoTime() < deadline do Thread.sleep(20)
    assert(pilot.screenText.contains("main view"))
    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())

  test("any key skips the splash"):
    val backend = HeadlessBackend(Size(30, 5))
    val app     = new TuiApp:
      override def splash: Option[SplashScreen] = Some(
        SplashScreen(text("INTRO"), Effect.fadeIn(50.millis), minimumDuration = 60.seconds)
      )
      override def bindings: KeyBindings        = KeyBindings(binding("q", "quit")(quit()))
      def view(using ReactiveScope): Element    = text("main view")
    val pilot   = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    assert(pilot.screenText.contains("INTRO"))
    pilot.pressKey(KeyCode.Enter).waitForIdle()
    assert(pilot.screenText.contains("main view"))
    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())

  test("runEffect animates over the rendered view and completes"):
    val backend         = HeadlessBackend(Size(20, 3))
    val app             = new TuiApp:
      override def config                    = io.worxbend.tui.runtime.RunnerConfig(tickRate = Some(20.millis))
      override def bindings: KeyBindings     = KeyBindings(
        binding("e", "run effect")(runEffect(Effect.dissolve(150.millis))),
        binding("q", "quit")(quit()),
      )
      def view(using ReactiveScope): Element = text("solid content here")
    val pilot           = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    assert(pilot.screenText.contains("solid content here"))
    pilot.pressKey(KeyCode.Char('e'))
    // mid-effect some cells are erased; after completion the content is fully back
    val deadline        = System.nanoTime() + 3.seconds.toNanos
    var sawPartial      = false
    while !sawPartial && System.nanoTime() < deadline do
      if !pilot.screenText.contains("solid content here") then sawPartial = true
      Thread.sleep(5)
    assert(sawPartial, "effect never visibly altered the frame")
    val restoreDeadline = System.nanoTime() + 3.seconds.toNanos
    while !pilot.screenText.contains("solid content here") && System.nanoTime() < restoreDeadline do Thread.sleep(10)
    assert(pilot.screenText.contains("solid content here"))
    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())
