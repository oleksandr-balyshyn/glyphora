package io.worxbend.tui.core

/** A terminal color: the 8 named ANSI colors, a 24-bit RGB value, or a 256-color palette index.
  *
  * `Reset` restores the terminal's default foreground/background rather than naming a concrete color.
  */
enum Color:
  case Reset, Black, Red, Green, Yellow, Blue, Magenta, Cyan, White
  case Rgb(r: Int, g: Int, b: Int)
  case Indexed(index: Int)
