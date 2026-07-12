package io.worxbend.tui.runtime

import io.worxbend.tui.core.{Buffer, Rect, StatefulWidget, Widget}

/** One frame being rendered: the drawable `area` plus the buffer widgets write into.
  *
  * The buffer itself stays module-private — application render code goes through the widget contract, which keeps every
  * write attributable to a widget and an area.
  */
final class Frame(val area: Rect, private[runtime] val buffer: Buffer):

  def renderWidget(widget: Widget, area: Rect): Unit =
    widget.render(area, buffer)

  def renderStatefulWidget[S](widget: StatefulWidget[S], area: Rect, state: S): Unit =
    widget.render(area, buffer, state)

  /** Applies a post-render [[Effect]] to what has been drawn so far — call after the widgets rendered. */
  def applyEffect(effect: Effect, elapsed: scala.concurrent.duration.FiniteDuration): Unit =
    effect.process(elapsed, buffer, area)
