package io.worxbend.tui.terminal

import io.worxbend.tui.core.Color

/** How many colors the terminal can actually show; RGB output is downsampled to fit.
  *
  * [[NoColor]] is not a device capability but an explicit opt-out: when it is in effect the backend emits text
  * attributes (bold, underline, …) but no foreground/background color at all. It is what honoring the `NO_COLOR`
  * convention resolves to.
  */
enum ColorDepth:
  case TrueColor, Ansi256, Ansi16, NoColor

object ColorDepth:

  /** Resolves the effective color depth from the environment.
    *
    * Precedence follows the widely-adopted conventions:
    *   1. `NO_COLOR` set to any non-empty value disables color entirely (see https://no-color.org) — unless
    *   2. `CLICOLOR_FORCE` is set to a non-zero value, which forces color on even when `NO_COLOR` asks for none or the
    *      output is not a TTY.
    *   3. Otherwise `COLORTERM=truecolor|24bit` wins, a `256color` `TERM` falls back to the 256 palette, and everything
    *      else to the classic 16.
    */
  def detect(env: Map[String, String] = sys.env): ColorDepth =
    val forced   = env.get("CLICOLOR_FORCE").exists(value => value.nonEmpty && value != "0")
    val disabled = env.get("NO_COLOR").exists(_.nonEmpty)
    if disabled && !forced then NoColor
    else capability(env)

  private def capability(env: Map[String, String]): ColorDepth =
    val colorterm = env.getOrElse("COLORTERM", "").toLowerCase
    if colorterm.contains("truecolor") || colorterm.contains("24bit") then TrueColor
    else if env.getOrElse("TERM", "").contains("256") then Ansi256
    else Ansi16

  /** Reduces `color` to something `depth` can represent (identity for capable terminals). [[NoColor]] is handled by the
    * SGR encoder dropping color codes, so this returns the color unchanged for it.
    */
  def downsample(color: Color, depth: ColorDepth): Color =
    depth match
      case TrueColor => color
      case NoColor   => color
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
