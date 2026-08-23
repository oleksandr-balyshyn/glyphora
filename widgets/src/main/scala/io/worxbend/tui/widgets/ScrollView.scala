package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, StatefulWidget, Widget}

/** Caller-owned scroll offset for a [[ScrollView]].
  *
  * Render-thread-only, and mutating it does not by itself schedule a frame. This is a plain mutable object, invisible
  * to the reactive layer: a background result written straight into it stays off screen until something unrelated
  * happens to repaint. Pair the mutation with a `Signal` write, or call `TuiApp.requestRedraw()` from the same
  * render-thread callback that made it.
  */
final class ScrollViewState:
  var offset: Int                              = 0
  private[widgets] var lastViewportHeight: Int = 1
  private[widgets] var lastContentHeight: Int  = 0

  def scrollUp(count: Int = 1): Unit =
    offset = math.max(0, offset - count)

  def scrollDown(count: Int = 1): Unit =
    offset = math.min(math.max(0, lastContentHeight - lastViewportHeight), offset + count)

/** A vertically scrollable window over content taller than the viewport.
  *
  * Each frame the content is handed its full `contentHeight` rect — it lays itself out as if all of it were on screen —
  * but the offscreen buffer it draws into covers only the scrolled-to window, so rows above and below are clipped by
  * the buffer's own bounds instead of being drawn and thrown away. That keeps the per-frame cost proportional to the
  * viewport rather than to the document: a 10 000-row content used to allocate two 2-million-element arrays and fill
  * them on every keystroke. The window is then blitted into place and a scrollbar drawn on the right edge when the
  * content overflows. Content height is explicit (the caller knows its data) — an intrinsic-measure pass is future
  * work.
  */
final case class ScrollView(
    content: Widget,
    contentHeight: Int,
    showScrollbar: Boolean = true,
) extends StatefulWidget[ScrollViewState]:

  def render(area: Rect, buffer: Buffer, state: ScrollViewState): Unit =
    if !area.isEmpty && contentHeight > 0 then
      val overflows      = contentHeight > area.height
      val scrollbarWidth = if showScrollbar && overflows then 1 else 0
      val contentWidth   = area.width - scrollbarWidth
      if contentWidth > 0 then
        state.lastViewportHeight = area.height
        state.lastContentHeight = contentHeight
        state.offset = math.max(0, math.min(state.offset, contentHeight - area.height))
        // the window in the content's own coordinate space: rows `state.offset` up to the bottom of the viewport
        val window    = Rect(0, state.offset, contentWidth, math.min(area.height, contentHeight - state.offset))
        val offscreen = Buffer(window)
        // the content still receives the whole `contentHeight` rect, so anything that positions itself relative to the
        // document (a header at row 0, a footer at the last row) lands where it always did; the buffer clips the rest
        content.render(Rect(0, 0, contentWidth, contentHeight), offscreen)
        buffer.blit(offscreen, area.position, window)
        if scrollbarWidth == 1 then Scrollbar(contentHeight, state.offset).render(area, buffer)
