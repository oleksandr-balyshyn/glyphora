package io.worxbend.tui.terminal

import io.worxbend.tui.core.Rect

/** Whether the display has to be erased before the next frame is written.
  *
  * A terminal that gets *narrower* reflows what it is already showing: a row longer than the new width wraps onto the
  * row below it, so glyphs the application will never address again reappear at coordinates it does not own. Repainting
  * every cell of the new, smaller area cannot remove them, because by definition they now sit outside it. Erasing the
  * display first is the only thing that can.
  *
  * A widening, or a change in height alone, reflows nothing — the terminal either has room for every existing row or
  * drops rows off the top, and neither leaves a glyph inside the app's own area that the app did not put there. Those
  * cases deliberately pay nothing, because erasing costs a visible flash of blank screen between the clear and the
  * repaint, and paying that on every resize would make an ordinary window drag flicker.
  *
  * Pure and total so the rule can be exercised on its own: `JLine3Backend` cannot be constructed without a controlling
  * terminal, which CI does not have (see [[JLine3BackendRedrawSpec]] for the same reasoning applied to the redraw
  * request).
  */
private[terminal] object ScreenReset:

  /** Whether moving from `previous` to `next` narrows the display, and so needs the screen erased first.
    *
    * `previous` is `None` when nothing has been flushed yet. A first frame has nothing on screen to reflow, so it never
    * asks for a clear — the alternate screen was already cleared on entry, and on the primary screen an unasked-for
    * erase would destroy whatever the user was looking at.
    */
  def clearsOnShrink(previous: Option[Rect], next: Rect): Boolean =
    previous.exists(before => next.width < before.width)
