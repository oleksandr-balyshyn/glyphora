package io.worxbend.tui.runtime

import scala.concurrent.duration.{DurationLong, FiniteDuration}

/** A count-down timer advanced from the app's tick loop; fires (via [[justExpired]]) once when it reaches zero.
  *
  * Caller-owned mutable state: call `tick` each frame with the elapsed real time, and read [[remaining]] /
  * [[isExpired]] / [[formatted]] in `view`. The start/stop/toggle controls and the tick guard come from [[TickDriven]];
  * what this class adds is the direction of travel, the clamp at zero and the one-shot expiry latch.
  */
final class Timer(val duration: FiniteDuration) extends TickDriven:
  private var remainingNanos: Long = math.max(0L, duration.toNanos)
  private var firedExpiry: Boolean = remainingNanos <= 0

  /** An expired timer has nowhere left to count, so `start`/`toggle` on one do nothing until it is [[reset]]. */
  override protected def canRun: Boolean = remainingNanos > 0

  /** Counts `delta` down, clamping at zero and stopping the clock on expiry. */
  protected def advance(deltaNanos: Long): Unit =
    remainingNanos = math.max(0L, remainingNanos - deltaNanos)
    if remainingNanos == 0 then halt()

  def reset(): Unit =
    remainingNanos = math.max(0L, duration.toNanos)
    stop()
    firedExpiry = remainingNanos <= 0

  def isExpired: Boolean = remainingNanos <= 0

  /** `true` exactly once, on the tick the timer first hits zero — the place to fire a timeout side effect. */
  def justExpired(): Boolean =
    if isExpired && !firedExpiry then
      firedExpiry = true
      true
    else false

  def remaining: FiniteDuration = remainingNanos.nanos
  def formatted: String         = formatDuration(remaining)
