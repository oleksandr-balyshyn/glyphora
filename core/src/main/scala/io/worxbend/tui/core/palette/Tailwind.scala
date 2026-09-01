package io.worxbend.tui.core.palette

import io.worxbend.tui.core.Color

/** One Tailwind hue ramp: eleven shades of the same colour, from the near-white `c50` to the near-black `c950`.
  *
  * The numbers are Tailwind's own step names, not indices — they run 50, 100, 200 … 900, 950 — and they mean the same
  * thing in every ramp, so `Tailwind.Blue.c500` and `Tailwind.Red.c500` sit at the same visual weight. That is what a
  * ramp is for: pick the step once for a role (a `c600` border, a `c100` surface) and every hue swapped into that role
  * keeps the design intact.
  *
  * A plain immutable value with no behaviour beyond the two lookups below, so it costs nothing to hold in a theme or
  * carry through a render pass.
  */
final case class Shades(
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
    c950: Color,
):

  /** All eleven shades in ramp order, lightest first. Use this when the step is computed rather than written down —
    * mapping a series index onto a ramp, say, or building a heat scale.
    */
  def all: Seq[Color] = Seq(c50, c100, c200, c300, c400, c500, c600, c700, c800, c900, c950)

  /** The shade named by a Tailwind `step` (50, 100, 200 … 900, 950), or `None` when `step` is not one of the eleven.
    *
    * `None` rather than a nearest match on purpose: a step that is not in the ramp is a mistake in the caller's code,
    * and quietly painting the nearest colour would hide it until someone noticed the wrong shade on screen.
    */
  def shade(step: Int): Option[Color] =
    val index = Shades.Steps.indexOf(step)
    if index < 0 then None else Some(all(index))

object Shades:

  /** The eleven Tailwind step names, in ramp order — the lookup table [[Shades.shade]] reads. */
  val Steps: Seq[Int] = Seq(50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950)

/** The Tailwind CSS default colour palette: 22 hue ramps of eleven shades each, plus a pure [[Black]] and [[White]].
  *
  * A terminal application has two places to get a colour from, and there is a gap between them. The sixteen named ANSI
  * colours are whatever the user's terminal theme says they are — useful, because they follow the user's taste, but you
  * cannot know what `Color.Red` will actually look like or whether text on it will be readable. Past those, the only
  * option is a hand-picked hex literal, and choosing a coherent set of them is design work.
  *
  * This is that design work, already done and widely recognised. Every value here is a 24-bit [[Color.Rgb]], so it
  * renders identically in every terminal that supports true colour and is unaffected by the user's theme — which is
  * what you want for a chart series or a status badge, and what you deliberately do '''not''' want for the general
  * chrome of an application that should blend into the terminal around it.
  *
  * {{{
  * import io.worxbend.tui.core.palette.Tailwind
  *
  * val ok    = Tailwind.Emerald.c500
  * val warn  = Tailwind.Amber.c500
  * val panel = Tailwind.Slate.c800
  *
  * // one hue at several weights: the shape a hierarchy of surfaces takes
  * val surfaces = Seq(Tailwind.Slate.c900, Tailwind.Slate.c800, Tailwind.Slate.c700)
  * }}}
  *
  * Pure data in `tui-core` with no dependency of its own, so widgets, the DSL and applications all reach the same
  * names. Values are transcribed from the Tailwind CSS v3 default palette.
  *
  * @see
  *   [[https://tailwindcss.com/docs/customizing-colors#default-color-palette the published palette]]
  */
object Tailwind:

  /** Pure black. Named here so a design that wants true black, rather than the terminal's idea of `Color.Black`, can
    * say so.
    */
  val Black: Color = fromPacked(0x000000)

  /** Pure white, for the reason [[Black]] exists. */
  val White: Color = fromPacked(0xffffff)

  /** The Tailwind `slate` ramp, lightest (`c50`) to darkest (`c950`). */
  val Slate: Shades =
    shadesOf(0xf8fafc, 0xf1f5f9, 0xe2e8f0, 0xcbd5e1, 0x94a3b8, 0x64748b, 0x475569, 0x334155, 0x1e293b, 0x0f172a,
      0x020617)

  /** The Tailwind `gray` ramp, lightest (`c50`) to darkest (`c950`). */
  val Gray: Shades =
    shadesOf(0xf9fafb, 0xf3f4f6, 0xe5e7eb, 0xd1d5db, 0x9ca3af, 0x6b7280, 0x4b5563, 0x374151, 0x1f2937, 0x111827,
      0x030712)

  /** The Tailwind `zinc` ramp, lightest (`c50`) to darkest (`c950`). */
  val Zinc: Shades =
    shadesOf(0xfafafa, 0xf4f4f5, 0xe4e4e7, 0xd4d4d8, 0xa1a1aa, 0x71717a, 0x52525b, 0x3f3f46, 0x27272a, 0x18181b,
      0x09090b)

  /** The Tailwind `neutral` ramp, lightest (`c50`) to darkest (`c950`). */
  val Neutral: Shades =
    shadesOf(0xfafafa, 0xf5f5f5, 0xe5e5e5, 0xd4d4d4, 0xa3a3a3, 0x737373, 0x525252, 0x404040, 0x262626, 0x171717,
      0x0a0a0a)

  /** The Tailwind `stone` ramp, lightest (`c50`) to darkest (`c950`). */
  val Stone: Shades =
    shadesOf(0xfafaf9, 0xf5f5f4, 0xe7e5e4, 0xd6d3d1, 0xa8a29e, 0x78716c, 0x57534e, 0x44403c, 0x292524, 0x1c1917,
      0x0c0a09)

  /** The Tailwind `red` ramp, lightest (`c50`) to darkest (`c950`). */
  val Red: Shades =
    shadesOf(0xfef2f2, 0xfee2e2, 0xfecaca, 0xfca5a5, 0xf87171, 0xef4444, 0xdc2626, 0xb91c1c, 0x991b1b, 0x7f1d1d,
      0x450a0a)

  /** The Tailwind `orange` ramp, lightest (`c50`) to darkest (`c950`). */
  val Orange: Shades =
    shadesOf(0xfff7ed, 0xffedd5, 0xfed7aa, 0xfdba74, 0xfb923c, 0xf97316, 0xea580c, 0xc2410c, 0x9a3412, 0x7c2d12,
      0x431407)

  /** The Tailwind `amber` ramp, lightest (`c50`) to darkest (`c950`). */
  val Amber: Shades =
    shadesOf(0xfffbeb, 0xfef3c7, 0xfde68a, 0xfcd34d, 0xfbbf24, 0xf59e0b, 0xd97706, 0xb45309, 0x92400e, 0x78350f,
      0x451a03)

  /** The Tailwind `yellow` ramp, lightest (`c50`) to darkest (`c950`). */
  val Yellow: Shades =
    shadesOf(0xfefce8, 0xfef9c3, 0xfef08a, 0xfde047, 0xfacc15, 0xeab308, 0xca8a04, 0xa16207, 0x854d0e, 0x713f12,
      0x422006)

  /** The Tailwind `lime` ramp, lightest (`c50`) to darkest (`c950`). */
  val Lime: Shades =
    shadesOf(0xf7fee7, 0xecfccb, 0xd9f99d, 0xbef264, 0xa3e635, 0x84cc16, 0x65a30d, 0x4d7c0f, 0x3f6212, 0x365314,
      0x1a2e05)

  /** The Tailwind `green` ramp, lightest (`c50`) to darkest (`c950`). */
  val Green: Shades =
    shadesOf(0xf0fdf4, 0xdcfce7, 0xbbf7d0, 0x86efac, 0x4ade80, 0x22c55e, 0x16a34a, 0x15803d, 0x166534, 0x14532d,
      0x052e16)

  /** The Tailwind `emerald` ramp, lightest (`c50`) to darkest (`c950`). */
  val Emerald: Shades =
    shadesOf(0xecfdf5, 0xd1fae5, 0xa7f3d0, 0x6ee7b7, 0x34d399, 0x10b981, 0x059669, 0x047857, 0x065f46, 0x064e3b,
      0x022c22)

  /** The Tailwind `teal` ramp, lightest (`c50`) to darkest (`c950`). */
  val Teal: Shades =
    shadesOf(0xf0fdfa, 0xccfbf1, 0x99f6e4, 0x5eead4, 0x2dd4bf, 0x14b8a6, 0x0d9488, 0x0f766e, 0x115e59, 0x134e4a,
      0x042f2e)

  /** The Tailwind `cyan` ramp, lightest (`c50`) to darkest (`c950`). */
  val Cyan: Shades =
    shadesOf(0xecfeff, 0xcffafe, 0xa5f3fc, 0x67e8f9, 0x22d3ee, 0x06b6d4, 0x0891b2, 0x0e7490, 0x155e75, 0x164e63,
      0x083344)

  /** The Tailwind `sky` ramp, lightest (`c50`) to darkest (`c950`). */
  val Sky: Shades =
    shadesOf(0xf0f9ff, 0xe0f2fe, 0xbae6fd, 0x7dd3fc, 0x38bdf8, 0x0ea5e9, 0x0284c7, 0x0369a1, 0x075985, 0x0c4a6e,
      0x082f49)

  /** The Tailwind `blue` ramp, lightest (`c50`) to darkest (`c950`). */
  val Blue: Shades =
    shadesOf(0xeff6ff, 0xdbeafe, 0xbfdbfe, 0x93c5fd, 0x60a5fa, 0x3b82f6, 0x2563eb, 0x1d4ed8, 0x1e40af, 0x1e3a8a,
      0x172554)

  /** The Tailwind `indigo` ramp, lightest (`c50`) to darkest (`c950`). */
  val Indigo: Shades =
    shadesOf(0xeef2ff, 0xe0e7ff, 0xc7d2fe, 0xa5b4fc, 0x818cf8, 0x6366f1, 0x4f46e5, 0x4338ca, 0x3730a3, 0x312e81,
      0x1e1b4b)

  /** The Tailwind `violet` ramp, lightest (`c50`) to darkest (`c950`). */
  val Violet: Shades =
    shadesOf(0xf5f3ff, 0xede9fe, 0xddd6fe, 0xc4b5fd, 0xa78bfa, 0x8b5cf6, 0x7c3aed, 0x6d28d9, 0x5b21b6, 0x4c1d95,
      0x2e1065)

  /** The Tailwind `purple` ramp, lightest (`c50`) to darkest (`c950`). */
  val Purple: Shades =
    shadesOf(0xfaf5ff, 0xf3e8ff, 0xe9d5ff, 0xd8b4fe, 0xc084fc, 0xa855f7, 0x9333ea, 0x7e22ce, 0x6b21a8, 0x581c87,
      0x3b0764)

  /** The Tailwind `fuchsia` ramp, lightest (`c50`) to darkest (`c950`). */
  val Fuchsia: Shades =
    shadesOf(0xfdf4ff, 0xfae8ff, 0xf5d0fe, 0xf0abfc, 0xe879f9, 0xd946ef, 0xc026d3, 0xa21caf, 0x86198f, 0x701a75,
      0x4a044e)

  /** The Tailwind `pink` ramp, lightest (`c50`) to darkest (`c950`). */
  val Pink: Shades =
    shadesOf(0xfdf2f8, 0xfce7f3, 0xfbcfe8, 0xf9a8d4, 0xf472b6, 0xec4899, 0xdb2777, 0xbe185d, 0x9d174d, 0x831843,
      0x500724)

  /** The Tailwind `rose` ramp, lightest (`c50`) to darkest (`c950`). */
  val Rose: Shades =
    shadesOf(0xfff1f2, 0xffe4e6, 0xfecdd3, 0xfda4af, 0xfb7185, 0xf43f5e, 0xe11d48, 0xbe123c, 0x9f1239, 0x881337,
      0x4c0519)

  /** Every ramp paired with its Tailwind name, in the order the palette is published. Lets an application offer the
    * palette as a choice — a theme picker, a "colour by hue name" configuration key — without repeating the list.
    */
  val Ramps: Seq[(String, Shades)] = Seq(
    "slate"   -> Slate,
    "gray"    -> Gray,
    "zinc"    -> Zinc,
    "neutral" -> Neutral,
    "stone"   -> Stone,
    "red"     -> Red,
    "orange"  -> Orange,
    "amber"   -> Amber,
    "yellow"  -> Yellow,
    "lime"    -> Lime,
    "green"   -> Green,
    "emerald" -> Emerald,
    "teal"    -> Teal,
    "cyan"    -> Cyan,
    "sky"     -> Sky,
    "blue"    -> Blue,
    "indigo"  -> Indigo,
    "violet"  -> Violet,
    "purple"  -> Purple,
    "fuchsia" -> Fuchsia,
    "pink"    -> Pink,
    "rose"    -> Rose,
  )

  /** The ramp published under `name` (lower-case, as Tailwind spells it), or `None` when no ramp has that name. */
  def ramp(name: String): Option[Shades] = Ramps.collectFirst { case (n, shades) if n == name => shades }

  /** Builds one ramp from eleven packed `0xrrggbb` integers, in step order.
    *
    * A packed literal per shade keeps each ramp on a single line, where the eleven values can be read as the gradient
    * they are; three separate channel arguments per shade would run to eleven lines for every one of the 22 ramps.
    */
  private def shadesOf(
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
      c950: Int,
  ): Shades =
    Shades(
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
      fromPacked(c950),
    )

  /** Unpacks one `0xrrggbb` integer into a [[Color.Rgb]]. */
  private def fromPacked(packed: Int): Color =
    Color.Rgb((packed >> 16) & 0xff, (packed >> 8) & 0xff, packed & 0xff)
