package io.worxbend.tui.core

import java.util.Locale

import scala.annotation.targetName

/** A terminal color: the 16 named ANSI colors (8 standard + 8 bright), a 24-bit RGB value, or a 256-color palette
  * index.
  *
  * `Reset` restores the terminal's default foreground/background rather than naming a concrete color. The `Bright*`
  * variants map to the SGR 90–97 / 100–107 codes; terminals downsample them to the standard 8 when they cannot show 16.
  */
enum Color:
  case Reset, Black, Red, Green, Yellow, Blue, Magenta, Cyan, White
  case BrightBlack, BrightRed, BrightGreen, BrightYellow, BrightBlue, BrightMagenta, BrightCyan, BrightWhite

  /** A 24-bit color, taken '''unchecked''': the channels are whatever the caller passed, including values outside
    * `0..255`. Use [[Color.rgb]] to build one that clamps. Everything in this file that reads channels clamps on the
    * way out, so an out-of-range literal cannot reach the SGR encoder as a malformed escape — but the value itself
    * keeps what it was given, which is why `Rgb(999, 0, 0) != Rgb(255, 0, 0)`.
    */
  case Rgb(r: Int, g: Int, b: Int)
  case Indexed(index: Int)

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

  /** Builds an [[Rgb]] color from a packed `0x00RRGGBB` integer — the shape a hex literal takes in source code:
    * `Color.fromInt(0xf8fafc)`. The top eight bits are ignored, so every `Int` names a color and this never fails,
    * unlike [[hex]], which reads text and answers `None` for anything malformed. Being total is what lets it fill in a
    * table of palette constants without an unsafe `.get` on an `Option`.
    */
  def fromInt(packed: Int): Color =
    Rgb((packed >> 16) & 0xff, (packed >> 8) & 0xff, packed & 0xff)

  /** The `0x00RRGGBB` packing of `color`, taken through [[approximateRgb]] — so a named or indexed color answers with
    * the palette value it approximates to, and the top byte is always zero. Round-trips with [[fromInt]] for any
    * [[Rgb]] whose channels are already in `0..255`.
    */
  def toInt(color: Color): Int =
    val (r, g, b) = approximateRgb(color)
    (r << 16) | (g << 8) | b

  /** Reads a color written the way a configuration or theme file writes one. Three forms are accepted:
    *
    *   - '''A name''': `reset`, `black`, `red`, `green`, `yellow`, `blue`, `magenta`, `cyan`, `white`, and the eight
    *     bright variants. Matching ignores case and the separators a hand-written name may carry, so `BrightRed`,
    *     `bright red`, `bright-red`, `light_red` and `brightred` are one and the same [[BrightRed]]. `light` is
    *     accepted everywhere `bright` is, because both spellings are in common use. `grey`, `gray`, `dark grey` and
    *     `dark gray` name [[BrightBlack]] — the mid grey a terminal shows for "bright black" — and `silver` names
    *     [[White]].
    *   - '''A hex color''', exactly what [[hex]] accepts: `#rrggbb`, `rrggbb`, `#rgb`, `rgb`.
    *   - '''A bare xterm palette index''' in `0..255`, such as `42`, giving an [[Indexed]].
    *
    * A string of one to three decimal digits is always read as a palette index, never as hex: `120` is `Indexed(120)`,
    * and a caller who means the color `#120` writes the `#`. That ordering is what the author of a configuration file
    * expects, and it is why `999` is rejected rather than quietly becoming the grey `#999`.
    *
    * Failure is a `Left` carrying a message that names the rejected input — the same shape `KeyEvent.parse` uses — so a
    * bad line in a theme file is something the caller reports rather than an exception it has to catch. Every string
    * the `render` extension method writes parses back to an equal color; the reverse is lossy, since many spellings
    * share one color.
    */
  def parse(value: String): Either[String, Color] =
    val trimmed = value.trim
    if trimmed.isEmpty then Left("cannot read a color from an empty string")
    else if isDecimalRun(trimmed) then
      // a run of up to three digits is read as a palette index and nothing else, so `999` is an out-of-range index
      // rather than the grey `#999` — the reading the writer of the string did not ask for
      paletteIndex(trimmed).toRight(s"'$value' is not a palette index in 0..255")
    else
      namedColor(canonicalName(trimmed))
        .orElse(hex(trimmed))
        .toRight(s"'$value' is not a color name, a hex color like #rrggbb, or a palette index in 0..255")

  /** Writes a color in the spelling [[parse]] reads back — the implementation behind the `render` extension method
    * below, which is how callers reach it. It is private and separately named because an extension method in this
    * companion is itself a one-argument `render`, so a public `Color.render(color)` beside it would be an ambiguous
    * overload of the very method delegating to it.
    */
  private def renderText(color: Color): String =
    color match
      case Rgb(r, g, b)   =>
        val (cr, cg, cb) = approximateRgb(Rgb(r, g, b))
        f"#$cr%02x$cg%02x$cb%02x"
      case Indexed(index) => math.max(0, math.min(255, index)).toString
      case Reset          => "Reset"
      case Black          => "Black"
      case Red            => "Red"
      case Green          => "Green"
      case Yellow         => "Yellow"
      case Blue           => "Blue"
      case Magenta        => "Magenta"
      case Cyan           => "Cyan"
      case White          => "White"
      case BrightBlack    => "BrightBlack"
      case BrightRed      => "BrightRed"
      case BrightGreen    => "BrightGreen"
      case BrightYellow   => "BrightYellow"
      case BrightBlue     => "BrightBlue"
      case BrightMagenta  => "BrightMagenta"
      case BrightCyan     => "BrightCyan"
      case BrightWhite    => "BrightWhite"

  /** Lowercases a written name and drops the separators it may carry, so `Bright_Red`, `bright red` and `bright-red`
    * all reduce to the one key [[namedColor]] matches on.
    *
    * `Locale.ROOT`, not the default locale: in a Turkish locale `"Indexed".toLowerCase` is `"ındexed"`, with a dotless
    * i, which would match nothing — the same trap `KeyEvent.parse` avoids the same way.
    */
  private def canonicalName(value: String): String =
    value.toLowerCase(Locale.ROOT).filterNot(c => c == ' ' || c == '-' || c == '_')

  private def namedColor(key: String): Option[Color] =
    key match
      case "reset"                                   => Some(Reset)
      case "black"                                   => Some(Black)
      case "red"                                     => Some(Red)
      case "green"                                   => Some(Green)
      case "yellow"                                  => Some(Yellow)
      case "blue"                                    => Some(Blue)
      case "magenta"                                 => Some(Magenta)
      case "cyan"                                    => Some(Cyan)
      case "white" | "silver"                        => Some(White)
      case "grey" | "gray" | "darkgrey" | "darkgray" => Some(BrightBlack)
      case other                                     => brightVariant(other)

  /** The `bright`/`light` half of the sixteen-color set. Both prefixes name the SGR 90–97 codes; terminals and
    * configuration files disagree about which word to use, so both are read. `bright grey`/`light grey` are the one
    * pair that is not a `Bright*` case: a terminal's "bright grey" is plain [[White]].
    */
  private def brightVariant(key: String): Option[Color] =
    val base =
      if key.startsWith("bright") then Some(key.drop("bright".length))
      else if key.startsWith("light") then Some(key.drop("light".length))
      else None
    base.flatMap:
      case "black"         => Some(BrightBlack)
      case "red"           => Some(BrightRed)
      case "green"         => Some(BrightGreen)
      case "yellow"        => Some(BrightYellow)
      case "blue"          => Some(BrightBlue)
      case "magenta"       => Some(BrightMagenta)
      case "cyan"          => Some(BrightCyan)
      case "white"         => Some(BrightWhite)
      case "grey" | "gray" => Some(White)
      case _               => None

  /** Whether `value` is written the way a palette index is: one to three decimal digits and nothing else. A sign or any
    * non-digit disqualifies it, so `#12` and `-1` are read as the other forms instead of quietly becoming palette
    * entries, and a six-digit run like `123456` is still read as hex.
    */
  private def isDecimalRun(value: String): Boolean =
    value.nonEmpty && value.length <= MaxIndexDigits && value.forall(c => c >= '0' && c <= '9')

  /** The palette entry `value` names, or `None` when the number is outside `0..255`. */
  private def paletteIndex(value: String): Option[Color] =
    value.toIntOption.filter(index => index >= 0 && index <= 255).map(Indexed.apply)

  /** `255`, the largest palette index, is three digits long. */
  private val MaxIndexDigits: Int = 3

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

  /** The same derivations as the functions above, written as methods on the color so a chain reads in the order it
    * happens: `theme.mixedWith(accent, 0.3).darken(0.1)` instead of `darken(mix(theme, accent, 0.3), 0.1)`.
    *
    * Each one delegates to the companion function, which stays the implementation — there is one definition of what
    * "lighten" means, and this is a second way to spell it. Living in `Color`'s companion means they are found on any
    * `Color` value without an import.
    */
  extension (color: Color)

    /** [[Color.lighten]]: this color moved toward white by `amount` in `0.0..1.0`. */
    // the extension and the function it delegates to erase to the same JVM signature, so the extension takes a
    // distinct erased name; the Scala-level name is the one callers write either way
    @targetName("lightenedBy")
    def lighten(amount: Double): Color = Color.lighten(color, amount)

    /** [[Color.darken]]: this color moved toward black by `amount` in `0.0..1.0`. */
    @targetName("darkenedBy")
    def darken(amount: Double): Color = Color.darken(color, amount)

    /** [[Color.mix]]: this color mixed toward `other`, where `t = 0` keeps this one and `t = 1` yields `other`. */
    def mixedWith(other: Color, t: Double): Color = Color.mix(color, other, t)

    /** [[Color.blend]]: this color composited over `background` at opacity `alpha`. */
    def over(background: Color, alpha: Double): Color = Color.blend(color, background, alpha)

    /** [[Color.gradient]]: `steps` evenly-spaced colors from this one to `to`, inclusive. */
    def gradientTo(to: Color, steps: Int): Seq[Color] = Color.gradient(color, to, steps)

    /** This color written in the spelling [[Color.parse]] reads back: a named color as the name it has in this enum
      * (`"BrightBlue"`, `"Reset"`), an [[Color.Rgb]] as lowercase `#rrggbb`, an [[Color.Indexed]] as its bare decimal
      * index.
      *
      * Channels and indexes are clamped on the way out the same way [[Color.approximateRgb]] clamps them, so an
      * unchecked `Rgb(999, 0, 0)` writes `#ff0000` rather than a value nothing can read back. This is the inverse of
      * [[Color.parse]]; the compiler-generated `toString` is not — it writes `Rgb(255,136,0)`, which no configuration
      * file wants.
      */
    def render: String = Color.renderText(color)

    /** [[Color.toInt]]: this color packed into one `0x00RRGGBB` integer. */
    def packed: Int = Color.toInt(color)

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
