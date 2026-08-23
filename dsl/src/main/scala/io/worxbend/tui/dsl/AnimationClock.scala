package io.worxbend.tui.dsl

import io.worxbend.tui.runtime.{ReactiveScope, RenderThread, Signal}

import java.util.concurrent.{ConcurrentHashMap, CountDownLatch, TimeUnit}
import scala.concurrent.duration.{DurationInt, DurationLong, FiniteDuration}

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
  * **One signal per render loop, one shared origin.** The value is elapsed time since the process started rather than
  * since a particular runner, so it is meaningful without a runner (a plain unit test reads `Duration.Zero` until
  * something advances it) and two runners in one JVM agree on what time it is. The `Signal` carrying it, however, is
  * per render loop, resolved through [[RenderThread.capture]] on every read and every advance. It has to be: a
  * `Signal`'s subscriber set is a plain mutable set with no lock, confined to *a* render thread — and with one signal
  * for the whole process, two runners' render threads both subscribe to and notify it at once. `checkRenderThread`
  * cannot catch that, because both threads *are* render threads. What it cost was measurable and silent: with the
  * shared signal put back, the regression test below — two loops, 2000 tracked reads each — sees one loop lose every
  * one of its 2000 subscriptions. An app whose only reactive dependency is the ambient clock (any `spinner`,
  * `orbitSpinner` or `indeterminateBar`) therefore froze mid-animation, with no exception to explain it.
  *
  * A caller belonging to no runner resolves the one shared unattributed loop, so plain unit tests and [[freezeAt]]
  * still see a single clock between them.
  */
object AnimationClock:

  private val origin = System.nanoTime()

  /** One clock per render loop. Entries are added on first use and removed by [[releaseLoop]] when a `TuiApp` exits, so
    * a JVM that starts and stops many apps does not accumulate one signal per run.
    */
  private val clocks = ConcurrentHashMap[RenderThread.RenderLoop, Signal[FiniteDuration]]()

  // What a clock created from now on starts at: the last time anything published, whichever loop published it. The
  // signals are per loop, but the *time* is not — that is the property this class promises — so a runner starting now
  // begins where the process has got to rather than back at zero, and a `freezeAt` taken before a runner exists still
  // decides that runner's first frame. Volatile because it is written on one render thread and read on another.
  @volatile private var seed: FiniteDuration = 0L.nanos

  /** The current animation time, subscribing the caller so its view repaints as the clock advances. */
  def elapsed(using ReactiveScope): FiniteDuration = clock.get

  /** The current animation time without subscribing — for a caller that repaints for its own reasons. */
  def peek: FiniteDuration = clock.peek

  /** Republishes the clock. Called from `TuiApp` on every tick, on the render thread. */
  private[dsl] def advance(): Unit = publish((System.nanoTime() - origin).nanos)

  /** Sets this loop's clock and records the value as the starting point for any clock created after it. */
  private def publish(value: FiniteDuration): Unit =
    seed = value
    clock.set(value)

  /** Binds a clock to the calling thread's render loop and hands back the key that [[releaseLoop]] takes.
    *
    * Called from `TuiApp`'s `onStart`, which is the first moment a run is certainly on its own render thread — the exit
    * path runs after that registration has already been torn down, so it could no longer resolve the loop for itself.
    * The registration flows one way only: `tui-runtime` learns nothing about `tui-dsl`, which is what the module
    * dependency edges require.
    */
  private[dsl] def attachToCurrentLoop(): RenderThread.RenderLoop =
    val loop = RenderThread.capture()
    val _    = clocks.computeIfAbsent(loop, _ => Signal(seed))
    loop

  /** How many render loops currently have a clock. Package-private, for the test that pins the release. */
  private[dsl] def attachedLoops: Int = clocks.size

  /** Drops the clock bound to `loop`, once its runner has exited. */
  private[dsl] def releaseLoop(loop: RenderThread.RenderLoop): Unit =
    val _ = clocks.remove(loop)

  /** The signal this thread's render loop publishes its animation time on.
    *
    * A plain lookup first, because this runs once per animated element per frame and the entry is present for all but
    * the first of them; `computeIfAbsent` is the fallback for a loop reading the clock before anything attached one — a
    * widget test with no runner, or a view rendered before `onStart`.
    */
  private def clock: Signal[FiniteDuration] =
    val loop = RenderThread.capture()
    Option(clocks.get(loop)).getOrElse(clocks.computeIfAbsent(loop, _ => Signal(seed)))

  /** Pins the clock to an exact value. For tests: an animation is a pure function of this, so pinning it makes any
    * frame reproducible without waiting for wall-clock time to pass.
    *
    * The pin lands on the clock of whichever render loop applies it — for a test thread belonging to no runner, the one
    * every other runner-less caller shares — and becomes the value any clock created *after* it starts at, so pinning
    * and then starting an app decides that app's first frame. Both halves are process-wide state, so a suite that pins
    * the ambient clock and then asserts on a specific frame can still be decided by whichever sibling suite pins it
    * next: a failure that appears only under parallel execution, and so in CI rather than on a developer's machine.
    * Prefer the `…At(elapsed)` element factories, which read nothing ambient; reach for this only when the ambient
    * clock itself is the subject, and serialise those suites against each other — which is the *only* thing
    * `AnimationClockLock` in the dsl tests is still for, now that a running app's clock is its own.
    *
    * Marshalled onto the render thread rather than set directly, because the caller is a *test* thread by construction.
    * The render-thread guard is process-wide, so a suite running beside another one that happens to have a live runner
    * would otherwise throw here — and it would throw only sometimes, depending on which suites were scheduled together,
    * which is the worst kind of test failure to be handed. Resolving the signal inside the body rather than around it
    * is what keeps the pin on the clock of the loop that actually applies it.
    *
    * The call then *waits* for that hand-off to complete. `RenderThread.runOnRenderThread` runs the body inline when
    * the caller is already a render thread, but queues it otherwise — and a test thread that belongs to no runner, in a
    * JVM where some other suite has one, queues onto a detached loop drained by whichever render thread reaches it
    * first. Returning before the queued body ran would leave the very next rendered frame pinned to the *old* value,
    * which is the frame the caller is about to assert on. The wait is bounded: if nothing drains the queue within
    * [[FreezeTimeout]] this gives up and returns rather than hanging the suite.
    */
  def freezeAt(elapsed: FiniteDuration): Unit =
    val applied = CountDownLatch(1)
    RenderThread.runOnRenderThread:
      publish(elapsed)
      applied.countDown()
    val _       = applied.await(FreezeTimeout.toMillis, TimeUnit.MILLISECONDS)

  /** How long [[freezeAt]] waits for its value to reach the render thread before giving up. Long enough that a busy
    * loop still gets there, short enough that a suite fails on an assertion rather than on a timeout.
    */
  private val FreezeTimeout: FiniteDuration = 2.seconds
