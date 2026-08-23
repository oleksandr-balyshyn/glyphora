package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Color, Constraint, Style, Widget}
import io.worxbend.tui.widgets as w

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/** A spinner whose colors were resolved from the ambient [[Theme]] at construction.
  *
  * The theme supplies the glyph and label styles; anything the call site sets with `.color`/`.bold`/`.style` layers on
  * top of the glyph style, so `spinner(tick).fg(Color.Red)` recolors the moving part and leaves the label alone.
  */
final case class SpinnerElement(
    elapsed: FiniteDuration,
    label: String,
    preset: w.SpinnerPreset,
    glyphStyle: Style,
    labelStyle: Style,
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = SpinnerElement
  def widget: Widget = w.Spinner(elapsed, label, preset, glyphStyle.patch(props.style), Some(labelStyle))

  /** Swaps the animation — see [[io.worxbend.tui.widgets.SpinnerPreset]] for the catalogue. */
  def preset(chosen: w.SpinnerPreset): SpinnerElement = copy(preset = chosen)

  /** Sets the caption shown after the glyph. */
  def label(text: String): SpinnerElement = copy(label = text)

  /** Styles the caption independently of the glyph. */
  def labelStyle(style: Style): SpinnerElement = copy(labelStyle = style)

  /** Runs the animation `factor` times slower than the preset's own speed. */
  def slowedBy(factor: Double): SpinnerElement = copy(preset = preset.slowedBy(factor))

  /** Runs the animation at an explicit frame rate. */
  def atFps(fps: Double): SpinnerElement = copy(preset = preset.atFps(fps))

  private[dsl] def withProps(props: ElementProps): SpinnerElement = copy(props = props)
  private[dsl] override def claim: SizeClaim                      = SizeClaim.OneRow

/** A one-row determinate progress bar, themed at construction. */
final case class ProgressBarElement(
    ratio: Double,
    label: w.ProgressLabel,
    bar: w.ProgressStyle,
    trackStyle: Style,
    fillStyle: Style,
    ramp: Option[w.ColorRamp],
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = ProgressBarElement
  def widget: Widget =
    w.LineGauge(ratio, label, trackStyle.patch(props.style), fillStyle.patch(props.style), bar, ramp)

  /** Swaps the glyph vocabulary — see [[io.worxbend.tui.widgets.ProgressStyle]] for the catalogue. */
  def preset(chosen: w.ProgressStyle): ProgressBarElement = copy(bar = chosen)

  /** Replaces the percentage caption with fixed text. */
  def label(text: String): ProgressBarElement = copy(label = w.ProgressLabel.Text(text))

  /** Shows fixed text followed by the percentage, as in `syncing 42%`. */
  def labelled(text: String): ProgressBarElement = copy(label = w.ProgressLabel.TextAndPercentage(text))

  /** Drops the caption entirely, leaving the bar the full width. */
  def bare: ProgressBarElement = copy(label = w.ProgressLabel.Hidden)

  /** Colors the fill by how far along it is — see [[io.worxbend.tui.widgets.ColorRamp]] for the built-in ramps. */
  def ramp(chosen: w.ColorRamp): ProgressBarElement = copy(ramp = Some(chosen))

  /** Colors the fill along a two-stop ramp. */
  def ramp(from: Color, to: Color): ProgressBarElement = copy(ramp = Some(w.ColorRamp(from, to)))

  private[dsl] def withProps(props: ElementProps): ProgressBarElement = copy(props = props)
  private[dsl] override def claim: SizeClaim                          = SizeClaim.OneRow

/** A one-row indeterminate progress bar, themed at construction. */
final case class IndeterminateElement(
    elapsed: FiniteDuration,
    motion: w.IndeterminateMotion,
    bar: w.ProgressStyle,
    trackStyle: Style,
    fillStyle: Style,
    period: FiniteDuration = 1600.millis,
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = IndeterminateElement
  def widget: Widget =
    w.IndeterminateBar(
      elapsed,
      trackStyle.patch(props.style),
      fillStyle.patch(props.style),
      bar,
      motion,
      period = period,
    )

  /** Sets how long one full traverse takes. */
  def period(duration: FiniteDuration): IndeterminateElement = copy(period = duration)

  /** Swaps how the segment travels — bounce, sweep, comet, or pulse. */
  def motion(chosen: w.IndeterminateMotion): IndeterminateElement = copy(motion = chosen)

  /** Swaps the glyph vocabulary. */
  def preset(chosen: w.ProgressStyle): IndeterminateElement = copy(bar = chosen)

  private[dsl] def withProps(props: ElementProps): IndeterminateElement = copy(props = props)
  private[dsl] override def claim: SizeClaim                            = SizeClaim.OneRow

/** Text carrying a time-based effect, themed at construction. */
final case class AnimatedTextElement(
    content: String,
    elapsed: FiniteDuration,
    effect: w.TextEffect,
    baseStyle: Style,
    highlightStyle: Style,
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = AnimatedTextElement
  def widget: Widget = w.AnimatedText(content, elapsed, effect, baseStyle.patch(props.style), highlightStyle)

  /** Swaps the effect — see [[io.worxbend.tui.widgets.TextEffect]] for the catalogue. */
  def effect(chosen: w.TextEffect): AnimatedTextElement = copy(effect = chosen)

  /** Styles the emphasised part — the crest, the cursor, the shimmer head — independently of the resting text. */
  def highlight(style: Style): AnimatedTextElement = copy(highlightStyle = style)

  private[dsl] def withProps(props: ElementProps): AnimatedTextElement = copy(props = props)

/** An orbit spinner whose colors were resolved from the ambient [[Theme]] at construction.
  *
  * The [[LoadingTheme]] supplies both: the resting path takes `track`, the arc takes `spinner`. Anything the call site
  * sets with `.color`/`.bold`/`.style` layers onto the arc only, so `orbitSpinner().fg(Color.Red)` recolors the moving
  * part and leaves the path themed — the rule [[SpinnerElement]] follows for its glyph and label.
  */
final case class OrbitSpinnerElement(
    elapsed: FiniteDuration,
    pathStyle: Style,
    arcStyle: Style,
    orbit: w.OrbitPath = w.OrbitPath.Circle,
    trail: w.OrbitTrail = w.OrbitTrail.Comet(),
    sweep: Double = 0.25,
    radius: Option[Int] = None,
    thickness: Int = 1,
    resolution: w.CanvasResolution = w.CanvasResolution.Braille,
    marker: String = "●",
    direction: w.SpinDirection = w.SpinDirection.Clockwise,
    period: FiniteDuration = 1600.millis,
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = OrbitSpinnerElement

  def widget: Widget =
    w.OrbitSpinner(
      elapsed,
      pathStyle,
      arcStyle.patch(props.style),
      orbit,
      trail,
      sweep,
      radius,
      thickness,
      resolution,
      marker,
      direction,
      period,
    )

  /** Swaps the loop the arc travels — a circle or a square. */
  def path(chosen: w.OrbitPath): OrbitSpinnerElement = copy(orbit = chosen)

  /** Fixes the figure's radius in dots; unset, it fills whatever area it is given. */
  def radius(dots: Int): OrbitSpinnerElement = copy(radius = Some(dots))

  /** Sets how much of the lap is lit, as a fraction — `.sweep(1.0)` with a solid trail lights the whole path and stops
    * the motion, which is the family's static "queued" state.
    */
  def sweep(fraction: Double): OrbitSpinnerElement = copy(sweep = fraction)

  /** Thickens the arc and the path inwards, in dots — worth it above about radius 8, where one dot reads as faint. */
  def thickness(dots: Int): OrbitSpinnerElement = copy(thickness = dots)

  /** Swaps how the arc is shaded — see [[io.worxbend.tui.widgets.OrbitTrail]]. */
  def trail(chosen: w.OrbitTrail): OrbitSpinnerElement = copy(trail = chosen)

  /** A uniform bright window instead of a fading tail — the colourless-terminal choice, and the legible one on a small
    * figure.
    */
  def solid: OrbitSpinnerElement = copy(trail = w.OrbitTrail.Solid)

  /** Colors the comet's decay along a ramp — the only way to get more than two steps of fade out of one style per cell,
    * and even then only about one step per cell the arc crosses.
    */
  def ramp(chosen: w.ColorRamp): OrbitSpinnerElement = copy(trail = w.OrbitTrail.Comet(Some(chosen)))

  /** Runs the arc the other way round. */
  def reversed: OrbitSpinnerElement = copy(direction = w.SpinDirection.CounterClockwise)

  /** Sets how long one full revolution takes. */
  def period(duration: FiniteDuration): OrbitSpinnerElement = copy(period = duration)

  /** Draws the figure from one `glyph` per cell instead of sub-cell dots — for a terminal with no braille block, at a
    * quarter of the vertical resolution.
    */
  def markers(glyph: String): OrbitSpinnerElement = copy(resolution = w.CanvasResolution.Cell, marker = glyph)

  /** Half-block dots: twice the vertical resolution of `.markers`, and no braille font needed. */
  def halfBlocks: OrbitSpinnerElement = copy(resolution = w.CanvasResolution.HalfBlock)

  private[dsl] def withProps(props: ElementProps): OrbitSpinnerElement = copy(props = props)

  /** A fixed radius claims its exact box; a fitted one claims the space it fills. Deriving both axes from the widget's
    * own `preferredSize` is what keeps the measurement from drifting away from what is painted.
    */
  private[dsl] override def claim: SizeClaim =
    w.OrbitSpinner(elapsed, radius = radius, resolution = resolution)
      .preferredSize
      .fold(SizeClaim.Fill)(size => SizeClaim.box(size.width, size.height))

/** A head travelling a one-cell track, themed at construction. */
final case class LinearSpinnerElement(
    elapsed: FiniteDuration,
    railStyle: Style,
    headStyle: Style,
    axis: w.LinearAxis = w.LinearAxis.Horizontal,
    path: w.LinearPath = w.LinearPath.Wrap,
    flow: w.LinearFlow = w.LinearFlow.Forward,
    trail: w.LinearTrail = w.LinearTrail.Comet,
    period: FiniteDuration = 1200.millis,
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = LinearSpinnerElement

  def widget: Widget =
    w.LinearSpinner(elapsed, railStyle, headStyle.patch(props.style), axis, path, flow, trail, period = period)

  /** Runs the track down a column instead of along a row. */
  def vertical: LinearSpinnerElement = copy(axis = w.LinearAxis.Vertical)

  /** The head turns back at each end rather than wrapping round. */
  def bouncing: LinearSpinnerElement = copy(path = w.LinearPath.Bounce)

  /** Runs the head the other way. */
  def reversed: LinearSpinnerElement = copy(flow = w.LinearFlow.Backward)

  /** A uniform lit window instead of a fading tail. */
  def solid: LinearSpinnerElement = copy(trail = w.LinearTrail.Solid)

  /** Sets how long one full cycle takes — a traverse when wrapping, a round trip when bouncing. */
  def period(duration: FiniteDuration): LinearSpinnerElement = copy(period = duration)

  private[dsl] def withProps(props: ElementProps): LinearSpinnerElement = copy(props = props)
  private[dsl] override def claim: SizeClaim                            =
    axis match
      case w.LinearAxis.Horizontal => SizeClaim.OneRow
      case w.LinearAxis.Vertical   => SizeClaim(Constraint.Fill(1), Constraint.Length(1))

/** A themed block of phase-offset spinners. */
final case class SpinnerGridElement(
    elapsed: FiniteDuration,
    preset: w.SpinnerPreset,
    phase: w.GridPhase,
    glyphStyle: Style,
    ramp: Option[w.ColorRamp] = None,
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = SpinnerGridElement

  def widget: Widget = w.SpinnerGrid(elapsed, preset, phase, glyphStyle.patch(props.style), ramp)

  /** Swaps the animation each slot runs — see [[io.worxbend.tui.widgets.SpinnerPreset]] for the catalogue. */
  def preset(chosen: w.SpinnerPreset): SpinnerGridElement = copy(preset = chosen)

  /** Swaps how neighbouring slots are offset — lockstep, diagonal, or radial. */
  def phase(chosen: w.GridPhase): SpinnerGridElement = copy(phase = chosen)

  /** Every slot in lockstep: the reduced-motion member of this family. */
  def uniform: SpinnerGridElement = copy(phase = w.GridPhase.Uniform)

  /** Colors the block by phase — free here, because every slot holds exactly one frame. */
  def ramp(chosen: w.ColorRamp): SpinnerGridElement = copy(ramp = Some(chosen))

  /** Runs the animation `factor` times slower than the preset's own speed. */
  def slowedBy(factor: Double): SpinnerGridElement = copy(preset = preset.slowedBy(factor))

  /** Runs the animation at an explicit frame rate. */
  def atFps(fps: Double): SpinnerGridElement = copy(preset = preset.atFps(fps))

  private[dsl] def withProps(props: ElementProps): SpinnerGridElement = copy(props = props)

/** A skeleton placeholder, themed at construction. */
final case class SkeletonElement(
    elapsed: FiniteDuration,
    baseStyle: Style,
    bandStyle: Style,
    bandWidth: Option[Int] = None,
    period: FiniteDuration = 1200.millis,
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = SkeletonElement
  def widget: Widget =
    w.Skeleton(elapsed, baseStyle.patch(props.style), bandStyle, bandWidth = bandWidth, period = period)

  /** Pins the sweeping band's width, so skeletons of different sizes pulse in step. */
  def band(cells: Int): SkeletonElement = copy(bandWidth = Some(cells))

  /** Sets how long one full sweep takes. */
  def period(duration: FiniteDuration): SkeletonElement = copy(period = duration)

  private[dsl] def withProps(props: ElementProps): SkeletonElement = copy(props = props)
