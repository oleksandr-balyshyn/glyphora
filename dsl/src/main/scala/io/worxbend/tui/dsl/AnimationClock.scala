package io.worxbend.tui.dsl

import io.worxbend.tui.runtime.{ReactiveScope, RenderThread, Signal}

import scala.concurrent.duration.{DurationLong, FiniteDuration}

/** The ambient time source the animated elements read, so showing a spinner needs no tick plumbing at all.
  *
  * Before this existed, an animation cost four separate steps: set a `tickRate`, declare a `Signal[Int]`, override
  * `onTick` to advance it, and thread the counter through every call site. All four were boilerplate the framework
  * already had the information to do itself.
  *
  * Reading it is a *tracked* read, which is what makes this work rather than merely shorten: a view only subscribes to
  * the clock when it actually renders an animation, so a screen with no spinner on it is not woken up by the ticks —
  * where a hand-rolled `ticks.get` in the view repaints the whole app forever whether anything is moving or not.
  *
  * The value is elapsed time since the process started rather than since a particular runner, so it is meaningful
  * without a runner (a plain unit test reads `Duration.Zero` until something advances it) and consistent across several
  * runners in one JVM.
  */
object AnimationClock:

  private val origin = System.nanoTime()

  private[dsl] val signal: Signal[FiniteDuration] = Signal(0L.nanos)

  /** The current animation time, subscribing the caller so its view repaints as the clock advances. */
  def elapsed(using ReactiveScope): FiniteDuration = signal.get

  /** The current animation time without subscribing — for a caller that repaints for its own reasons. */
  def peek: FiniteDuration = signal.peek

  /** Republishes the clock. Called from `TuiApp` on every tick, on the render thread. */
  private[dsl] def advance(): Unit = signal.set((System.nanoTime() - origin).nanos)

  /** Pins the clock to an exact value. For tests: an animation is a pure function of this, so pinning it makes any
    * frame reproducible without waiting for wall-clock time to pass.
    *
    * Marshalled onto the render thread rather than set directly, because the caller is a *test* thread by construction.
    * The render-thread guard is process-wide, so a suite running beside another one that happens to have a live runner
    * would otherwise throw here — and it would throw only sometimes, depending on which suites were scheduled together,
    * which is the worst kind of test failure to be handed.
    */
  def freezeAt(elapsed: FiniteDuration): Unit = RenderThread.runOnRenderThread(signal.set(elapsed))
