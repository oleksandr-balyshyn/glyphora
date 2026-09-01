package io.worxbend.tui.core.palette

import io.worxbend.tui.core.Color

/** One Material tonal ramp: ten shades of a hue, from the near-white `c50` to the deep `c900`.
  *
  * The numbers are Material's own step names, not indices — 50, 100, 200 … 900 — and a step means the same visual
  * weight in every ramp, so `Material.Red.c500` and `Material.Blue.c500` sit at the same level. Pick a step once for a
  * role (a `c700` bar, a `c100` surface) and any hue swapped into that role keeps the design intact.
  *
  * Material's ramps stop at `c900`; Tailwind's [[Shades]] have a further `c950`, which is why these are two types
  * rather than one shared one. A plain immutable value with no behaviour beyond the two lookups below.
  */
final case class Tonal(
    c50: Color,
    c100: Color,
    c200: Color,
    c300: Color,
    c400: Color,
    c500: Color,
    c600: Color,
    c700: Color,
    c800: Color,
    c900: Color,
):

  /** All ten shades in ramp order, lightest first — for a step that is computed rather than written down, such as
    * mapping a series index onto a ramp.
    */
  def all: Seq[Color] = Seq(c50, c100, c200, c300, c400, c500, c600, c700, c800, c900)

  /** The shade named by a Material `step` (50, 100, 200 … 900), or `None` when `step` is not one of the ten.
    *
    * `None` rather than the nearest match on purpose: a step that is not in the ramp is a mistake in the caller's code,
    * and quietly painting a neighbouring colour would hide it until someone noticed the wrong shade on screen.
    */
  def shade(step: Int): Option[Color] =
    val index = Tonal.Steps.indexOf(step)
    if index < 0 then None else Some(all(index))

object Tonal:

  /** The ten Material step names, in ramp order — the lookup table [[Tonal.shade]] reads. */
  val Steps: Seq[Int] = Seq(50, 100, 200, 300, 400, 500, 600, 700, 800, 900)

/** A [[Tonal]] ramp plus the four saturated accent shades Material gives most of its hues.
  *
  * The accents are not further steps of the same ramp: they are deliberately more vivid than any of `c50`…`c900`, and
  * Material uses them for the one thing on a screen that has to be noticed — a selection, an alert, a call to action.
  * As a general surface or body colour they are exhausting, which is why they are named apart here rather than folded
  * into [[all]].
  */
final case class Accented(tonal: Tonal, a100: Color, a200: Color, a400: Color, a700: Color):

  /** The ten ordinary shades, lightest first — the ramp's own [[Tonal.all]]. */
  def all: Seq[Color] = tonal.all

  /** The four accent shades, in Material's order: `a100`, `a200`, `a400`, `a700`. */
  def accents: Seq[Color] = Seq(a100, a200, a400, a700)

  export tonal.{c50, c100, c200, c300, c400, c500, c600, c700, c800, c900, shade}

/** The Material Design 2014 colour palette: 16 hue ramps with accents, 3 without, plus a pure [[Black]] and [[White]].
  *
  * The second design-system palette `tui-core` ships, beside [[Tailwind]], and it exists for the same reason: the
  * sixteen named ANSI colours are whatever the reader's terminal theme says they are, and past those the only option is
  * a hand-picked hex literal. Choosing a coherent set of those is design work that has already been done, publicly,
  * twice over — and both answers are here as plain data.
  *
  * Which to reach for is taste rather than capability. Material's ramps are warmer and its accents far more saturated
  * than Tailwind's, so a Material accent shouts where a Tailwind `c500` speaks. Every value is a 24-bit
  * [[io.worxbend.tui.core.Color.Rgb]], so it renders identically wherever true colour is supported and is unaffected by
  * the user's theme — which is what you want for a chart series or a status badge, and deliberately what you do not
  * want for the general chrome of an application that should blend into the terminal around it.
  *
  * {{{
  * import io.worxbend.tui.core.palette.Material
  *
  * val warn     = Material.Amber.c700
  * val urgent   = Material.Red.a400 // an accent: for the one thing that must be seen
  * val surfaces = Seq(Material.BlueGray.c900, Material.BlueGray.c800, Material.BlueGray.c700)
  * }}}
  *
  * Names are spelled the Scala way (`DeepOrange`, `BlueGray`) rather than Material's own `DEEP_ORANGE`. Values are
  * transcribed from the published specification.
  *
  * @see
  *   [[https://m2.material.io/design/color/the-color-system.html the published palette]]
  */
object Material:

  /** Pure black. Named here so a design that wants true black, rather than the terminal's idea of `Color.Black`, can
    * say so.
    */
  val Black: Color = fromPacked(0x000000)

  /** Pure white, for the reason [[Black]] exists. */
  val White: Color = fromPacked(0xffffff)

  /** The Material `red` ramp, `c50` (lightest) to `c900` (darkest), plus its four accents. */
  val Red: Accented =
    accented(0xffebee, 0xffcdd2, 0xef9a9a, 0xe57373, 0xef5350, 0xf44336, 0xe53935, 0xd32f2f, 0xc62828, 0xb71c1c,
      0xff8a80, 0xff5252, 0xff1744, 0xd50000)

  /** The Material `pink` ramp, `c50` (lightest) to `c900` (darkest), plus its four accents. */
  val Pink: Accented =
    accented(0xfce4ec, 0xf8bbd0, 0xf48fb1, 0xf06292, 0xec407a, 0xe91e63, 0xd81b60, 0xc2185b, 0xad1457, 0x880e4f,
      0xff80ab, 0xff4081, 0xf50057, 0xc51162)

  /** The Material `purple` ramp, `c50` (lightest) to `c900` (darkest), plus its four accents. */
  val Purple: Accented =
    accented(0xf3e5f5, 0xe1bee7, 0xce93d8, 0xba68c8, 0xab47bc, 0x9c27b0, 0x8e24aa, 0x7b1fa2, 0x6a1b9a, 0x4a148c,
      0xea80fc, 0xe040fb, 0xd500f9, 0xaa00ff)

  /** The Material `deep purple` ramp, `c50` (lightest) to `c900` (darkest), plus its four accents. */
  val DeepPurple: Accented =
    accented(0xede7f6, 0xd1c4e9, 0xb39ddb, 0x9575cd, 0x7e57c2, 0x673ab7, 0x5e35b1, 0x512da8, 0x4527a0, 0x311b92,
      0xb388ff, 0x7c4dff, 0x651fff, 0x6200ea)

  /** The Material `indigo` ramp, `c50` (lightest) to `c900` (darkest), plus its four accents. */
  val Indigo: Accented =
    accented(0xe8eaf6, 0xc5cae9, 0x9fa8da, 0x7986cb, 0x5c6bc0, 0x3f51b5, 0x3949ab, 0x303f9f, 0x283593, 0x1a237e,
      0x8c9eff, 0x536dfe, 0x3d5afe, 0x304ffe)

  /** The Material `blue` ramp, `c50` (lightest) to `c900` (darkest), plus its four accents. */
  val Blue: Accented =
    accented(0xe3f2fd, 0xbbdefb, 0x90caf9, 0x64b5f6, 0x42a5f5, 0x2196f3, 0x1e88e5, 0x1976d2, 0x1565c0, 0x0d47a1,
      0x82b1ff, 0x448aff, 0x2979ff, 0x2962ff)

  /** The Material `light blue` ramp, `c50` (lightest) to `c900` (darkest), plus its four accents. */
  val LightBlue: Accented =
    accented(0xe1f5fe, 0xb3e5fc, 0x81d4fa, 0x4fc3f7, 0x29b6f6, 0x03a9f4, 0x039be5, 0x0288d1, 0x0277bd, 0x01579b,
      0x80d8ff, 0x40c4ff, 0x00b0ff, 0x0091ea)

  /** The Material `cyan` ramp, `c50` (lightest) to `c900` (darkest), plus its four accents. */
  val Cyan: Accented =
    accented(0xe0f7fa, 0xb2ebf2, 0x80deea, 0x4dd0e1, 0x26c6da, 0x00bcd4, 0x00acc1, 0x0097a7, 0x00838f, 0x006064,
      0x84ffff, 0x18ffff, 0x00e5ff, 0x00b8d4)

  /** The Material `teal` ramp, `c50` (lightest) to `c900` (darkest), plus its four accents. */
  val Teal: Accented =
    accented(0xe0f2f1, 0xb2dfdb, 0x80cbc4, 0x4db6ac, 0x26a69a, 0x009688, 0x00897b, 0x00796b, 0x00695c, 0x004d40,
      0xa7ffeb, 0x64ffda, 0x1de9b6, 0x00bfa5)

  /** The Material `green` ramp, `c50` (lightest) to `c900` (darkest), plus its four accents. */
  val Green: Accented =
    accented(0xe8f5e9, 0xc8e6c9, 0xa5d6a7, 0x81c784, 0x66bb6a, 0x4caf50, 0x43a047, 0x388e3c, 0x2e7d32, 0x1b5e20,
      0xb9f6ca, 0x69f0ae, 0x00e676, 0x00c853)

  /** The Material `light green` ramp, `c50` (lightest) to `c900` (darkest), plus its four accents. */
  val LightGreen: Accented =
    accented(0xf1f8e9, 0xdcedc8, 0xc5e1a5, 0xaed581, 0x9ccc65, 0x8bc34a, 0x7cb342, 0x689f38, 0x558b2f, 0x33691e,
      0xccff90, 0xb2ff59, 0x76ff03, 0x64dd17)

  /** The Material `lime` ramp, `c50` (lightest) to `c900` (darkest), plus its four accents. */
  val Lime: Accented =
    accented(0xf9fbe7, 0xf0f4c3, 0xe6ee9c, 0xdce775, 0xd4e157, 0xcddc39, 0xc0ca33, 0xafb42b, 0x9e9d24, 0x827717,
      0xf4ff81, 0xeeff41, 0xc6ff00, 0xaeea00)

  /** The Material `yellow` ramp, `c50` (lightest) to `c900` (darkest), plus its four accents. */
  val Yellow: Accented =
    accented(0xfffde7, 0xfff9c4, 0xfff59d, 0xfff176, 0xffee58, 0xffeb3b, 0xfdd835, 0xfbc02d, 0xf9a825, 0xf57f17,
      0xffff8d, 0xffff00, 0xffea00, 0xffd600)

  /** The Material `amber` ramp, `c50` (lightest) to `c900` (darkest), plus its four accents. */
  val Amber: Accented =
    accented(0xfff8e1, 0xffecb3, 0xffe082, 0xffd54f, 0xffca28, 0xffc107, 0xffb300, 0xffa000, 0xff8f00, 0xff6f00,
      0xffe57f, 0xffd740, 0xffc400, 0xffab00)

  /** The Material `orange` ramp, `c50` (lightest) to `c900` (darkest), plus its four accents. */
  val Orange: Accented =
    accented(0xfff3e0, 0xffe0b2, 0xffcc80, 0xffb74d, 0xffa726, 0xff9800, 0xfb8c00, 0xf57c00, 0xef6c00, 0xe65100,
      0xffd180, 0xffab40, 0xff9100, 0xff6d00)

  /** The Material `deep orange` ramp, `c50` (lightest) to `c900` (darkest), plus its four accents. */
  val DeepOrange: Accented =
    accented(0xfbe9e7, 0xffccbc, 0xffab91, 0xff8a65, 0xff7043, 0xff5722, 0xf4511e, 0xe64a19, 0xd84315, 0xbf360c,
      0xff9e80, 0xff6e40, 0xff3d00, 0xdd2c00)

  /** The Material `brown` ramp, `c50` (lightest) to `c900` (darkest). Material gives it no accents. */
  val Brown: Tonal =
    tonal(0xefebe9, 0xd7ccc8, 0xbcaaa4, 0xa1887f, 0x8d6e63, 0x795548, 0x6d4c41, 0x5d4037, 0x4e342e, 0x3e2723)

  /** The Material `gray` ramp, `c50` (lightest) to `c900` (darkest). Material gives it no accents. */
  val Gray: Tonal =
    tonal(0xfafafa, 0xf5f5f5, 0xeeeeee, 0xe0e0e0, 0xbdbdbd, 0x9e9e9e, 0x757575, 0x616161, 0x424242, 0x212121)

  /** The Material `blue gray` ramp, `c50` (lightest) to `c900` (darkest). Material gives it no accents. */
  val BlueGray: Tonal =
    tonal(0xeceff1, 0xcfd8dc, 0xb0bec5, 0x90a4ae, 0x78909c, 0x607d8b, 0x546e7a, 0x455a64, 0x37474f, 0x263238)

  /** Builds a ten-shade ramp from `0xrrggbb` literals, in the order Material lists them. */
  private def tonal(
      c50: Int,
      c100: Int,
      c200: Int,
      c300: Int,
      c400: Int,
      c500: Int,
      c600: Int,
      c700: Int,
      c800: Int,
      c900: Int,
  ): Tonal =
    Tonal(
      fromPacked(c50),
      fromPacked(c100),
      fromPacked(c200),
      fromPacked(c300),
      fromPacked(c400),
      fromPacked(c500),
      fromPacked(c600),
      fromPacked(c700),
      fromPacked(c800),
      fromPacked(c900),
    )

  /** Builds a ramp plus its four accents: the ten [[tonal]] literals followed by `a100`, `a200`, `a400`, `a700`. */
  private def accented(
      c50: Int,
      c100: Int,
      c200: Int,
      c300: Int,
      c400: Int,
      c500: Int,
      c600: Int,
      c700: Int,
      c800: Int,
      c900: Int,
      a100: Int,
      a200: Int,
      a400: Int,
      a700: Int,
  ): Accented =
    Accented(
      tonal(c50, c100, c200, c300, c400, c500, c600, c700, c800, c900),
      fromPacked(a100),
      fromPacked(a200),
      fromPacked(a400),
      fromPacked(a700),
    )

  /** Unpacks one `0xrrggbb` integer into a [[io.worxbend.tui.core.Color.Rgb]]. */
  private def fromPacked(packed: Int): Color =
    Color.Rgb((packed >> 16) & 0xff, (packed >> 8) & 0xff, packed & 0xff)
