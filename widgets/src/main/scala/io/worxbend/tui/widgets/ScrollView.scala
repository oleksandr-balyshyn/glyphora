package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Position, Rect, StatefulWidget, Widget}

/** Caller-owned scroll offset for a [[ScrollView]]. */
final class ScrollViewState:
  var offset: Int = 0
  private[widgets] var lastViewportHeight: Int = 1
  private[widgets] var lastContentHeight: Int = 0

  def scrollUp(count: Int = 1): Unit =
    offset = math.max(0, offset - count)

  def scrollDown(count: Int = 1): Unit =
    offset = math.min(math.max(0, lastContentHeight - lastViewportHeight), offset + count)

/** A vertically scrollable window over content taller than the viewport.
  *
  * The content renders at its full `contentHeight` into an offscreen buffer each frame; the visible window is blitted
  * into place and a scrollbar drawn on the right edge when the content overflows. Content height is explicit (the
  * caller knows its data) — an intrinsic-measure pass is future work.
  */
final case class ScrollView(
    content: Widget,
    contentHeight: Int,
    showScrollbar: Boolean = true,
) extends StatefulWidget[ScrollViewState]:

  def render(area: Rect, buffer: Buffer, state: ScrollViewState): Unit =
    if !area.isEmpty && contentHeight > 0 then
      val overflows = contentHeight > area.height
      val scrollbarWidth = if showScrollbar && overflows then 1 else 0
      val contentWidth = area.width - scrollbarWidth
      if contentWidth > 0 then
        state.lastViewportHeight = area.height
        state.lastContentHeight = contentHeight
        state.offset = math.max(0, math.min(state.offset, contentHeight - area.height))
        val offscreen = Buffer(Rect(0, 0, contentWidth, contentHeight))
        content.render(offscreen.area, offscreen)
        buffer.blit(offscreen, Position(area.x, area.y), Rect(0, state.offset, contentWidth, area.height))
        if scrollbarWidth == 1 then
          val scrollbarState = ScrollbarState(contentLength = contentHeight, position = state.offset)
          Scrollbar().render(area, buffer, scrollbarState)
