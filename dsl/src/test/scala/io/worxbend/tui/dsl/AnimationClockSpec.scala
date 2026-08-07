package io.worxbend.tui.dsl

import io.worxbend.tui.core.Size
import io.worxbend.tui.runtime.RunnerConfig
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot
import io.worxbend.tui.widgets as w

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/** The ambient clock exists to delete four steps of ceremony — a `tickRate`, a `Signal[Int]`, an `onTick` override, and
  * threading the counter through every call site. These pin what it replaced them with.
  */
final class AnimationClockSpec extends AnyFunSuite:

  private def app(rate: Option[FiniteDuration])(view0: ReactiveScope ?=> Element): (HeadlessBackend, Pilot) =
    val backend = HeadlessBackend(Size(24, 4))
    val started = new TuiApp:
      override def config: RunnerConfig      = RunnerConfig(tickRate = rate)
      override def bindings: KeyBindings     = KeyBindings(binding("ctrl+q", "quit")(quit()))
      def view(using ReactiveScope): Element = view0
    (backend, Pilot.start(backend) { val _ = started.runWith(backend) }.waitForIdle())

  private def close(pilot: Pilot): Unit =
    pilot.pressKey(KeyCode.Char('q'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())

  /** The headline: a spinner with no counter, no `onTick`, and no argument still animates. */
  test("a spinner animates with no tick plumbing at the call site"):
    val (_, pilot) = app(Some(10.millis))(spinner("working"))
    val first      = pilot.screenLines.head
    val deadline   = System.nanoTime() + 2_000_000_000L
    var moved      = false
    while !moved && System.nanoTime() < deadline do
      pilot.waitForIdle()
      moved = pilot.screenLines.head != first
    assert(moved, s"the spinner never advanced past '$first'")
    close(pilot)

  /** The reason the clock is a tracked read rather than a plain value: a view that renders no animation must not be
    * repainted by the ticks. A hand-rolled `ticks.get` in the view repaints the whole app forever instead.
    */
  test("ticks do not repaint a view that renders no animation"):
    val (backend, pilot) = app(Some(10.millis))(text("static"))
    pilot.waitForIdle()
    val drawsBefore      = backend.drawCount
    val deadline         = System.nanoTime() + 300_000_000L
    while System.nanoTime() < deadline do pilot.waitForIdle()
    assert(backend.drawCount == drawsBefore, "a static view was repainted by animation ticks")
    close(pilot)

  test("a view that renders an animation is repainted by the ticks"):
    val (backend, pilot) = app(Some(10.millis))(spinner("working"))
    pilot.waitForIdle()
    val drawsBefore      = backend.drawCount
    val deadline         = System.nanoTime() + 300_000_000L
    while System.nanoTime() < deadline do pilot.waitForIdle()
    assert(backend.drawCount > drawsBefore, "an animated view was never repainted")
    close(pilot)

  /** Without a tick rate there is nothing to advance the clock, so the animation is simply static — it must not throw
    * or render blank, because a spinner in a tick-less app is a plausible mistake rather than a crash.
    */
  test("with no tick rate the animation renders its first frame and stays there"):
    val (_, pilot) = app(None)(spinner("working"))
    assert(pilot.screenLines.head.endsWith("working"))
    close(pilot)

  /** An animation is a pure function of the clock, so pinning it makes any frame reproducible without waiting. */
  test("freezing the clock makes a frame reproducible"):
    AnimationClockLock.frozenAt(0.millis):
      val (_, first) = app(None)(spinner())
      val atZero     = first.screenLines.head
      close(first)

      AnimationClock.freezeAt(w.SpinnerPreset.Dots.frameDuration)
      val (_, second) = app(None)(spinner())
      assert(second.screenLines.head != atZero, "a different frozen moment should render a different frame")
      close(second)

      AnimationClock.freezeAt(0.millis)
      val (_, third) = app(None)(spinner())
      assert(third.screenLines.head == atZero, "the same frozen moment must render the same frame")
      close(third)

  /** The explicit-clock factories stay available for animations tied to something other than wall time. */
  test("the explicit-clock factories drive from a caller's own value"):
    val (_, pilot) = app(None)(
      column(
        spinnerAt(0.millis, "a"),
        spinnerAt(w.SpinnerPreset.Dots.frameDuration, "b"),
      )
    )
    assert(pilot.screenLines.head != pilot.screenLines(1), "two different moments should render different frames")
    close(pilot)
