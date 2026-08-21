package io.worxbend.tui.core

/** A terminal color: the 16 named ANSI colors (8 standard + 8 bright), a 24-bit RGB value, or a 256-color palette
  * index.
  *
  * `Reset` restores the terminal's default foreground/background rather than naming a concrete color. The `Bright*`
  * variants map to the SGR 90–97 / 100–107 codes; terminals downsample them to the standard 8 when they cannot show 16.
  */
enum Color:
  case Reset, Black, Red, Green, Yellow, Blue, Magenta, Cyan, White
  case BrightBlack, BrightRed, BrightGreen, BrightYellow, BrightBlue, BrightMagenta, BrightCyan, BrightWhite
  case Rgb(r: Int, g: Int, b: Int)
  case Indexed(index: Int)

/** A pair of colors picked by the terminal's background: `light` on a light terminal, `dark` on a dark one (Lip Gloss's
  * `AdaptiveColor`). Resolve it against the theme or a detected background before styling — the render path only ever
  * sees a concrete [[Color]].
  */
final case class AdaptiveColor(light: Color, dark: Color):
  def resolve(darkBackground: Boolean): Color = if darkBackground then dark else light

object Color:

  /** Builds an [[Rgb]] color, clamping each channel to `0..255`. */
  def rgb(r: Int, g: Int, b: Int): Color =
    Rgb(clampChannel(r), clampChannel(g), clampChannel(b))

  /** Parses a CSS-style hex color: `#rrggbb`, `rrggbb`, `#rgb`, or `rgb` (leading `#` optional, case-insensitive).
    * `None` when the string is not a valid 3- or 6-digit hex color.
    */
  def hex(value: String): Option[Color] =
    val digits = if value.startsWith("#") then value.drop(1) else value
    if digits.length == 6 && digits.forall(isHexDigit) then
      Some(Rgb(byteAt(digits, 0), byteAt(digits, 2), byteAt(digits, 4)))
    else if digits.length == 3 && digits.forall(isHexDigit) then
      // #rgb expands each nibble to a byte: `f` -> `ff`
      Some(Rgb(expandNibble(digits.charAt(0)), expandNibble(digits.charAt(1)), expandNibble(digits.charAt(2))))
    else None

  /** Moves `color` toward white by `amount` in `0.0..1.0` (0 = unchanged, 1 = white). Returns an [[Rgb]]. */
  def lighten(color: Color, amount: Double): Color =
    val (r, g, b) = approximateRgb(color)
    val t         = clampUnit(amount)
    Rgb(lerp(r, 255, t), lerp(g, 255, t), lerp(b, 255, t))

  /** Moves `color` toward black by `amount` in `0.0..1.0` (0 = unchanged, 1 = black). Returns an [[Rgb]]. */
  def darken(color: Color, amount: Double): Color =
    val (r, g, b) = approximateRgb(color)
    val t         = clampUnit(amount)
    Rgb(lerp(r, 0, t), lerp(g, 0, t), lerp(b, 0, t))

  /** Linearly mixes two colors in RGB space: `t = 0` yields `a`, `t = 1` yields `b`. Returns an [[Rgb]]. */
  def mix(a: Color, b: Color, t: Double): Color =
    val (ar, ag, ab) = approximateRgb(a)
    val (br, bg, bb) = approximateRgb(b)
    val f            = clampUnit(t)
    Rgb(lerp(ar, br, f), lerp(ag, bg, f), lerp(ab, bb, f))

  /** `foreground` composited over `background` at opacity `alpha` (0 = fully background, 1 = fully foreground). A
    * software alpha since terminals have no real transparency; returns an [[Rgb]].
    */
  def blend(foreground: Color, background: Color, alpha: Double): Color =
    mix(background, foreground, alpha)

  /** `steps` evenly-spaced colors from `from` to `to` inclusive (a 1-step gradient is just `from`); a non-positive
    * `steps` asks for no colors and gets none. Builds on [[mix]].
    */
  def gradient(from: Color, to: Color, steps: Int): Seq[Color] =
    if steps <= 0 then Seq.empty
    else if steps == 1 then Seq(mix(from, to, 0))
    else Seq.tabulate(steps)(i => mix(from, to, i.toDouble / (steps - 1)))

  /** RGB approximation for every color model — good enough for fades and capability downsampling, not for color
    * management. Named colors use common terminal palette values; indexed colors decode the xterm 256-color cube and
    * grayscale ramp.
    */
  def approximateRgb(color: Color): (Int, Int, Int) =
    color match
      // the Rgb case takes its channels unchecked (only Color.rgb clamps), so clamp on the way out rather than let
      // an out-of-range literal reach the SGR encoder as a malformed escape
      case Rgb(r, g, b)   => (clampChannel(r), clampChannel(g), clampChannel(b))
      case Black          => (0, 0, 0)
      case Red            => (205, 49, 49)
      case Green          => (13, 188, 121)
      case Yellow         => (229, 229, 16)
      case Blue           => (36, 114, 200)
      case Magenta        => (188, 63, 188)
      case Cyan           => (17, 168, 205)
      case White          => (229, 229, 229)
      case BrightBlack    => (127, 127, 127)
      case BrightRed      => (255, 0, 0)
      case BrightGreen    => (0, 255, 0)
      case BrightYellow   => (255, 255, 0)
      case BrightBlue     => (92, 92, 255)
      case BrightMagenta  => (255, 0, 255)
      case BrightCyan     => (0, 255, 255)
      case BrightWhite    => (255, 255, 255)
      case Reset          => (192, 192, 192)
      case Indexed(index) =>
        // an index outside the palette is a caller mistake, not a crash: clamp rather than index off the cube
        paletteRgb(math.max(0, math.min(255, index)))

  /** Decodes one xterm-256 palette `entry`, which must already be in `0..255`.
    *
    * The palette is three regions laid end to end: `0..15` are the named ANSI colors, so they resolve through
    * [[AnsiPalette]] and agree with [[approximateRgb]]'s named cases rather than forming a grey ramp; `16..231` are a
    * 6x6x6 color cube whose index decomposes base-6 into red, green and blue levels from [[CubeLevels]]; `232..255` are
    * a 24-step grayscale ramp starting at 8 and rising by 10 per step.
    */
  private def paletteRgb(entry: Int): (Int, Int, Int) =
    if entry < CubeBase then approximateRgb(AnsiPalette(entry))
    else if entry >= GrayscaleBase then
      val gray = GrayscaleStart + (entry - GrayscaleBase) * GrayscaleStep
      (gray, gray, gray)
    else
      val cube = entry - CubeBase
      (CubeLevels(cube / 36), CubeLevels(cube / 6 % 6), CubeLevels(cube % 6))

  /** The six per-channel levels of the xterm 6x6x6 color cube, in cube-index order. */
  private val CubeLevels: Vector[Int] = Vector(0, 95, 135, 175, 215, 255)

  /** The first palette entry of the 6x6x6 color cube. */
  private val CubeBase: Int = 16

  /** The first palette entry of the 24-step grayscale ramp, and the ramp's first level and per-step increment. */
  private val GrayscaleBase: Int  = 232
  private val GrayscaleStart: Int = 8
  private val GrayscaleStep: Int  = 10

  /** The first sixteen palette entries, in xterm order — what `Indexed(0)`..`Indexed(15)` name. */
  private val AnsiPalette: Vector[Color] =
    Vector(
      Black,
      Red,
      Green,
      Yellow,
      Blue,
      Magenta,
      Cyan,
      White,
      BrightBlack,
      BrightRed,
      BrightGreen,
      BrightYellow,
      BrightBlue,
      BrightMagenta,
      BrightCyan,
      BrightWhite,
    )

  private def clampChannel(value: Int): Int = math.max(0, math.min(255, value))

  /** Clamps an interpolation factor to `0.0..1.0`. `NaN` — a divide-by-zero in an animation clock, say — clamps to
    * `0.0`, the identity end of every interpolation here: `math.min`/`math.max` propagate it, and `math.round(NaN)` is
    * `0`, which would otherwise paint the affected cells black.
    */
  private def clampUnit(value: Double): Double =
    if value.isNaN then 0.0 else math.max(0.0, math.min(1.0, value))

  /** Interpolates one channel, clamping the result so an out-of-range [[Rgb]] input cannot produce one. */
  private def lerp(from: Int, to: Int, t: Double): Int =
    clampChannel(math.round(from + (to - from) * t).toInt)

  private def isHexDigit(c: Char): Boolean =
    (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')

  /** The byte formed by the two hex nibbles at `index` and `index + 1`. */
  private def byteAt(digits: String, index: Int): Int =
    Character.digit(digits.charAt(index), 16) * 16 + Character.digit(digits.charAt(index + 1), 16)

  private def expandNibble(c: Char): Int =
    val v = Character.digit(c, 16)
    v * 16 + v
