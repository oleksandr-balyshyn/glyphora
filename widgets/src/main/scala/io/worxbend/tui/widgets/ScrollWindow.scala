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
    */
  def offsetFor(offset: Int, selected: Option[Int], total: Int, viewportHeight: Int): Int =
    val maxOffset = math.max(0, total - viewportHeight)
    val clamped   = math.max(0, math.min(offset, maxOffset))
    selected match
      case None        => clamped
      case Some(index) =>
        // `selected` is caller-owned and may be a "nothing highlighted" sentinel of -1, which must never become a
        // negative offset — callers index their content with it directly.
        if index < clamped then math.max(0, index)
        else if index >= clamped + viewportHeight then math.max(0, index - viewportHeight + 1)
        else clamped
