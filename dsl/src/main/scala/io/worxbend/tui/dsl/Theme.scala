package io.worxbend.tui.dsl

import io.worxbend.tui.core.Style
import io.worxbend.tui.core.Color
import io.worxbend.tui.widgets.{ColorRamp, MarkdownTheme, SyntaxTheme}

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

/** Semantic styles the chrome presets, the themed element factories, and applications draw from.
  *
  * A `TuiApp` hands its own [[TuiApp.theme]] to `view` as a `using` parameter, so everything a view calls sees it
  * without any `given` ceremony. Outside a view — a widget-level test, an element built in a helper object — the
  * ambient [[Theme.default]] applies.
  *
  * The five sub-palettes are grouped rather than flattened because they retheme as units: `loading` for the spinner and
  * progress family, `markdown` for a rendered document, and `markdown.syntax` (mirrored as [[syntax]] for the
  * standalone highlighter) for code.
  *
  * @param border
  *   the frame style `panel` and `rule` draw with when the caller sets no style of their own.
  * @param focus
  *   the cue the focus pass paints on the focused element, and the selection highlight of the scrollable widgets.
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
    markdown: MarkdownTheme,
    syntax: SyntaxTheme,
)

object Theme:

  // The two shared code palettes are declared before the themes that name them: `object` members initialise top to
  // bottom, so a `val Light` that read a `LightSyntax` declared below it would capture `null`.

  /** Shared by `Light`'s Markdown fences and its standalone highlighter, so the two cannot drift apart. */
  private val LightSyntax: SyntaxTheme = SyntaxTheme(
    keyword = Style.Default.withFg(Color.Indexed(90)),
    string = Style.Default.withFg(Color.Indexed(28)),
    number = Style.Default.withFg(Color.Indexed(130)),
    comment = Style.Default.italic.withFg(Color.Indexed(245)),
    function = Style.Default.withFg(Color.Indexed(25)),
    variable = Style.Default.withFg(Color.Indexed(30)),
    default = Style.Default.withFg(Color.Black),
  )

  /** Shared by `HighContrast`'s Markdown fences and its standalone highlighter. */
  private val HighContrastSyntax: SyntaxTheme = SyntaxTheme(
    keyword = Style.Default.withFg(Color.Yellow).bold,
    string = Style.Default.withFg(Color.White).bold,
    number = Style.Default.withFg(Color.Yellow).bold,
    comment = Style.Default.withFg(Color.White).italic,
    function = Style.Default.withFg(Color.White).bold,
    variable = Style.Default.withFg(Color.White),
    default = Style.Default.withFg(Color.White),
  )

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
    // the widget-level defaults are already tuned for a dark terminal, so `Dark` is the one theme that can take them
    markdown = MarkdownTheme(),
    syntax = SyntaxTheme(),
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
    // Cyan and Yellow — the widget defaults — are close to invisible on a white background, so the light document
    // palette moves to blue headings and a dark ochre for inline code
    markdown = MarkdownTheme(
      heading1 = Style.Default.bold.withFg(Color.Blue),
      heading2 = Style.Default.bold.withFg(Color.Black),
      heading3 = Style.Default.underline.withFg(Color.Black),
      strong = Style.Default.bold.withFg(Color.Black),
      emphasis = Style.Default.italic.withFg(Color.Black),
      code = Style.Default.withFg(Color.Indexed(130)),
      quote = Style.Default.italic.withFg(Color.Indexed(245)),
      bullet = Style.Default.withFg(Color.Blue),
      link = Style.Default.withFg(Color.Blue).underline,
      syntax = LightSyntax,
    ),
    syntax = LightSyntax,
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
    // no hue carries meaning here: weight and underline do the work, so the palette stays legible on a monochrome or
    // heavily colour-filtered terminal
    markdown = MarkdownTheme(
      heading1 = Style.Default.withFg(Color.Yellow).bold,
      heading2 = Style.Default.withFg(Color.White).bold,
      heading3 = Style.Default.withFg(Color.White).underline,
      strong = Style.Default.withFg(Color.White).bold,
      emphasis = Style.Default.withFg(Color.White).italic,
      code = Style.Default.withFg(Color.Yellow).bold,
      quote = Style.Default.withFg(Color.White).italic,
      bullet = Style.Default.withFg(Color.Yellow).bold,
      link = Style.Default.withFg(Color.White).underline.bold,
      syntax = HighContrastSyntax,
    ),
    syntax = HighContrastSyntax,
  )

  /** The ambient default, for element construction that happens outside a `view`: a widget-level test, a helper object
    * that builds a panel once, a REPL session.
    *
    * It is deliberately *not* how a running application gets its theme. `TuiApp` passes [[TuiApp.theme]] into `view` as
    * a `using` parameter (see [[View]]), so inside a view the app's own theme always wins over this one — which is the
    * bug this arrangement fixes: a `given` installed by the framework *around* the call to `view` is not in scope
    * inside the view's body, so an app that overrode `theme` and then called `statusBar(bindings)` used to fall back
    * here without saying so.
    */
  given default: Theme = Dark
