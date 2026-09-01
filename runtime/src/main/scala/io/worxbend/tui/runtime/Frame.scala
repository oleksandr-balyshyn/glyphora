package io.worxbend.tui.runtime

import io.worxbend.tui.core.{Buffer, Effect, Rect, StatefulWidget, Widget}
import scala.concurrent.duration.FiniteDuration

/** One frame being rendered: the drawable `area` plus the buffer widgets write into.
  *
  * The buffer itself stays module-private — application render code goes through the widget contract, which keeps every
  * write attributable to a widget and an area.
  *
  * `count` is how many frames this runner composed before this one: the first frame of a run is `0` and the number goes
  * up by one per composed frame, wrapping back to `0` after `Long.MaxValue` (about 300 million years at a thousand
  * frames a second — the wrap is stated so the type is total, not because anyone will see it). It counts *composed*
  * frames, not cells that reached the terminal: a frame the backend's diff turned into no output at all still consumed
  * its number. Use it to do expensive work only every Nth frame, or to label a frame in a debug overlay. Do **not**
  * drive animation from it — a frame rate is not a clock, and the same animation would then run at a different speed on
  * a fast terminal than on a slow one. `core.Progress` with a real elapsed duration is the one owner of
  * time-to-position arithmetic.
  */
final class Frame(val area: Rect, private[runtime] val buffer: Buffer, val count: Long = 0L):

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
