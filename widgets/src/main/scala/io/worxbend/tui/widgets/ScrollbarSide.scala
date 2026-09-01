package io.worxbend.tui.widgets

/** Which of the two edges available to its axis a [[Scrollbar]] draws on.
  *
  * The sides are named by position along the axis rather than by compass point, so one type serves both orientations:
  * `Far` is the right edge of a vertical bar and the bottom edge of a horizontal one, `Near` the left edge and the top
  * edge. Naming them this way also keeps `core.Direction` a plain axis with no opinion about sides.
  *
  * `Far` is the conventional placement and the default, so a caller that says nothing keeps the bar exactly where it
  * has always been. `Near` is for a left-hand gutter beside content, or a horizontal ruler drawn above it.
  */
enum ScrollbarSide:
  case Near, Far
