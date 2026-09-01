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

  /** This color spelled as the Scala expression that rebuilds it: `Color.Red`, `Color.Rgb(1,2,3)`, `Color.Indexed(9)`.
    *
    * The derived `toString` already prints the shape — `Rgb(1,2,3)` — but without the `Color.` qualifier, which is the
    * one thing that stops it from compiling when pasted back into a test. See [[Style.asSource]], which builds on this
    * to print a whole style as code.
    */
  def asSource: String = s"Color.$this"

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
      // every remaining case is a singleton, whose derived `toString` is already its own name — the same
      // derivation `asSource` relies on
      case named          => named.toString

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

  /** Builds an [[Rgb]] color from HSL — Hue, Saturation and Lightness, the color notation CSS spells `hsl(30, 65%,
    * 55%)`.
    *
    * `hue` is an angle in degrees around the color wheel (0 red, 120 green, 240 blue) and it '''wraps''': `-30` and
    * `330` name the same color. `saturation` and `lightness` are fractions clamped to `0.0..1.0` — saturation `0.0` is
    * grey whatever the hue, lightness `0.0` is black and `1.0` is white. `NaN` in any argument is treated as `0.0`, the
    * same way [[clampUnit]] already handles it, so a divide-by-zero in a palette generator cannot paint a malformed
    * cell.
    *
    * Reach for HSL when colors have to relate to each other. `n` evenly spaced hues at one saturation and lightness is
    * a categorical chart palette — six distinguishable series are `Seq.tabulate(6)(i => Color.hsl(i * 60.0, 0.65,
    * 0.55))` — and moving only the lightness is a tint that keeps the hue, which is exactly what [[lighten]] (fading
    * toward white, so it washes the hue out) deliberately is not.
    */
  def hsl(hue: Double, saturation: Double, lightness: Double): Color =
    val h         = wrapHue(hue) / 60.0 // the wheel in sixths: one sextant per pair of primaries
    val s         = clampUnit(saturation)
    val l         = clampUnit(lightness)
    // `chroma` is how far this color travels from grey; `x` is the partly-mixed second channel inside the sextant,
    // and `m` lifts the whole triple to the requested lightness.
    val chroma    = (1.0 - math.abs(2.0 * l - 1.0)) * s
    val x         = chroma * (1.0 - math.abs(h % 2.0 - 1.0))
    val m         = l - chroma / 2.0
    val (r, g, b) = math.floor(h).toInt match
      case 0 => (chroma, x, 0.0)
      case 1 => (x, chroma, 0.0)
      case 2 => (0.0, chroma, x)
      case 3 => (0.0, x, chroma)
      case 4 => (x, 0.0, chroma)
      case _ => (chroma, 0.0, x)
    Rgb(channel(r + m), channel(g + m), channel(b + m))

  /** This color as `(hue, saturation, lightness)` — the inverse of [[hsl]]. Hue is in `0.0..360.0` degrees, saturation
    * and lightness in `0.0..1.0`. A grey has no hue to report, so it answers `0.0` for both hue and saturation.
    *
    * Goes through [[approximateRgb]], so a named or [[Indexed]] color answers for its palette approximation rather than
    * refusing. Round-tripping through [[hsl]] is accurate to within one unit per channel: the trip is through `Double`
    * arithmetic and back into 8-bit channels, so it rounds rather than reproducing the exact bytes.
    */
  def toHsl(color: Color): (Double, Double, Double) =
    val (ri, gi, bi) = approximateRgb(color)
    val r            = ri / 255.0
    val g            = gi / 255.0
    val b            = bi / 255.0
    val max          = math.max(r, math.max(g, b))
    val min          = math.min(r, math.min(g, b))
    val delta        = max - min
    val lightness    = (max + min) / 2.0
    if delta == 0.0 then (0.0, 0.0, lightness)
    else
      val saturation = delta / (1.0 - math.abs(2.0 * lightness - 1.0))
      val sextant    =
        if max == r then ((g - b) / delta) % 6.0
        else if max == g then (b - r) / delta + 2.0
        else (r - g) / delta + 4.0
      (wrapHue(sextant * 60.0), clampUnit(saturation), lightness)

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

  /** Mixes two colors the short way around the color wheel, in HSL rather than RGB: `t = 0` yields `a`, `t = 1` yields
    * `b`. Returns an [[Rgb]].
    *
    * [[mix]] interpolates the three channels independently, which is the right thing for a fade to a background but the
    * wrong thing for a ramp between two hues. Halfway from red to cyan in RGB is a dead grey, because the channels
    * cross over rather than travelling round the wheel; here it is the yellow-green that lies between them.
    *
    * The hue takes the shorter of the two arcs — red to magenta goes through purple, not through green — because a
    * two-colour ramp is nearly always meant as the short way. When a specific direction matters, build the stops with
    * [[hsl]] and the hue arithmetic you want.
    *
    * A grey has no hue to travel from, so when either end is unsaturated the hue of the other end is used for both and
    * only saturation and lightness move. Without that, mixing toward grey would swing the hue to `0` (red) on the way.
    */
  def mixHsl(a: Color, b: Color, t: Double): Color =
    val (ah, as, al) = toHsl(a)
    val (bh, bs, bl) = toHsl(b)
    val f            = clampUnit(t)
    // an unsaturated end has no meaningful hue of its own: borrow the other end's rather than swing through red
    val fromHue      = if as == 0.0 then bh else ah
    val toHue        = if bs == 0.0 then ah else bh
    // the signed short way round: wrapHue puts the difference in 0..360, and anything past a half turn is shorter
    // travelled backwards
    val forward      = wrapHue(toHue - fromHue)
    val arc          = if forward > 180.0 then forward - 360.0 else forward
    hsl(fromHue + arc * f, as + (bs - as) * f, al + (bl - al) * f)

  /** `steps` evenly-spaced colors from `from` to `to` inclusive, interpolated through HSL — the hue-space counterpart
    * of [[gradient]], built on [[mixHsl]] exactly as that one is built on [[mix]].
    *
    * This is what a chart ramp or a generated palette wants: the stops stay saturated all the way across instead of
    * sagging through grey in the middle. A 1-step gradient is just `from`, and a non-positive `steps` asks for no
    * colors and gets none.
    */
  def gradientHsl(from: Color, to: Color, steps: Int): Seq[Color] =
    if steps <= 0 then Seq.empty
    else if steps == 1 then Seq(mixHsl(from, to, 0))
    else Seq.tabulate(steps)(i => mixHsl(from, to, i.toDouble / (steps - 1)))

  /** The WCAG relative luminance of `color`: how much light it emits, from `0.0` (black) to `1.0` (white).
    *
    * "Relative luminance" is the brightness a human eye perceives, not the average of the three channels. Green carries
    * most of the perceived brightness and blue almost none, which is why the weights below are so uneven, and each
    * channel is first un-done from the sRGB encoding that display hardware applies. This is the standard definition
    * from the Web Content Accessibility Guidelines, and it is the input [[contrastRatio]] needs.
    *
    * Computed over [[approximateRgb]], so every colour answers. For the sixteen named ANSI colours the RGB a terminal
    * actually paints is chosen by the terminal emulator, not by this library, so the number is '''nominal''': it
    * describes the palette this library assumes — which is what a theme definition can be checked against — and not
    * what any particular terminal shows.
    */
  def luminance(color: Color): Double =
    val (r, g, b) = approximateRgb(color)
    0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)

  /** The WCAG contrast ratio between two colors, from `1.0` (indistinguishable) to `21.0` (black against white).
    *
    * This is the number accessibility guidance is written in: WCAG AA asks for at least `4.5` for normal text and `3.0`
    * for large text or interface components, AAA for `7.0`. Symmetric in its arguments — swapping foreground and
    * background gives the same ratio — and it carries the same nominal-palette caveat as [[luminance]].
    */
  def contrastRatio(a: Color, b: Color): Double =
    val la      = luminance(a)
    val lb      = luminance(b)
    val lighter = math.max(la, lb)
    val darker  = math.min(la, lb)
    (lighter + 0.05) / (darker + 0.05)

  /** [[Black]] or [[White]], whichever reads better on `background` — the label colour to use over a colour that was
    * computed rather than chosen, such as a heat-map cell or a generated series swatch.
    *
    * A tie goes to `Black`, following the convention that dark text is the default over an unknown mid-tone.
    *
    * This picks the better of two colours; it cannot promise the result is good enough. A background near the middle of
    * the range leaves neither black nor white far from it — against the nominal `Red` here, the better of the two
    * reaches only about `4.1`, short of the `4.5` WCAG AA asks for normal text. When a threshold has to be met, check
    * it with [[contrastRatio]] and change the background, which is the only thing that can actually fix it.
    */
  def readableOn(background: Color): Color =
    if contrastRatio(Black, background) >= contrastRatio(White, background) then Black else White

  /** One sRGB channel in `0..255` converted to its linear-light value, per WCAG 2.x.
    *
    * Display hardware does not treat a channel of 128 as half the light of 255 — the encoding is deliberately curved so
    * that the values available are spread the way the eye notices differences. Luminance arithmetic has to undo that
    * curve first, which is what this does.
    */
  private def linearize(channel: Int): Double =
    val c = channel / 255.0
    if c <= 0.04045 then c / 12.92 else math.pow((c + 0.055) / 1.055, 2.4)

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

    /** [[Color.mixHsl]]: this color mixed toward `other` through HSL, the short way around the color wheel. */
    def mixedThroughHueWith(other: Color, t: Double): Color = Color.mixHsl(color, other, t)

    /** [[Color.gradientHsl]]: `steps` evenly-spaced colors from this one to `to`, interpolated through HSL. */
    def hueGradientTo(to: Color, steps: Int): Seq[Color] = Color.gradientHsl(color, to, steps)

    /** [[Color.luminance]]: this color's WCAG relative luminance, `0.0` (black) to `1.0` (white). */
    // named `relativeLuminance` for the same reason `asHsl` is not `toHsl`: an extension's receiver becomes its first
    // parameter, so an extension called `luminance` would be ambiguous with the companion function at every call site
    def relativeLuminance: Double = Color.luminance(color)

    /** [[Color.contrastRatio]]: the WCAG contrast ratio between this color and `other`, `1.0` to `21.0`. Spelled to
      * read at the call site as `fg.contrastWith(bg)`.
      */
    def contrastWith(other: Color): Double = Color.contrastRatio(color, other)

    /** [[Color.toHsl]]: this color as `(hue, saturation, lightness)`.
      *
      * Spelled `asHsl` rather than `toHsl` on purpose: an extension's receiver becomes its first parameter, so an
      * extension named `toHsl` would be indistinguishable from the companion function `toHsl(color)` — every call
      * inside this file would be ambiguous. The two names name one derivation.
      */
    def asHsl: (Double, Double, Double) = Color.toHsl(color)

    /** This color with its hue rotated by `degrees` around the color wheel, keeping saturation and lightness. `180`
      * gives the complementary color; a grey has no hue, so rotating it changes nothing.
      */
    def rotateHue(degrees: Double): Color =
      val (h, s, l) = Color.toHsl(color)
      Color.hsl(h + degrees, s, l)

    /** This color with its lightness replaced by `lightness` in `0.0..1.0`, keeping hue and saturation — a tint that
      * stays the same color, unlike [[lighten]], which fades toward white and drains the hue as it goes.
      */
    def withLightness(lightness: Double): Color =
      val (h, s, _) = Color.toHsl(color)
      Color.hsl(h, s, lightness)

    /** This color with its saturation replaced by `saturation` in `0.0..1.0`, keeping hue and lightness. `0.0` is the
      * grey of the same lightness.
      */
    def withSaturation(saturation: Double): Color =
      val (h, _, l) = Color.toHsl(color)
      Color.hsl(h, saturation, l)

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

  /** Wraps an angle in degrees into `0.0..360.0`, so `-30` and `330` and `690` all name the same point on the color
    * wheel. `NaN` becomes `0.0`, for the reason [[clampUnit]] gives.
    */
  private def wrapHue(degrees: Double): Double =
    if degrees.isNaN then 0.0
    else
      val remainder = degrees % 360.0
      if remainder < 0.0 then remainder + 360.0 else remainder

  /** Turns a `0.0..1.0` channel fraction into a `0..255` byte, clamping so rounding at the ends cannot overshoot. */
  private def channel(value: Double): Int =
    if value.isNaN then 0 else clampChannel(math.round(value * 255.0).toInt)

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
