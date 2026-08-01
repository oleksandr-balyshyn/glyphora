package io.worxbend.tui.widgets

/** Horizontal placement of a line inside the area it is drawn in — used by [[Paragraph]] for its text and by [[Block]]
  * for its title. A line wider than the area is clipped from the right regardless of alignment.
  */
enum Alignment:
  case Left, Center, Right
