package io.worxbend.tui.runtime

import scala.concurrent.duration.{Duration, FiniteDuration}

/** The run/stop control surface shared by [[Stopwatch]] and [[Timer]].
  *
  * Both are caller-owned clocks that only move while running and are advanced by the app's tick loop, so the running
  * flag, the four control methods and the "ignore non-positive deltas" guard live here once. What differs between the
  * two — which direction the clock moves, and whether it may start at all — is expressed by the two hooks [[advance]]
  * and [[canRun]].
  *
  * Not thread-safe, and deliberately so: like widget state, an instance belongs to whichever thread drives it. In a
  * `TuiApp` that is the render thread, because `tick` is called from the event handler.
  */
private[runtime] trait TickDriven:

  private var active: Boolean = false

  /** Moves the clock by `deltaNanos` (always positive). Called only while running. */
  protected def advance(deltaNanos: Long): Unit

  /** Whether starting is meaningful right now. [[Timer]] says no once it has counted down to zero; a stopwatch always
    * has somewhere to go, so the default is `true`.
    */
  protected def canRun: Boolean = true

  /** Stops the clock from inside [[advance]] — how [[Timer]] halts itself on expiry. */
  protected def halt(): Unit = active = false

  def start(): Unit  = if canRun then active = true
  def stop(): Unit   = active = false
  def toggle(): Unit = if active then stop() else start()

  def isRunning: Boolean = active

  /** Advances the clock by `delta` when running; a no-op otherwise. Zero and negative deltas are ignored, so a tick
    * loop that hands over a clock that ran backwards (a system-clock adjustment, say) cannot rewind the reading.
    */
  def tick(delta: FiniteDuration): Unit =
    if active && delta > Duration.Zero then advance(delta.toNanos)
