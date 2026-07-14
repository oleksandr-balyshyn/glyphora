package io.worxbend.tui.core

/** A terminal color: the 8 named ANSI colors, a 24-bit RGB value, or a 256-color palette index.
  *
  * `Reset` restores the terminal's default foreground/background rather than naming a concrete color.
  */
enum Color:
  case Reset, Black, Red, Green, Yellow, Blue, Magenta, Cyan, White
  case Rgb(r: Int, g: Int, b: Int)
  case Indexed(index: Int)

object Color:

  /** RGB approximation for every color model — good enough for fades and capability downsampling, not for
    * color management. Named colors use common terminal palette values; indexed colors decode the xterm
    * 256-color cube and grayscale ramp.
    */
  def approximateRgb(color: Color): (Int, Int, Int) =
    color match
      case Rgb(r, g, b) => (r, g, b)
      case Black        => (0, 0, 0)
      case Red          => (205, 49, 49)
      case Green        => (13, 188, 121)
      case Yellow       => (229, 229, 16)
      case Blue         => (36, 114, 200)
      case Magenta      => (188, 63, 188)
      case Cyan         => (17, 168, 205)
      case White        => (229, 229, 229)
      case Reset        => (192, 192, 192)
      case Indexed(index) =>
        if index < 16 then if index < 8 then (index * 24, index * 24, index * 24) else (192, 192, 192)
        else if index >= 232 then
          val gray = 8 + (index - 232) * 10
          (gray, gray, gray)
        else
          val cube  = index - 16
          val steps = Vector(0, 95, 135, 175, 215, 255)
          (steps(cube / 36), steps(cube / 6 % 6), steps(cube % 6))
