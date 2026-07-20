package io.worxbend.tui.runtime

import scala.concurrent.duration.{Duration, DurationLong, FiniteDuration}

/** `hh:mm:ss` (or `mm:ss` under an hour) for a non-negative duration — the usual readout for [[Stopwatch]]/[[Timer]].
  */
def formatDuration(duration: FiniteDuration): String =
  val totalSeconds = math.max(0L, duration.toSeconds)
  val hours        = totalSeconds / 3600
  val minutes      = totalSeconds % 3600 / 60
  val seconds      = totalSeconds % 60
  if hours > 0 then f"$hours%d:$minutes%02d:$seconds%02d" else f"$minutes%02d:$seconds%02d"

/** A count-up timer advanced from the app's tick loop.
  *
  * Caller-owned mutable state, like the widget states: call [[tick]] each frame with the elapsed real time (e.g. the
  * runner's `tickRate`), and read [[elapsed]] / [[formatted]] in `view`. Time only accrues while [[isRunning]].
  */
final class Stopwatch(initial: FiniteDuration = Duration.Zero):
  private var elapsedNanos: Long = math.max(0L, initial.toNanos)
  private var active: Boolean    = false

  def start(): Unit  = active = true
  def stop(): Unit   = active = false
  def toggle(): Unit = active = !active
  def reset(): Unit  =
    elapsedNanos = 0L
    active = false

  def isRunning: Boolean = active

  /** Advances the clock by `delta` when running; a no-op otherwise (negative deltas are ignored). */
  def tick(delta: FiniteDuration): Unit =
    if active && delta > Duration.Zero then elapsedNanos += delta.toNanos

  def elapsed: FiniteDuration = elapsedNanos.nanos
  def formatted: String       = formatDuration(elapsed)

/** A count-down timer advanced from the app's tick loop; fires (via [[justExpired]]) once when it reaches zero.
  *
  * Caller-owned mutable state: call [[tick]] each frame with the elapsed real time, and read [[remaining]] /
  * [[isExpired]] / [[formatted]] in `view`.
  */
final class Timer(val duration: FiniteDuration):
  private var remainingNanos: Long = math.max(0L, duration.toNanos)
  private var active: Boolean      = false
  private var firedExpiry: Boolean = remainingNanos <= 0

  def start(): Unit  = if remainingNanos > 0 then active = true
  def stop(): Unit   = active = false
  def toggle(): Unit = if remainingNanos > 0 then active = !active
  def reset(): Unit  =
    remainingNanos = math.max(0L, duration.toNanos)
    active = false
    firedExpiry = remainingNanos <= 0

  def isRunning: Boolean = active
  def isExpired: Boolean = remainingNanos <= 0

  /** Counts `delta` down when running, clamping at zero and stopping on expiry. */
  def tick(delta: FiniteDuration): Unit =
    if active && delta > Duration.Zero then
      remainingNanos = math.max(0L, remainingNanos - delta.toNanos)
      if remainingNanos == 0 then active = false

  /** `true` exactly once, on the tick the timer first hits zero — the place to fire a timeout side effect. */
  def justExpired(): Boolean =
    if isExpired && !firedExpiry then
      firedExpiry = true
      true
    else false

  def remaining: FiniteDuration = remainingNanos.nanos
  def formatted: String         = formatDuration(remaining)
