package io.worxbend.tui.widgets

/** The scroll-to-selection rule every selectable list widget follows.
  *
  * Widgets keep their scroll offset in caller-owned state and re-derive it during render, so this is a pure function of
  * the offset the caller last saw and the geometry of the current frame.
  */
private[widgets] object ScrollWindow:

  /** The offset to render at: `offset` clamped so the window never runs past the end of the content, then nudged by the
    * smallest amount that brings `selected` back inside it.
    *
    * Nudging by the minimum is what makes a one-row keyboard move scroll by one row instead of re-centering the view.
    * With no selection the clamped offset stands, so scrolling a list nobody has selected in still works.
    *
    * `padding` widens what "inside the window" means. With the default of zero the selection may sit on the very first
    * or very last visible row, which is the classic "the cursor is welded to the bottom line" feel: the reader cannot
    * see what is coming next. With `padding = 2` the window is nudged so that two further rows of content stay visible
    * on each side of the selection whenever the content has that much to show, so pressing "down" starts scrolling the
    * list underneath a cursor that has stopped two rows short of the edge.
    *
    * Two clamps keep that well behaved. The padding itself is capped at `(viewportHeight - 1) / 2`, because a padding
    * bigger than half the window would ask for more rows above and below the selection than the window holds, and the
    * two demands would then fight each other into an oscillation from frame to frame. And the resulting offset is
    * clamped back into `0 .. total - viewportHeight`, so near either end of the content — where there is nothing left
    * to reveal — the selection does reach the edge row rather than the list scrolling past its own contents.
    */
  def offsetFor(offset: Int, selected: Option[Int], total: Int, viewportHeight: Int, padding: Int = 0): Int =
    val maxOffset = math.max(0, total - viewportHeight)
    val clamped   = math.max(0, math.min(offset, maxOffset))
    selected match
      case None        => clamped
      case Some(index) =>
        val pad     = math.max(0, math.min(padding, (viewportHeight - 1) / 2))
        // the highest offset that still leaves `pad` rows of content visible above the selection, and the lowest that
        // leaves `pad` rows visible below it. With `pad == 0` these are exactly the old two branches.
        val highest = math.max(0, index - pad)
        val lowest  = math.max(0, index + pad - viewportHeight + 1)
        val nudged  =
          if clamped > highest then highest
          else if clamped < lowest then lowest
          else clamped
        // the selection is caller-owned, so clamp rather than trust it: an index outside the content must never become
        // an offset outside the content, because callers index their content with the result directly.
        math.max(0, math.min(nudged, maxOffset))
