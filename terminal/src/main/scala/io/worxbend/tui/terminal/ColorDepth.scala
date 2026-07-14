package io.worxbend.tui.terminal

import io.worxbend.tui.core.Color

/** How many colors the terminal can actually show; RGB output is downsampled to fit. */
enum ColorDepth:
  case TrueColor, Ansi256, Ansi16

object ColorDepth:

  /** Conventional environment-based detection: `COLORTERM=truecolor|24bit` wins, a `256color` TERM falls back to the
    * 256 palette, everything else to the classic 16.
    */
  def detect(env: Map[String, String] = sys.env): ColorDepth =
    val colorterm = env.getOrElse("COLORTERM", "").toLowerCase
    if colorterm.contains("truecolor") || colorterm.contains("24bit") then TrueColor
    else if env.getOrElse("TERM", "").contains("256") then Ansi256
    else Ansi16

  /** Reduces `color` to something `depth` can represent (identity for capable terminals). */
  def downsample(color: Color, depth: ColorDepth): Color =
    depth match
      case TrueColor => color
      case Ansi256   =>
        color match
          case rgb: Color.Rgb => Color.Indexed(nearestIndexed(rgb))
          case other          => other
      case Ansi16    =>
        color match
          case Color.Rgb(r, g, b)                  => nearestNamed(r, g, b)
          case Color.Indexed(index) if index >= 16 =>
            val (r, g, b) = Color.approximateRgb(Color.Indexed(index))
            nearestNamed(r, g, b)
          case other                               => other

  /** Nearest xterm-256 palette entry: the grayscale ramp for near-gray values, else the 6x6x6 color cube. */
  private def nearestIndexed(rgb: Color.Rgb): Int =
    val Color.Rgb(r, g, b) = rgb
    val isGrayish          = math.abs(r - g) < 10 && math.abs(g - b) < 10
    if isGrayish && r >= 4 && r <= 243 then 232 + math.min(23, math.max(0, (r - 8) / 10))
    else 16 + 36 * cubeStep(r) + 6 * cubeStep(g) + cubeStep(b)

  private def cubeStep(value: Int): Int =
    if value < 48 then 0 else if value < 115 then 1 else (value - 35) / 40

  private def nearestNamed(r: Int, g: Int, b: Int): Color =
    val candidates = Seq(
      Color.Black,
      Color.Red,
      Color.Green,
      Color.Yellow,
      Color.Blue,
      Color.Magenta,
      Color.Cyan,
      Color.White,
    )
    candidates.minBy { candidate =>
      val (cr, cg, cb) = Color.approximateRgb(candidate)
      val (dr, dg, db) = (cr - r, cg - g, cb - b)
      dr * dr + dg * dg + db * db
    }
