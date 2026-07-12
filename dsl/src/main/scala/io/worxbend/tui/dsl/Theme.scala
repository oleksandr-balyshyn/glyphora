package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Color, Style}

/** Semantic styles the chrome presets (and applications) draw from, provided ambiently via `given Theme`.
  *
  * The default is [[Theme.Dark]]; an application overrides it by defining its own given (or by rendering from a
  * `Signal[Theme]` for runtime switching — the view re-evaluates, presets pick up the new value).
  */
final case class Theme(
    name: String,
    primary: Style,
    accent: Style,
    muted: Style,
    error: Style,
    warning: Style,
    success: Style,
    surface: Style,
    border: Style,
    focus: Style,
)

object Theme:

  val Dark: Theme = Theme(
    name = "dark",
    primary = Style.Default,
    accent = Style.Default.withFg(Color.Cyan),
    muted = Style.Default.dim,
    error = Style.Default.withFg(Color.Red),
    warning = Style.Default.withFg(Color.Yellow),
    success = Style.Default.withFg(Color.Green),
    surface = Style.Default.withBg(Color.Indexed(236)).withFg(Color.White),
    border = Style.Default.withFg(Color.Indexed(244)),
    focus = Style.Default.reverse,
  )

  val Light: Theme = Theme(
    name = "light",
    primary = Style.Default.withFg(Color.Black),
    accent = Style.Default.withFg(Color.Blue),
    muted = Style.Default.withFg(Color.Indexed(245)),
    error = Style.Default.withFg(Color.Red),
    warning = Style.Default.withFg(Color.Indexed(130)),
    success = Style.Default.withFg(Color.Green),
    surface = Style.Default.withBg(Color.Indexed(253)).withFg(Color.Black),
    border = Style.Default.withFg(Color.Indexed(248)),
    focus = Style.Default.reverse,
  )

  val HighContrast: Theme = Theme(
    name = "high-contrast",
    primary = Style.Default.withFg(Color.White).bold,
    accent = Style.Default.withFg(Color.Yellow).bold,
    muted = Style.Default.withFg(Color.White),
    error = Style.Default.withFg(Color.Red).bold,
    warning = Style.Default.withFg(Color.Yellow).bold,
    success = Style.Default.withFg(Color.Green).bold,
    surface = Style.Default.withBg(Color.Black).withFg(Color.White).bold,
    border = Style.Default.withFg(Color.White),
    focus = Style.Default.reverse.bold,
  )

  /** The ambient default; shadow it with a local given (or an app-level one) to re-theme. */
  given default: Theme = Dark
