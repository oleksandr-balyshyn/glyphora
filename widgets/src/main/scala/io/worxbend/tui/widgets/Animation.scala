package io.worxbend.tui.widgets

import io.worxbend.tui.core.Progress

import scala.concurrent.duration.FiniteDuration

/** The two ways an animation turns elapsed time into a discrete position, shared so every looping widget wraps the same
  * way — including at a negative elapsed time, which a caller subtracting two timestamps can produce.
  *
  * Both are the widget-facing names for [[io.worxbend.tui.core.Progress]], which is where the arithmetic lives: the
  * same `elapsed → position` answer that `Tween` and `Effect` use for their one-shot progress, so a looping widget and
  * a timed effect can never disagree about where a moment falls in a cycle.
  */
private[widgets] object Animation:

  /** Which of `steps` positions a cycle of length `period` is at after `elapsed`. See
    * [[io.worxbend.tui.core.Progress.stepped]] for the degenerate cases (`steps <= 0`, a zero period, a negative time).
    */
  def step(elapsed: FiniteDuration, period: FiniteDuration, steps: Int): Int =
    Progress.stepped(elapsed, period, steps)

  /** Which of `steps` positions a run at `perSecond` positions per second is at after `elapsed`. */
  def stepAtRate(elapsed: FiniteDuration, perSecond: Double, steps: Int): Int =
    Progress.steppedAtRate(elapsed, perSecond, steps)
