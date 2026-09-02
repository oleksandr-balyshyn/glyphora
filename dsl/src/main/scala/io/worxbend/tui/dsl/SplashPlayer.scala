package io.worxbend.tui.dsl

import io.worxbend.tui.runtime.Frame

import scala.concurrent.duration.{DurationInt, DurationLong, FiniteDuration}

/** Plays a [[SplashScreen]] intro for one run of a [[TuiApp]].
  *
  * The intro owns the first few frames outright: while [[isActive]] the app renders this instead of its view and any
  * key skips it. It finishes when both the effect and the screen's `minimumDuration` have elapsed.
  *
  * `now` is the clock, in nanoseconds, and is a parameter rather than a direct `System.nanoTime()` call so a test can
  * step the intro through its phases without waiting for wall-clock time to pass.
  *
  * One of these belongs to one run and is touched only from that run's render thread, so its fields are unsynchronised.
  */
private[dsl] final class SplashPlayer(intro: Option[SplashScreen], now: () => Long):

  private var startNanos: Long  = 0L
  private var finished: Boolean = false
  private var skipped: Boolean  = false

  /** Whether the intro still owns the frame. `false` outright when the app declared no splash. */
  def isActive: Boolean = intro.nonEmpty && !finished && !skipped

  /** Ends the intro immediately — what any key press does. */
  def skip(): Unit = skipped = true

  /** Paints the intro's content with its effect applied at the elapsed time. The first call starts the clock, so the
    * intro is timed from the frame it first appeared on rather than from when the runner started.
    */
  def render(frame: Frame): Unit =
    intro.foreach { splash =>
      if startNanos == 0L then startNanos = now()
      frame.renderWidget(splash.content.widget, frame.area)
      frame.applyEffect(splash.effect, elapsed)
    }

  /** Flips the intro to finished once its effect and minimum duration have both elapsed; `true` on the tick that did
    * it, so the caller knows to schedule the redraw that shows the real view.
    */
  def advance(): Boolean =
    intro match
      case Some(splash) if isActive && startNanos != 0L =>
        val total = splash.effect.duration match
          case finite: FiniteDuration => if finite > splash.minimumDuration then finite else splash.minimumDuration
          case _                      => splash.minimumDuration
        if elapsed >= total then
          finished = true
          true
        else false
      case _                                            => false

  private def elapsed: FiniteDuration = (now() - startNanos).nanos

private[dsl] object SplashPlayer:

  /** How often an intro asks to be advanced. The intro is animated frame by frame, so it needs a clock even when the
    * app configured no `tickRate` of its own — `TuiApp` asks its ambient ticker for this interval while the intro is
    * active, and the demand goes away with the frame that ends it.
    */
  val TickRate: FiniteDuration = 50.millis
