package io.worxbend.tui.dsl

import io.worxbend.tui.core.Effect
import io.worxbend.tui.runtime.Frame

import scala.concurrent.duration.DurationLong

/** The post-render [[Effect]]s a [[TuiApp]] has started and not yet finished, behind [[TuiApp.runEffect]].
  *
  * An effect is a pure function of how long it has been running, so all this has to keep is the start time of each one.
  * `now` is the clock, in nanoseconds, as a parameter rather than a direct `System.nanoTime()` call, so a test can step
  * an effect through its phases without waiting for wall-clock time to pass.
  *
  * One of these belongs to one [[TuiApp]] instance and every method runs on that app's render thread — `start` from an
  * event handler, `applyTo` from the render pass, `prune` from the tick stage — so its field is unsynchronised.
  */
private[dsl] final class EffectStack(now: () => Long):

  private var running: List[(Effect, Long)] = Nil

  /** Starts `effect` over the whole frame, timed from this moment. */
  def start(effect: Effect): Unit =
    running = (effect, now()) :: running

  def isEmpty: Boolean = running.isEmpty

  /** Applies every running effect over the frame that was just composed. */
  def applyTo(frame: Frame): Unit =
    if running.nonEmpty then
      val at = now()
      running.foreach((effect, started) => frame.applyEffect(effect, (at - started).nanos))

  /** Drops finished effects; `true` when any were dropped, which is the caller's cue to schedule one more redraw so the
    * un-effected frame is what stays on screen.
    */
  def prune(): Boolean =
    if running.isEmpty then false
    else
      val at              = now()
      val (done, ongoing) = running.partition((effect, started) => effect.isDone((at - started).nanos))
      running = ongoing
      done.nonEmpty
