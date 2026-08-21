package io.worxbend.tui.runtime

import scala.concurrent.duration.{Duration, DurationLong, FiniteDuration}

/** A count-up timer advanced from the app's tick loop.
  *
  * Caller-owned mutable state, like the widget states: call `tick` each frame with the elapsed real time (e.g. the
  * runner's `tickRate`), and read [[elapsed]] / [[formatted]] in `view`. Time only accrues while running; the
  * start/stop/toggle controls and the tick guard come from [[TickDriven]].
  */
final class Stopwatch(initial: FiniteDuration = Duration.Zero) extends TickDriven:
  private var elapsedNanos: Long = math.max(0L, initial.toNanos)

  protected def advance(deltaNanos: Long): Unit = elapsedNanos += deltaNanos

  def reset(): Unit =
    elapsedNanos = 0L
    stop()

  def elapsed: FiniteDuration = elapsedNanos.nanos
  def formatted: String       = formatDuration(elapsed)
