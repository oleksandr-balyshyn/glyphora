package io.worxbend.tui.runtime

import io.worxbend.tui.core.{Buffer, Effect, Rect, StatefulWidget, Widget}
import scala.concurrent.duration.FiniteDuration

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

  /** Applies a post-render [[Effect]] to what has been drawn so far — call after the widgets rendered.
    *
    * `elapsed` is time since **this effect** started, not since this frame began and not since the application started.
    * The frame does not track it: an effect is a pure function of the elapsed time it is given, so the caller is the
    * one that has to remember each effect's start. `TuiApp.runEffect` in `tui-dsl` keeps that timestamp for every
    * active effect and passes the difference here; code driving a bare [[Runner]] must do the same.
    */
  def applyEffect(effect: Effect, elapsed: FiniteDuration): Unit =
    effect.process(elapsed, buffer, area)
