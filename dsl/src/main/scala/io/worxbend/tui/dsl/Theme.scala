package io.worxbend.tui.dsl

import io.worxbend.tui.core.Style
import io.worxbend.tui.core.Color
import io.worxbend.tui.widgets.ColorRamp

/** The styles the loading and progress animations draw from.
  *
  * Split out of [[Theme]] rather than flattened into it because these five travel together: retheming a progress bar
  * means changing `track` and `fill` consistently, and a theme that got one of them wrong would be hard to spot. It
  * also keeps `Theme`'s own vocabulary semantic (`accent`, `muted`) rather than widget-specific.
  *
  * `fillRamp` is optional because a color ramp is a deliberate choice, not a default: a bar that walks red-to-green
  * reads as "danger receding", which is right for disk usage and wrong for a download. Themes leave it unset and a call
  * site opts in with `.ramp(...)`.
  */
final case class LoadingTheme(
    spinner: Style,
    label: Style,
    track: Style,
    fill: Style,
    band: Style,
    fillRamp: Option[ColorRamp] = None,
)

object LoadingTheme:

  /** Derives a loading palette from a theme's own semantic styles, so a custom [[Theme]] gets a coherent one for free:
    * the moving parts take the accent color, the track and label recede into the muted style.
    */
  def from(accent: Style, muted: Style, surface: Style): LoadingTheme =
    LoadingTheme(spinner = accent, label = muted, track = muted, fill = accent, band = surface)

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
    loading: LoadingTheme,
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
    loading = LoadingTheme(
      spinner = Style.Default.withFg(Color.Cyan),
      label = Style.Default.dim,
      track = Style.Default.withFg(Color.Indexed(238)),
      fill = Style.Default.withFg(Color.Cyan),
      band = Style.Default.withFg(Color.Indexed(245)),
    ),
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
    loading = LoadingTheme(
      spinner = Style.Default.withFg(Color.Blue),
      label = Style.Default.withFg(Color.Indexed(245)),
      track = Style.Default.withFg(Color.Indexed(252)),
      fill = Style.Default.withFg(Color.Blue),
      band = Style.Default.withFg(Color.Indexed(248)),
    ),
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
    loading = LoadingTheme(
      spinner = Style.Default.withFg(Color.Yellow).bold,
      label = Style.Default.withFg(Color.White),
      track = Style.Default.withFg(Color.White),
      fill = Style.Default.withFg(Color.Yellow).bold,
      band = Style.Default.withFg(Color.White).bold,
    ),
  )

  /** The ambient default; shadow it with a local given (or an app-level one) to re-theme. */
  given default: Theme = Dark
