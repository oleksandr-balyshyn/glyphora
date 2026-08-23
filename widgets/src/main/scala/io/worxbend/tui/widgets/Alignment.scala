package io.worxbend.tui.widgets

/** Horizontal placement of a line inside the area it is drawn in — used by [[Paragraph]] for its text and by [[Block]]
  * for its title. A line wider than the area is clipped from the right regardless of alignment.
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
