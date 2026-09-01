package io.worxbend.tui.widgets

/** The one-column glyphs a sub-cell surface draws a single dot with at [[CanvasResolution.Cell]].
  *
  * [[Canvas]], [[Chart]], [[OrbitSpinner]] and [[LinearSpinner]] all take a `marker`, and each of them wrote its own
  * default down as a bare string literal. Two things went wrong with that. An application choosing its own marker had
  * no way to *name* the default it was replacing — the library's own bullet was a private value it could not reach —
  * and the same glyph appearing in four unrelated files is four places to keep in step.
  *
  * These are plain `String`s rather than an enum on purpose: a marker may be any glyph exactly one column wide, and the
  * surfaces accept whatever the application picks (anything wider is refused and drawn as [[Dot]] instead, so a
  * two-column emoji cannot smear into the cell next door). Naming them is about being able to say which one you mean,
  * not about restricting the choice.
  */
object Marker:

  /** The default single dot, `"•"`. Also what stands in for a marker that is not exactly one column wide. */
  val Dot: String = "•"

  /** A filled circle, `"●"` — the spinner default. The same one column as [[Dot]], but heavier on screen. */
  val Circle: String = "●"

  /** A full block, `"█"`, for a solid trail rather than a dotted one. */
  val Block: String = "█"

  /** `"*"`, the ASCII floor for a terminal whose font has none of the above. */
  val Ascii: String = "*"
