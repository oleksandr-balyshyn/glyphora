package io.worxbend.tui.testsupport

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.{Duration, FiniteDuration}

/** A clock reading a test moves by hand, for driving a runner's tick scheduling without waiting on wall-clock time.
  *
  * `TerminalRunner` takes its clock as a parameter (`nanoTime: () => Long`) precisely so a test can supply one. Pass
  * [[reading]] there and the runner's ticks stop depending on how fast the machine is: it emits one tick each time the
  * reading has moved on by the configured tick rate, so three advances of one tick rate produce exactly three ticks and
  * never a fourth.
  *
  * Each advance is one tick at most. When the runner fires a tick it records the reading it fired at, rather than
  * adding a rate to the previous deadline, so a single jump of three tick rates still fires once and puts the next tick
  * a whole rate after the jump. Three ticks are three advances.
  *
  * {{{
  * val clock = ManualClock()
  * val pilot = Pilot.start(Size(20, 3)) { backend =>
  *   TerminalRunner(backend, RunnerConfig(tickRate = Some(50.millis)), clock.reading).run(onStart, handle, render)
  * }
  * pilot.advanceClock(clock, 50.millis).advanceClock(clock, 50.millis)
  * }}}
  *
  * What this does *not* do is freeze animation time. `dsl.AnimationClock` reads the system clock itself, so a test
  * about where an animation has got to pins that separately with `AnimationClock.freezeAt`; this clock only decides
  * when the runner's loop believes a tick is due.
  *
  * Ownership and threads: the reading is an `AtomicLong` written by the test thread (through [[advance]]) and read by
  * the app thread (through [[reading]]), so no lock is needed and neither side can observe a half-written value. One
  * clock belongs to one runner: sharing it between two apps would make each one's advance move the other's schedule.
  *
  * @param start
  *   the reading the clock begins at. Any value works — the runner only ever compares two readings — so the default of
  *   zero is chosen because it makes [[elapsed]] read as "time since the test started".
  */
final class ManualClock(start: FiniteDuration = Duration.Zero):

  private val nanos: AtomicLong = AtomicLong(start.toNanos)

  /** The clock itself, in the shape `TerminalRunner` wants: hand it over as the runner's `nanoTime` argument. */
  val reading: () => Long = () => nanos.get()

  /** How far the clock has been advanced past [[start]] — what a test prints when an assertion about ticks fails. */
  def elapsed: FiniteDuration = Duration.fromNanos(nanos.get() - start.toNanos)

  /** Moves the clock forward by `delta` and returns this clock, so advances chain.
    *
    * The app thread notices on its next pass around the event loop, which is at most one poll away; it does not have to
    * be woken. What the manual clock buys is not immediacy but *exactness* — how many ticks an advance produces stops
    * depending on how long anything took.
    *
    * A zero or negative delta throws: a clock that stood still or ran backwards is a mistake in the test, and the
    * runner compares readings to decide when a tick is due, so a rewind would silently postpone the next one.
    */
  def advance(delta: FiniteDuration): ManualClock =
    require(delta > Duration.Zero, s"advance needs a positive delta, got $delta")
    val _ = nanos.addAndGet(delta.toNanos)
    this
