package io.worxbend.tui.core

/** Horizontal placement of content inside the columns it is drawn in.
  *
  * A [[Line]] can carry one of these to say where that single row sits, and the widgets that draw text (a paragraph, a
  * bordered title, a button caption) take one to place everything they draw. Content wider than the space it is placed
  * in is clipped from the right regardless of alignment.
  *
  * This enum lives in `tui-core` rather than in `tui-widgets` because `Line` — which is a core value — needs to name
  * it, and the dependency edges only point downward: nothing in `tui-core` may reference a widget.
  */
enum Alignment:
  case Left, Center, Right

  /** The column content of `contentWidth` starts at, when placed this way inside `contentWidth` columns beginning at
    * `areaX`.
    *
    * The arithmetic lives here rather than in each widget because the only interesting part of it is the guard: content
    * *wider* than the space it is placed in leaves a negative difference, and half of a negative number is a start
    * column to the left of the area. Clamping the difference at zero pins over-wide content to `areaX` and lets the
    * caller's own clipping deal with the overflow, which is how overflow is handled everywhere else in the toolkit.
    *
    * @param areaX
    *   the leftmost column available
    * @param areaWidth
    *   how many columns are available from `areaX`
    * @param contentWidth
    *   the display width of what is being placed, in terminal columns (not characters)
    */
  def originAt(areaX: Int, areaWidth: Int, contentWidth: Int): Int = this match
    case Left   => areaX
    case Center => areaX + math.max(0, areaWidth - contentWidth) / 2
    case Right  => areaX + math.max(0, areaWidth - contentWidth)
