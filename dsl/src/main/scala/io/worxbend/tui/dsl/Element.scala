package io.worxbend.tui.dsl

import io.worxbend.tui.core.{
  Cell,
  CharWidth,
  Constraint,
  Direction,
  Flex,
  KeyCode,
  KeyEvent,
  KeyModifiers,
  Line,
  MouseEvent,
  MouseEventKind,
  Rect,
  Size,
  Style,
  Text,
  Widget,
}
import io.worxbend.tui.runtime.{ReactiveScope, Signal}

import java.time.LocalTime
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import io.worxbend.tui.widgets as w

/** Framework mouse behavior: the event plus the element's rendered area, `true` when the event is consumed. */
private[dsl] type BuiltinMouseHandler = (MouseEvent, Rect) => Boolean

/** A node of the retained-mode UI tree.
  *
  * Every element ultimately renders through a `tui-core` [[Widget]] — the DSL is a declarative layer over
  * `tui-widgets`, never a parallel rendering path. The sealed hierarchy is plain data: construction tests can
  * pattern-match it, and styling extensions rebuild nodes instead of mutating them.
  */
sealed trait Element:
  def props: ElementProps
  def style: Style           = props.style
  def widget: Widget
  def children: Seq[Element] = Seq.empty
  private[dsl] def withProps(props: ElementProps): Element

  /** Containers rebuild with transformed children during the focus pass; leaves ignore it. */
  private[dsl] def withChildren(children: Seq[Element]): Element = this

  /** Framework behavior an interactive element performs when focused (editing, toggling, cycling) — runs after the
    * user's own `onKeyEvent` handler declined the event.
    */
  private[dsl] def builtinKeyHandler: Option[KeyEvent => Boolean] = None

  /** Framework mouse behavior (click-to-activate, wheel scrolling, drags), given the event and the element's rendered
    * area. Runs after the user's `onMouseEvent` declined; only ever called on the hit element.
    */
  private[dsl] def builtinMouseHandler: Option[BuiltinMouseHandler] = None

  /** Framework paste behavior: how a bracketed paste lands in this element while focused. */
  private[dsl] def builtinPasteHandler: Option[String => Boolean] = None

  /** The space this element claims along `direction` inside a container when the user set nothing explicit — one-row
    * widgets claim one row vertically but their natural width (or a fill) horizontally.
    */
  private[dsl] def preferredSize(direction: Direction): Constraint =
    val _ = direction
    Constraint.Fill(1)

  /** The rows this element needs at `width`, when knowable — the measurement pass scroll views and auto-sizing
    * containers use. Defaults to a fixed vertical preferred size; content-driven elements override it.
    */
  private[dsl] def intrinsicHeight(width: Int): Option[Int] =
    val _ = width
    props.constraint.getOrElse(preferredSize(Direction.Vertical)) match
      case Constraint.Length(cells) => Some(cells)
      case _                        => None

  private[dsl] final def layoutItem(direction: Direction): w.LayoutItem =
    w.LayoutItem(props.constraint.getOrElse(preferredSize(direction)), widget)

// ---- the shared built-in vocabulary ----
//
// The small combinators every element below reuses for its layout claim, its focus styling, and the framework key and
// mouse behavior it performs while focused. They own no state of their own — each closes over state the *element*
// (and therefore the app) owns — and they are called only from `builtinKeyHandler`/`builtinMouseHandler`, so they run
// on the render thread like every other part of a frame.

/** How far PageUp/PageDown move a scrollable, in rows. One place to change the page step for every scrollable. */
private val PageStep = 10

/** What a one-line control claims from its container: exactly one row when stacked vertically, whatever width is going
  * when laid out horizontally.
  */
private def singleRow(direction: Direction): Constraint =
  direction match
    case Direction.Vertical   => Constraint.Length(1)
    case Direction.Horizontal => Constraint.Fill(1)

/** Focused interactive elements render with the focus style (the theme's, once the focus pass ran) layered over their
  * own, so the user can see where keystrokes go.
  */
private def focusStyled(props: ElementProps): Style =
  if props.focused then props.style.patch(props.focusStyle) else props.style

/** A mouse press activates the control (focus already moved on the press). */
private def clickActivates(activate: () => Unit): BuiltinMouseHandler =
  (event, _) =>
    if event.kind == MouseEventKind.Down then
      activate()
      true
    else false

/** Wheel events scroll by one step. */
private def wheelScrolls(up: () => Unit, down: () => Unit): BuiltinMouseHandler =
  (event, _) =>
    event.kind match
      case MouseEventKind.ScrollUp   =>
        up()
        true
      case MouseEventKind.ScrollDown =>
        down()
        true
      case _                         => false

/** Space/Enter activates a two-state control. Only reached while the element is focused — [[EventRouter]] gates every
  * built-in key handler on that.
  */
private def toggleOnActivate(activate: () => Unit): KeyEvent => Boolean =
  event =>
    event.code match
      case KeyCode.Char(' ') | KeyCode.Enter =>
        activate()
        true
      case _                                 => false

/** The scrolling key vocabulary shared by every viewport-shaped element: Up/Down move one row, PageUp/PageDown move
  * [[PageStep]] rows. `up`/`down` are handed the row count to move and do the scrolling on the caller-owned state;
  * anything else is left unconsumed so it keeps bubbling.
  */
private def scrollKeys(up: Int => Unit, down: Int => Unit): KeyEvent => Boolean =
  event =>
    event.code match
      case KeyCode.Up       =>
        up(1)
        true
      case KeyCode.Down     =>
        down(1)
        true
      case KeyCode.PageUp   =>
        up(PageStep)
        true
      case KeyCode.PageDown =>
        down(PageStep)
        true
      case _                => false

/** The caret and erase key vocabulary shared by every single-line text field: Backspace/Delete erase either side of the
  * caret, Left/Right/Home/End move it. `state` is the caller-owned editing state the keys mutate; anything else is left
  * unconsumed, which is what lets a field layer its own `Char` handling on top and fall through to this.
  */
private def cursorKeys(state: w.TextInputState): KeyEvent => Boolean =
  event =>
    event.code match
      case KeyCode.Backspace =>
        state.backspace()
        true
      case KeyCode.Delete    =>
        state.delete()
        true
      case KeyCode.Left      =>
        state.moveLeft()
        true
      case KeyCode.Right     =>
        state.moveRight()
        true
      case KeyCode.Home      =>
        state.moveHome()
        true
      case KeyCode.End       =>
        state.moveEnd()
        true
      case _                 => false

/** Left/Right step `index` through `size` positions, wrapping at both ends. `size` is by-name because it is derived
  * from the element's current contents, and nothing is consumed when there is nothing to step through — an empty option
  * list or a tab bar with no pages leaves the arrows free to bubble.
  */
private def stepsWrapping(size: => Int, index: Signal[Int]): KeyEvent => Boolean =
  event =>
    val count = size
    if count == 0 then false
    else
      event.code match
        case KeyCode.Left  =>
          index.update(current => (current - 1 + count) % count)
          true
        case KeyCode.Right =>
          index.update(current => (current + 1) % count)
          true
        case _             => false

final case class TextElement(content: String, props: ElementProps = ElementProps()) extends Element:
  def widget: Widget                                           = w.Paragraph(Text.styled(content, props.style))
  private[dsl] def withProps(props: ElementProps): TextElement = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    direction match
      case Direction.Vertical   => Constraint.Length(content.split("\n", -1).length)
      case Direction.Horizontal =>
        Constraint.Length(content.split("\n", -1).map(CharWidth.of).maxOption.getOrElse(0))

final case class PanelElement(
    title: Option[String],
    override val children: Seq[Element],
    borderType: w.BorderType = w.BorderType.Plain,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget                                                           =
    (area, buffer) =>
      val block = w.Block(title.map(text => Line.styled(text, props.style)), borderType, props.style)
      block.render(area, buffer)
      w.Column(children.map(_.layoutItem(Direction.Vertical))).render(block.inner(area), buffer)
  private[dsl] def withProps(props: ElementProps): PanelElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): PanelElement = copy(children = children)
  private[dsl] override def intrinsicHeight(width: Int): Option[Int]           =
    val heights = children.map(_.intrinsicHeight(math.max(0, width - 2)))
    if heights.forall(_.nonEmpty) then Some(heights.flatten.sum + 2) else None

final case class RowElement(
    override val children: Seq[Element],
    spacing: Int = 0,
    flex: Flex = Flex.Start,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget = w.Row(children.map(_.layoutItem(Direction.Horizontal)), spacing, flex)
  private[dsl] def withProps(props: ElementProps): RowElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): RowElement = copy(children = children)
  private[dsl] override def intrinsicHeight(width: Int): Option[Int]         =
    val heights = children.map(_.intrinsicHeight(width))
    if heights.forall(_.nonEmpty) then heights.flatten.maxOption else None

final case class ColumnElement(
    override val children: Seq[Element],
    spacing: Int = 0,
    flex: Flex = Flex.Start,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget = w.Column(children.map(_.layoutItem(Direction.Vertical)), spacing, flex)
  private[dsl] override def intrinsicHeight(width: Int): Option[Int]            =
    val heights = children.map(_.intrinsicHeight(width))
    if heights.forall(_.nonEmpty) then Some(heights.flatten.sum + spacing * math.max(0, children.size - 1))
    else None
  private[dsl] def withProps(props: ElementProps): ColumnElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): ColumnElement = copy(children = children)

final case class SpacerElement(props: ElementProps = ElementProps()) extends Element:
  def widget: Widget                                             = w.Spacer
  private[dsl] def withProps(props: ElementProps): SpacerElement = copy(props = props)

final case class GaugeElement(
    ratio: Double,
    label: Option[String] = None,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget = w.Gauge(ratio, label, props.style, filledStyle = props.style.reverse)
  private[dsl] def withProps(props: ElementProps): GaugeElement             = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint = singleRow(direction)

/** A spinner whose colors were resolved from the ambient [[Theme]] at construction.
  *
  * The theme supplies the glyph and label styles; anything the call site sets with `.color`/`.bold`/`.style` layers on
  * top of the glyph style, so `spinner(tick).color(Color.Red)` recolors the moving part and leaves the label alone.
  */
final case class SpinnerElement(
    elapsed: FiniteDuration,
    label: String,
    preset: w.SpinnerPreset,
    glyphStyle: Style,
    labelStyle: Style,
    props: ElementProps = ElementProps(),
) extends Element:
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

  private[dsl] def withProps(props: ElementProps): SpinnerElement           = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint = singleRow(direction)
  private[dsl] override def intrinsicHeight(width: Int): Option[Int]        = Some(1)

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

  private[dsl] def withProps(props: ElementProps): ProgressBarElement       = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint = singleRow(direction)
  private[dsl] override def intrinsicHeight(width: Int): Option[Int]        = Some(1)

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

  private[dsl] def withProps(props: ElementProps): IndeterminateElement     = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint = singleRow(direction)
  private[dsl] override def intrinsicHeight(width: Int): Option[Int]        = Some(1)

/** Text carrying a time-based effect, themed at construction. */
final case class AnimatedTextElement(
    content: String,
    elapsed: FiniteDuration,
    effect: w.TextEffect,
    baseStyle: Style,
    highlightStyle: Style,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget = w.AnimatedText(content, elapsed, effect, baseStyle.patch(props.style), highlightStyle)

  /** Swaps the effect — see [[io.worxbend.tui.widgets.TextEffect]] for the catalogue. */
  def effect(chosen: w.TextEffect): AnimatedTextElement = copy(effect = chosen)

  /** Styles the emphasised part — the crest, the cursor, the shimmer head — independently of the resting text. */
  def highlight(style: Style): AnimatedTextElement = copy(highlightStyle = style)

  private[dsl] def withProps(props: ElementProps): AnimatedTextElement = copy(props = props)
  private[dsl] override def intrinsicHeight(width: Int): Option[Int]   =
    Some(w.AnimatedText(content, elapsed, effect).preferredHeight)

/** One styled message line, themed at construction. */
final case class NoticeElement(
    message: String,
    level: w.NoticeLevel,
    timestamp: Option[LocalTime],
    messageStyle: Style,
    accentStyle: Style,
    timestampStyle: Style,
    wrap: Boolean = false,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget =
    w.Notice(message, level, timestamp, messageStyle.patch(props.style), accentStyle, timestampStyle, wrap = wrap)

  /** Stamps the notice with the moment the event happened. Pass the time explicitly — a widget that read the clock
    * itself would render a different frame on every repaint.
    */
  def at(time: LocalTime): NoticeElement = copy(timestamp = Some(time))

  /** Lets a long message wrap onto further rows instead of clipping. */
  def wrapped: NoticeElement = copy(wrap = true)

  private[dsl] def withProps(props: ElementProps): NoticeElement     = copy(props = props)
  private[dsl] override def intrinsicHeight(width: Int): Option[Int] =
    Some(w.Notice(message, level, timestamp, wrap = wrap).heightOf(width))

/** A short inline label, themed at construction. */
final case class BadgeElement(
    label: String,
    variant: w.BadgeVariant,
    badgeStyle: Style,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget = w.Badge(label, variant, badgeStyle.patch(props.style))

  /** Bracketed text with no block of colour behind it — for a badge sitting inside prose. */
  def outline: BadgeElement = copy(variant = w.BadgeVariant.Outline)

  /** A coloured dot before plain text: carries the colour without the emphasis. */
  def dot: BadgeElement = copy(variant = w.BadgeVariant.Dot)

  private[dsl] def withProps(props: ElementProps): BadgeElement             = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    direction match
      case Direction.Vertical   => Constraint.Length(1)
      case Direction.Horizontal => Constraint.Length(w.Badge(label, variant).preferredWidth)
  private[dsl] override def intrinsicHeight(width: Int): Option[Int]        = Some(1)

/** An orbit spinner whose colors were resolved from the ambient [[Theme]] at construction.
  *
  * The [[LoadingTheme]] supplies both: the resting path takes `track`, the arc takes `spinner`. Anything the call site
  * sets with `.color`/`.bold`/`.style` layers onto the arc only, so `orbitSpinner().color(Color.Red)` recolors the
  * moving part and leaves the path themed — the rule [[SpinnerElement]] follows for its glyph and label.
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
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    w.OrbitSpinner(elapsed, radius = radius, resolution = resolution).preferredSize match
      case None       => Constraint.Fill(1)
      case Some(size) =>
        direction match
          case Direction.Vertical   => Constraint.Length(size.height)
          case Direction.Horizontal => Constraint.Length(size.width)

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

  private[dsl] def withProps(props: ElementProps): LinearSpinnerElement     = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    (axis, direction) match
      case (w.LinearAxis.Horizontal, Direction.Vertical) => Constraint.Length(1)
      case (w.LinearAxis.Vertical, Direction.Horizontal) => Constraint.Length(1)
      case _                                             => Constraint.Fill(1)

/** A themed block of phase-offset spinners. */
final case class SpinnerGridElement(
    elapsed: FiniteDuration,
    preset: w.SpinnerPreset,
    phase: w.GridPhase,
    glyphStyle: Style,
    ramp: Option[w.ColorRamp] = None,
    props: ElementProps = ElementProps(),
) extends Element:

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
  def widget: Widget =
    w.Skeleton(elapsed, baseStyle.patch(props.style), bandStyle, bandWidth = bandWidth, period = period)

  /** Pins the sweeping band's width, so skeletons of different sizes pulse in step. */
  def band(cells: Int): SkeletonElement = copy(bandWidth = Some(cells))

  /** Sets how long one full sweep takes. */
  def period(duration: FiniteDuration): SkeletonElement = copy(period = duration)

  private[dsl] def withProps(props: ElementProps): SkeletonElement = copy(props = props)

final case class SparklineElement(
    data: Seq[Long],
    max: Option[Long] = None,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget                                                        = w.Sparkline(data, max, props.style)
  private[dsl] def withProps(props: ElementProps): SparklineElement         = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint = singleRow(direction)

final case class TabsElement(
    titles: Seq[String],
    selected: Int = 0,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget                                           = w.Tabs(titles.map(Line.raw), selected, props.style)
  private[dsl] def withProps(props: ElementProps): TabsElement = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint = singleRow(direction)

final case class TableElement(
    rows: Seq[Seq[String]],
    widths: Seq[Constraint],
    header: Option[Seq[String]] = None,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget                                            =
    w.Table(rows.map(_.map(Line.raw)), widths, header.map(_.map(Line.raw)), style = props.style)
  private[dsl] def withProps(props: ElementProps): TableElement = copy(props = props)

/** Escape hatch: any core [[Widget]] as a leaf element (its rendering ignores the element style). `measure` lets
  * width-dependent content (wrapped markdown, images) report its height to the measurement pass.
  */
final case class WidgetElement(
    wrapped: Widget,
    props: ElementProps = ElementProps(),
    measure: Int => Option[Int] = _ => None,
) extends Element:
  def widget: Widget                                                 = wrapped
  private[dsl] def withProps(props: ElementProps): WidgetElement     = copy(props = props)
  private[dsl] override def intrinsicHeight(width: Int): Option[Int] =
    measure(width).orElse(super.intrinsicHeight(width))

// ---- interactive (focusable) elements ----

/** Single-line text input. Editing state (value + cursor) is app-owned; editing keys are handled by the built-in
  * handler while focused, and any consumed key triggers a redraw.
  */
final case class InputElement(
    state: w.TextInputState,
    placeholder: String = "",
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget                                                        =
    val input = w.TextInput(placeholder, showCursor = props.focused, style = props.style)
    (area, buffer) => input.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): InputElement             = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint = singleRow(direction)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]  = Some(handleKey)
  private[dsl] override def builtinPasteHandler: Option[String => Boolean]  = Some { text =>
    if props.focused then
      state.insert(text.replace("\r", "").replace("\n", " ")) // single-line input: fold newlines to spaces
      true
    else false
  }

  private def handleKey(event: KeyEvent): Boolean =
    event.code match
      case KeyCode.Char(c) if event.modifiers.isEmpty || event.modifiers == KeyModifiers.Shift =>
        state.insert(Character.toString(c))
        true
      case _ => cursorKeys(state)(event)

/** A labelled checkbox over a caller-owned `Signal`. Space/Enter (or a click) flips it while focused. */
final case class CheckboxElement(
    label: String,
    checked: Signal[Boolean],
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget                                               = w.Checkbox(label, checked.peek, focusStyled(props))
  private[dsl] def withProps(props: ElementProps): CheckboxElement = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint  = singleRow(direction)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]   =
    Some(toggleOnActivate(() => checked.update(value => !value)))
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some(clickActivates(() => checked.update(value => !value)))

/** A labelled on/off switch — a [[CheckboxElement]] in switch clothing, with the same Space/Enter/click activation. */
final case class ToggleElement(
    label: String,
    on: Signal[Boolean],
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget                                             = w.Toggle(label, on.peek, focusStyled(props))
  private[dsl] def withProps(props: ElementProps): ToggleElement = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint  = singleRow(direction)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]   =
    Some(toggleOnActivate(() => on.update(value => !value)))
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some(clickActivates(() => on.update(value => !value)))

/** A one-row option cycler. Left/Right step through `options` while focused (wrapping at both ends) and a click
  * advances one; the selection is an index into `options`, so it survives nothing but a stable option list.
  */
final case class SelectElement(
    options: Seq[String],
    selected: Signal[Int],
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some(clickActivates(() => if options.nonEmpty then selected.update(index => (index + 1) % options.size)))
  def widget: Widget = w.Select(options, selected.peek, focusStyled(props))
  private[dsl] def withProps(props: ElementProps): SelectElement             = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint  = singleRow(direction)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]   =
    Some(stepsWrapping(options.size, selected))

/** A scrollable single-selection list. Up/Down move the selection while focused, the wheel does the same on hover; the
  * widget scrolls to keep the selection visible. `state` is caller-owned, so the app can read or set the selection.
  */
final case class ListElement(
    items: Seq[String],
    state: w.ListState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some(wheelScrolls(() => state.selectPrevious(items.size), () => state.selectNext(items.size)))
  def widget: Widget =
    // no whole-body focus styling: the selection highlight is the focus cue for scrollable widgets
    val view = w.ListView(items.map(Line.raw), style = props.style)
    (area, buffer) => view.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): ListElement               = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]   = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    event.code match
      case KeyCode.Down =>
        state.selectNext(items.size)
        true
      case KeyCode.Up   =>
        state.selectPrevious(items.size)
        true
      case _            => false

/** A collapsible tree over an in-memory node list. Up/Down move the selection through the *visible* rows and Enter
  * expands or collapses the selected branch (a no-op on a leaf), all while focused.
  */
final case class TreeElement(
    nodes: Seq[w.TreeNode],
    state: w.TreeState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget =
    // no whole-body focus styling: the selection highlight is the focus cue for scrollable widgets
    val tree = w.Tree(nodes, style = props.style)
    (area, buffer) => tree.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): TreeElement             = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean] = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    event.code match
      case KeyCode.Down  =>
        state.selectNext(nodes)
        true
      case KeyCode.Up    =>
        state.selectPrevious(nodes)
        true
      case KeyCode.Enter =>
        state.toggle(nodes)
        true
      case _             => false

/** A vertical menu / dropdown / context menu popup. Up/Down move the highlight (skipping separators and disabled
  * items), Enter (or a click) fires `onSelect` with the chosen index, the wheel scrolls. Escape is left unconsumed so
  * an enclosing app can close it.
  */
final case class MenuElement(
    items: Seq[w.MenuItem],
    state: w.MenuState,
    onSelect: Int => Unit,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget                                                         =
    val menu = w.Menu(items, style = props.style, highlightStyle = props.focusStyle)
    (area, buffer) => menu.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): MenuElement               = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint  =
    direction match
      case Direction.Vertical   => Constraint.Length(items.size + 2)
      case Direction.Horizontal => Constraint.Length(w.Menu(items).width)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]   = Some(handleKey)
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some(handleMouse)

  private def handleKey(event: KeyEvent): Boolean =
    event.code match
      case KeyCode.Down                      =>
        state.selectNext(items)
        true
      case KeyCode.Up                        =>
        state.selectPrevious(items)
        true
      case KeyCode.Enter | KeyCode.Char(' ') =>
        if items.lift(state.selected).exists(_.selectable) then onSelect(state.selected)
        true
      case _                                 =>
        false

  private def handleMouse(event: MouseEvent, area: Rect): Boolean =
    event.kind match
      case MouseEventKind.ScrollUp   =>
        state.selectPrevious(items)
        true
      case MouseEventKind.ScrollDown =>
        state.selectNext(items)
        true
      case MouseEventKind.Down       =>
        val row = event.y - (area.y + 1) + state.offset // +1 skips the top border
        if row >= 0 && row < items.size && items(row).selectable then
          state.selected = row
          onSelect(row)
        true
      case _                         => false

/** Renders `content` at an absolute offset inside whatever area it is given, sized `width` x `height` (clipped to the
  * area). The building block for context menus and tooltips anchored at a mouse or widget position — compose it over a
  * base with [[Element.layers]].
  */
final case class PositionedElement(
    dx: Int,
    dy: Int,
    width: Int,
    height: Int,
    content: Element,
    props: ElementProps = ElementProps(),
) extends Element:
  override def children: Seq[Element]                                               = Seq(content)
  def widget: Widget                                                                =
    (area, buffer) =>
      val target = Rect(area.x + dx, area.y + dy, width, height).intersection(area)
      if !target.isEmpty then content.widget.render(target, buffer)
  private[dsl] def withProps(props: ElementProps): PositionedElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): PositionedElement =
    copy(content = children.headOption.getOrElse(content))

/** Fills its whole area with `fill` (a solid background) before rendering `inner` — what the chrome bars use to read as
  * continuous surfaces. Transparent to focus and event routing.
  */
final case class FilledElement(
    inner: Element,
    fill: Style,
    props: ElementProps = ElementProps(),
) extends Element:
  override def children: Seq[Element]                                           = inner.children
  def widget: Widget                                                            =
    (area, buffer) =>
      var y = area.y
      while y < area.bottom do
        var x = area.x
        while x < area.right do
          buffer.set(x, y, Cell(" ", fill))
          x += 1
        y += 1
      inner.widget.render(area, buffer)
  private[dsl] def withProps(props: ElementProps): FilledElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): FilledElement =
    copy(inner = inner.withChildren(children))
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]      = inner.builtinKeyHandler
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler]    =
    inner.builtinMouseHandler
  private[dsl] override def builtinPasteHandler: Option[String => Boolean]      = inner.builtinPasteHandler
  private[dsl] override def preferredSize(direction: Direction): Constraint     = inner.preferredSize(direction)

/** Z-ordered stacking: every child renders over the full area in order, so later children paint over earlier ones — the
  * primitive under dialogs, toasts, palettes, and splash overlays.
  */
final case class LayersElement(
    override val children: Seq[Element],
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget                                                            =
    (area, buffer) => children.foreach(_.widget.render(area, buffer))
  private[dsl] def withProps(props: ElementProps): LayersElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): LayersElement = copy(children = children)

/** A subtree chosen by the terminal's size — a media query, not a container query.
  *
  * `build` is handed the size of the whole terminal, not this node's allotted area, and runs during [[ResponsivePass]]
  * before the focus pass, so whatever it returns is an ordinary part of the tree: its focusables take Tab stops, its
  * handlers receive keys, and clicks hit-test into it — unless the node sits on a layer a modal covers, in which case
  * [[ResponsivePass]] suppresses the branch along with the rest of that layer. That is what makes swapping *components*
  * work and not just constraints — a `row` of three panes at 120 columns can become a `tabbedContent` at 60.
  *
  * The node is otherwise transparent: it holds the built branch as its single child, so a constraint, a style, or an
  * `onKeyEvent` set on it applies exactly as it would on a `column` wrapping the same content, and the branch's own
  * layout claim becomes the node's when none is set explicitly.
  *
  * `resolved` is filled in by [[ResponsivePass]] on every render, never by user code — the same contract
  * `ElementProps.focused` has. While it is empty the node still renders: it falls back to building against its own
  * area, so a construction test that draws `element.widget` straight into a buffer without a [[TuiApp]] behind it shows
  * content rather than blank space. That fallback has no focus pass behind it, so focusables inside it are inert —
  * which is why the pass exists rather than doing this at render time.
  */
final case class ResponsiveElement(
    build: Size => Element,
    resolved: Option[Element] = None,
    props: ElementProps = ElementProps(),
) extends Element:
  override def children: Seq[Element]                                               = resolved.toSeq
  def widget: Widget                                                                =
    resolved match
      case Some(branch) => branch.widget
      case None         => (area, buffer) => build(Size(area.width, area.height)).widget.render(area, buffer)
  private[dsl] def withProps(props: ElementProps): ResponsiveElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): ResponsiveElement =
    copy(resolved = children.headOption.orElse(resolved))

  /** This node holding what `build` produces at `size` — [[ResponsivePass]]'s single mutation. */
  private[dsl] def resolvedAt(branch: Element): ResponsiveElement = copy(resolved = Some(branch))

  private[dsl] override def preferredSize(direction: Direction): Constraint =
    resolved.map(_.preferredSize(direction)).getOrElse(Constraint.Fill(1))

  private[dsl] override def intrinsicHeight(width: Int): Option[Int] =
    props.constraint match
      case Some(Constraint.Length(cells)) => Some(cells)
      case Some(_)                        => None
      case None                           => resolved.flatMap(_.intrinsicHeight(width))

/** A scrollable viewport over taller-than-the-screen content. Up/Down/PageUp/PageDown scroll while focused. */
final case class ScrollViewElement(
    content: Element,
    contentHeight: Int,
    state: w.ScrollViewState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  override def children: Seq[Element]                                               = Seq(content)
  def widget: Widget                                                                =
    (area, buffer) =>
      val resolved =
        if contentHeight > 0 then contentHeight
        else content.intrinsicHeight(math.max(1, area.width - 1)).getOrElse(area.height)
      // the content renders into an offscreen buffer, so it cannot see where this scroll view sits on screen; the
      // focus pass's viewport wrapper needs that to translate the areas recorded underneath it
      content match
        case viewport: ScrollViewportElement => viewport.publishScreenArea(area)
        case _                               => ()
      w.ScrollView(content.widget, resolved).render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): ScrollViewElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): ScrollViewElement =
    copy(content = children.headOption.getOrElse(content))
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]          =
    Some(scrollKeys(rows => state.scrollUp(rows), rows => state.scrollDown(rows)))
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler]        =
    Some(wheelScrolls(() => state.scrollUp(), () => state.scrollDown()))

/** A tab row plus the selected page (Textual's `TabbedContent`): Left/Right switch pages while focused. Only the active
  * page's focusables participate in the tab order.
  */
final case class TabbedContentElement(
    titles: Seq[String],
    activePage: Element,
    selected: Signal[Int],
    pageCount: Int,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  override def children: Seq[Element]                                                  = Seq(activePage)
  def widget: Widget                                                                   =
    val tabs = w.Tabs(titles.map(Line.raw), selected.peek, focusStyled(props))
    (area, buffer) =>
      w.Column(
        Seq(
          w.LayoutItem(Constraint.Length(1), tabs),
          w.LayoutItem(Constraint.Fill(1), activePage.widget),
        )
      ).render(area, buffer)
  private[dsl] def withProps(props: ElementProps): TabbedContentElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): TabbedContentElement =
    copy(activePage = children.headOption.getOrElse(activePage))
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]             =
    Some(stepsWrapping(pageCount, selected))

/** A toggleable section: `▸ title` collapsed, `▾ title` plus the body expanded; Enter/Space toggle while focused.
  * Collapsed bodies leave the tab order entirely.
  */
final case class CollapsibleElement(
    title: String,
    body: Element,
    expanded: Signal[Boolean],
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  override def children: Seq[Element]                                 = if expanded.peek then Seq(body) else Seq.empty
  def widget: Widget                                                  =
    val isOpen         = expanded.peek
    val marker         = if isOpen then "▾ " else "▸ "
    val header: Widget = (area, buffer) => buffer.setString(area.x, area.y, marker + title, focusStyled(props))
    (area, buffer) =>
      if isOpen then
        w.Column(
          Seq(w.LayoutItem(Constraint.Length(1), header), w.LayoutItem(Constraint.Fill(1), body.widget))
        ).render(area, buffer)
      else header.render(area, buffer)
  private[dsl] def withProps(props: ElementProps): CollapsibleElement = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): CollapsibleElement =
    copy(body = children.headOption.getOrElse(body))
  private[dsl] override def preferredSize(direction: Direction): Constraint          =
    direction match
      case Direction.Vertical if !expanded.peek => Constraint.Length(1)
      case _                                    => Constraint.Fill(1)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]           =
    Some(toggleOnActivate(() => expanded.update(open => !open)))

/** Two panes split by an adjustable divider: `[`/`]` shift the split while the pane itself is focused. */
final case class SplitPaneElement(
    first: Element,
    second: Element,
    splitPercent: Signal[Int],
    horizontal: Boolean = true,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler]       =
    Some { (event, area) =>
      if event.kind == MouseEventKind.Drag then
        val fraction =
          if horizontal then (event.x - area.x).toDouble / math.max(1, area.width)
          else (event.y - area.y).toDouble / math.max(1, area.height)
        splitPercent.set(math.max(10, math.min(90, math.round(fraction * 100).toInt)))
        true
      else false
    }
  override def children: Seq[Element]                                              = Seq(first, second)
  def widget: Widget                                                               =
    val percent = math.max(10, math.min(90, splitPercent.peek))
    val items   = Seq(
      first.layoutItem(direction).copy(constraint = Constraint.Percentage(percent)),
      second.layoutItem(direction).copy(constraint = Constraint.Fill(1)),
    )
    if horizontal then w.Row(items, spacing = 1) else w.Column(items, spacing = 0)
  private[dsl] def withProps(props: ElementProps): SplitPaneElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): SplitPaneElement =
    children match
      case Seq(a, b) => copy(first = a, second = b)
      case _         => this
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]         = Some(handleKey)

  private def direction: Direction = if horizontal then Direction.Horizontal else Direction.Vertical

  private def handleKey(event: KeyEvent): Boolean =
    event.code match
      case KeyCode.Char('[') =>
        splitPercent.update(value => math.max(10, value - 5))
        true
      case KeyCode.Char(']') =>
        splitPercent.update(value => math.min(90, value + 5))
        true
      case _                 => false

/** A text input with a live suggestion dropdown: typing filters `suggestions` (subsequence match), Up/Down move the
  * highlight, Enter accepts it into the input and fires `onAccept`.
  */
final case class AutocompleteElement(
    state: AutocompleteState,
    suggestions: Seq[String],
    onAccept: String => Unit = _ => (),
    maxSuggestions: Int = 5,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:

  private def matches: Seq[String] =
    val query = state.input.value
    if query.isEmpty then Seq.empty
    else suggestions.filter(Fuzzy.matcher(query)).take(maxSuggestions)

  def widget: Widget                                                        =
    val visible   = matches
    val highlight = math.max(0, math.min(state.highlighted, math.max(0, visible.size - 1)))
    val input     = w.TextInput(showCursor = props.focused, style = props.style)
    (area, buffer) =>
      input.render(Rect(area.x, area.y, area.width, 1), buffer, state.input)
      visible.zipWithIndex.foreach { (candidate, index) =>
        val rowStyle = if index == highlight && props.focused then props.style.reverse else props.style.dim
        buffer.setString(area.x + 2, area.y + 1 + index, candidate, rowStyle)
      }
  private[dsl] def withProps(props: ElementProps): AutocompleteElement      = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    direction match
      case Direction.Vertical   => Constraint.Length(1 + matches.size)
      case Direction.Horizontal => Constraint.Fill(1)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]  = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    event.code match
      case KeyCode.Char(c) if event.modifiers.isEmpty || event.modifiers == KeyModifiers.Shift =>
        state.input.insert(Character.toString(c))
        state.highlighted = 0
        true
      case KeyCode.Backspace                                                                   =>
        state.input.backspace()
        state.highlighted = 0
        true
      case KeyCode.Down                                                                        =>
        state.highlighted = math.min(state.highlighted + 1, math.max(0, matches.size - 1))
        true
      case KeyCode.Up                                                                          =>
        state.highlighted = math.max(0, state.highlighted - 1)
        true
      case KeyCode.Enter                                                                       =>
        matches.lift(math.min(state.highlighted, math.max(0, matches.size - 1))) match
          case Some(choice) =>
            state.accept(choice)
            onAccept(choice)
            true
          case None         => false
      case KeyCode.Left                                                                        =>
        state.input.moveLeft()
        true
      case KeyCode.Right                                                                       =>
        state.input.moveRight()
        true
      case _                                                                                   => false

/** A file chooser over a [[FilePickerState]]: arrows navigate, Enter opens directories or accepts a file into
  * `state.chosen`.
  */
final case class FilePickerElement(
    state: FilePickerState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget                                                       =
    val tree = w.DirectoryTree(style = props.style)
    (area, buffer) =>
      val treeArea   = Rect(area.x, area.y, area.width, math.max(0, area.height - 1))
      tree.render(treeArea, buffer, state.tree)
      val chosenLine = state.chosen.peek.map(path => s"→ $path").getOrElse("→ (nothing selected)")
      buffer.setString(area.x, area.bottom - 1, chosenLine, props.style.dim)
  private[dsl] def withProps(props: ElementProps): FilePickerElement       = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean] = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    event.code match
      case KeyCode.Down  =>
        state.tree.selectNext()
        true
      case KeyCode.Up    =>
        state.tree.selectPrevious()
        true
      case KeyCode.Enter =>
        state.tree.selected match
          case Some(path) if java.nio.file.Files.isDirectory(path) =>
            state.tree.toggle()
            true
          case Some(path)                                          =>
            state.chosen.set(Some(path))
            true
          case None                                                => false
      case _             => false

/** Mutually exclusive options: Up/Down move the selection while focused. */
final case class RadioGroupElement(
    options: Seq[String],
    selected: Signal[Int],
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget = w.RadioGroup(options, selected.peek, props.style, focusStyled(props).bold)
  private[dsl] def withProps(props: ElementProps): RadioGroupElement        = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    direction match
      case Direction.Vertical   => Constraint.Length(math.max(1, options.size))
      case Direction.Horizontal => Constraint.Fill(1)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]  = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    if options.isEmpty then false
    else
      event.code match
        case KeyCode.Down =>
          selected.update(index => math.min(index + 1, options.size - 1))
          true
        case KeyCode.Up   =>
          selected.update(index => math.max(index - 1, 0))
          true
        case _            => false

/** A value slider: Left/Right adjust by `step`, Home/End jump to the bounds, while focused. */
final case class SliderElement(
    value: Signal[Int],
    min: Int = 0,
    max: Int = 100,
    step: Int = 5,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some { (event, area) =>
      event.kind match
        case MouseEventKind.Down | MouseEventKind.Drag =>
          if area.width > 3 then
            val fraction = (event.x - area.x - 1).toDouble / (area.width - 3)
            value.set(min + math.round(math.max(0.0, math.min(1.0, fraction)) * (max - min)).toInt)
          true
        case _                                         => false
    }
  def widget: Widget = w.Slider(value.peek, min, max, props.style, focusStyled(props).bold)
  private[dsl] def withProps(props: ElementProps): SliderElement             = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint  = singleRow(direction)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]   = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    event.code match
      case KeyCode.Left  =>
        value.update(v => math.max(min, v - step))
        true
      case KeyCode.Right =>
        value.update(v => math.min(max, v + step))
        true
      case KeyCode.Home  =>
        value.set(min)
        true
      case KeyCode.End   =>
        value.set(max)
        true
      case _             => false

/** A multi-select list: Up/Down move the cursor, Space toggles membership of the cursor row. */
final case class SelectionListElement(
    items: Seq[String],
    selected: Signal[Set[Int]],
    state: w.ListState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget                                                       =
    val chosen   = selected.peek
    val rendered = items.zipWithIndex.map { (item, index) =>
      val marker = if chosen.contains(index) then "[x] " else "[ ] "
      Line.raw(marker + item)
    }
    val view     = w.ListView(rendered, style = props.style)
    (area, buffer) => view.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): SelectionListElement    = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean] = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    event.code match
      case KeyCode.Down      =>
        state.selectNext(items.size)
        true
      case KeyCode.Up        =>
        state.selectPrevious(items.size)
        true
      case KeyCode.Char(' ') =>
        state.selected.foreach { cursor =>
          selected.update(current => if current.contains(cursor) then current - cursor else current + cursor)
        }
        true
      case _                 => false

/** A text input restricted to numbers (optional single leading minus and, with `allowDecimal`, one dot). */
final case class NumberInputElement(
    state: w.TextInputState,
    allowDecimal: Boolean = false,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget                                                        =
    val input = w.TextInput(showCursor = props.focused, style = props.style)
    (area, buffer) => input.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): NumberInputElement       = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint = singleRow(direction)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]  = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    event.code match
      case KeyCode.Char(c) if event.modifiers.isEmpty =>
        if accepts(c) then state.insert(Character.toString(c))
        true // swallow rejected characters too: they must not bubble as global keys while typing
      case _ => cursorKeys(state)(event)

  private def accepts(codePoint: Int): Boolean =
    if Character.isDigit(codePoint) then true
    else if codePoint == '-' then state.cursor == 0 && !state.value.startsWith("-")
    else if codePoint == '.' then allowDecimal && !state.value.contains('.')
    else false

/** A template-driven input (`##/##/####`): `#` accepts a digit, `A` a letter, literals insert themselves. */
final case class MaskedInputElement(
    state: w.TextInputState,
    mask: String,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget                                                        =
    val input = w.TextInput(placeholder = mask, showCursor = props.focused, style = props.style)
    (area, buffer) => input.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): MaskedInputElement       = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint = singleRow(direction)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]  = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    event.code match
      case KeyCode.Char(c) if event.modifiers.isEmpty =>
        typeChar(c)
        true
      case KeyCode.Backspace                          =>
        eraseSlot()
        true
      case _                                          => false

  private def typeChar(codePoint: Int): Unit =
    state.moveEnd()
    var position = currentLength
    // literals between fillable slots insert themselves
    while position < mask.length && !isSlot(mask.charAt(position)) do
      state.insert(mask.charAt(position).toString)
      position += 1
    if position < mask.length && slotAccepts(mask.charAt(position), codePoint) then
      state.insert(Character.toString(codePoint))

  private def eraseSlot(): Unit =
    state.moveEnd()
    state.backspace()
    while currentLength > 0 && !isSlot(mask.charAt(currentLength - 1)) do state.backspace()

  private def currentLength: Int = CharWidth.graphemeClusters(state.value).size

  private def isSlot(m: Char): Boolean = m == '#' || m == 'A'

  private def slotAccepts(m: Char, codePoint: Int): Boolean =
    (m == '#' && Character.isDigit(codePoint)) || (m == 'A' && Character.isLetter(codePoint))

/** A page indicator: Left/Right change the page while focused. */
final case class PaginatorElement(
    current: Signal[Int],
    total: Int,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget = w.Paginator(current.peek, total, props.style, focusStyled(props).bold)
  private[dsl] def withProps(props: ElementProps): PaginatorElement         = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint = singleRow(direction)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]  = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    if total == 0 then false
    else
      event.code match
        case KeyCode.Left  =>
          current.update(page => math.max(0, page - 1))
          true
        case KeyCode.Right =>
          current.update(page => math.min(total - 1, page + 1))
          true
        case _             => false

/** A pressable button: Enter or Space triggers `action` while focused. */
final case class ButtonElement(
    label: String,
    action: () => Unit,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget                                                         = w.Button(label, focusStyled(props))
  private[dsl] def withProps(props: ElementProps): ButtonElement             = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint  = singleRow(direction)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]   =
    Some(toggleOnActivate(action))
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some(clickActivates(action))

/** A scrollable log panel: Up/Down (and PageUp/PageDown) scroll while focused; the tail re-follows when scrolled back
  * to the bottom.
  */
final case class LogElement(
    state: w.LogState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some(wheelScrolls(() => state.scrollUp(), () => state.scrollDown()))
  def widget: Widget                                                         =
    val log = w.Log(props.style)
    (area, buffer) => log.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): LogElement                = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]   =
    Some(scrollKeys(rows => state.scrollUp(rows), rows => state.scrollDown(rows)))

/** Multi-line editor element. While focused it consumes printable characters, Enter (newline), Backspace, Delete,
  * arrows, Home/End, Ctrl+Z (undo) and Ctrl+Y (redo) — Tab stays free for focus traversal. A bracketed paste lands as
  * one edit, with carriage returns stripped.
  */
final case class TextAreaElement(
    state: w.TextAreaState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  private[dsl] override def builtinPasteHandler: Option[String => Boolean] = Some { text =>
    if props.focused then
      state.insert(text.replace("\r", ""))
      true
    else false
  }
  def widget: Widget                                                       =
    val editor = w.TextArea(showCursor = props.focused, style = props.style)
    (area, buffer) => editor.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): TextAreaElement         = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean] = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    event match
      case KeyEvent(KeyCode.Char('z'), modifiers) if modifiers.has(KeyModifiers.Ctrl)                   =>
        state.undo()
        true
      case KeyEvent(KeyCode.Char('y'), modifiers) if modifiers.has(KeyModifiers.Ctrl)                   =>
        state.redo()
        true
      case KeyEvent(KeyCode.Char(c), modifiers) if modifiers.isEmpty || modifiers == KeyModifiers.Shift =>
        state.insert(Character.toString(c))
        true
      case KeyEvent(KeyCode.Enter, _)                                                                   =>
        state.newline()
        true
      case KeyEvent(KeyCode.Backspace, _)                                                               =>
        state.backspace()
        true
      case KeyEvent(KeyCode.Delete, _)                                                                  =>
        state.delete()
        true
      case KeyEvent(KeyCode.Left, _)                                                                    =>
        state.moveLeft()
        true
      case KeyEvent(KeyCode.Right, _)                                                                   =>
        state.moveRight()
        true
      case KeyEvent(KeyCode.Up, _)                                                                      =>
        state.moveUp()
        true
      case KeyEvent(KeyCode.Down, _)                                                                    =>
        state.moveDown()
        true
      case KeyEvent(KeyCode.Home, _)                                                                    =>
        state.moveHome()
        true
      case KeyEvent(KeyCode.End, _)                                                                     =>
        state.moveEnd()
        true
      case _                                                                                            => false

/** A filesystem browser. Up/Down move the selection, Enter expands or collapses the selected directory, while focused.
  * Listings are read lazily and cached in `state` — it touches the disk on expansion, never per frame.
  */
final case class DirectoryTreeElement(
    state: w.DirectoryTreeState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget                                                       =
    val tree = w.DirectoryTree(style = props.style)
    (area, buffer) => tree.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): DirectoryTreeElement    = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean] = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    event.code match
      case KeyCode.Down  =>
        state.selectNext()
        true
      case KeyCode.Up    =>
        state.selectPrevious()
        true
      case KeyCode.Enter =>
        state.toggle()
        true
      case _             => false

/** A sortable, filterable table. Up/Down move the row selection while focused, and PageUp/PageDown turn the page once
  * `state.pageSize` is set (they are left unconsumed otherwise, so they keep bubbling). Sorting and filtering have no
  * built-in keys — drive `state.sortBy`/`state.setFilter` from the app's own bindings.
  */
final case class DataTableElement(
    table: w.DataTable,
    state: w.DataTableState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget                                                       =
    (area, buffer) => table.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): DataTableElement        = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean] = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    event.code match
      case KeyCode.Down                                =>
        state.selectNext(table.visibleRows(state).size)
        true
      case KeyCode.Up                                  =>
        state.selectPrevious(table.visibleRows(state).size)
        true
      case KeyCode.PageDown if state.pageSize.nonEmpty =>
        state.nextPage(table.filteredRows(state).size)
        true
      case KeyCode.PageUp if state.pageSize.nonEmpty   =>
        state.previousPage()
        true
      case _                                           => false

/** Wraps a focusable element during the focus pass so its rendered area is recorded for click-to-focus hit-testing.
  * Transparent for everything else: props, children, and handlers delegate to the wrapped node.
  */
private[dsl] final case class TrackedElement(inner: Element, index: Int, tracker: FocusTracker) extends Element:
  def props: ElementProps                                                        = inner.props
  override def children: Seq[Element]                                            = inner.children
  def widget: Widget                                                             =
    (area, buffer) =>
      tracker.record(index, area)
      inner.widget.render(area, buffer)
  private[dsl] def withProps(props: ElementProps): TrackedElement                = copy(inner = inner.withProps(props))
  private[dsl] override def withChildren(children: Seq[Element]): TrackedElement =
    copy(inner = inner.withChildren(children))
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]       = inner.builtinKeyHandler
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler]     =
    inner.builtinMouseHandler
  private[dsl] override def builtinPasteHandler: Option[String => Boolean]       = inner.builtinPasteHandler
  private[dsl] override def preferredSize(direction: Direction): Constraint      = inner.preferredSize(direction)

/** Wraps the content of a [[ScrollViewElement]] during the focus pass so the areas recorded inside it are screen areas.
  *
  * `w.ScrollView` renders its content into an offscreen buffer anchored at (0, 0) and blits the visible window into
  * place, so every rect the content subtree is handed is in content coordinates. This node publishes the
  * content-to-screen mapping to the [[FocusTracker]] for exactly the duration of the content render — pushed from
  * inside that render, so the paths where `ScrollView` draws no content push nothing, and popped in a `finally`.
  * Transparent otherwise: it renders, measures and routes straight to `inner`, and carries neutral props of its own so
  * it never doubles up the wrapped element's handlers or focus state.
  *
  * Owned by the [[FocusPass]] that built it and touched only on the render thread, like the tracker it writes to. A
  * focusable only partly inside the viewport records the clipped rect; width is never clipped (the offscreen buffer is
  * exactly the viewport width less the scrollbar column), so `x`/`width`-based built-ins such as the slider stay exact,
  * while a `y`/`height`-based one — the splitPane divider — reads the clipped height when half scrolled out.
  */
private[dsl] final class ScrollViewportElement(
    val inner: Element,
    tracker: FocusTracker,
    state: w.ScrollViewState,
) extends Element:

  /** The enclosing scroll view's own area, published each frame just before it renders this content. */
  private var screenArea: Rect = Rect.Zero

  private[dsl] def publishScreenArea(area: Rect): Unit = screenArea = area

  val props: ElementProps             = ElementProps()
  override def children: Seq[Element] = Seq(inner)

  def widget: Widget =
    (area, buffer) =>
      // `area` is the offscreen content rect: its width is the viewport width less any scrollbar column, and
      // `state.offset` has already been clamped by `ScrollView.render`
      val viewport  = Rect(screenArea.x, screenArea.y, area.width, screenArea.height)
      val transform = ViewportTransform(screenArea.x - area.x, screenArea.y - area.y - state.offset, viewport)
      tracker.pushViewport(transform)
      try inner.widget.render(area, buffer)
      finally tracker.popViewport()

  private[dsl] def withProps(props: ElementProps): Element =
    val _ = props
    this

  private[dsl] override def withChildren(children: Seq[Element]): Element =
    children.headOption.fold(this)(child => ScrollViewportElement(child, tracker, state))

  private[dsl] override def preferredSize(direction: Direction): Constraint = inner.preferredSize(direction)
  // load-bearing: `ScrollViewElement.widget` measures its content through this wrapper when no explicit content
  // height was given, so the trait default here would silently collapse an auto-sized scroll view
  private[dsl] override def intrinsicHeight(width: Int): Option[Int]        = inner.intrinsicHeight(width)

/** Wraps a non-focusable element that carries an `onMouseEvent` during the focus pass, so its rendered area is recorded
  * and the mouse router can offer it only the events that landed inside it. Focusable elements need no such wrapper —
  * [[TrackedElement]] already records their area under their focus index. Transparent for everything else: props,
  * children, measurement and handlers delegate to the wrapped node.
  */
private[dsl] final case class PointerElement(inner: Element, pointerId: Int, tracker: FocusTracker) extends Element:
  def props: ElementProps                                                        = inner.props
  override def children: Seq[Element]                                            = inner.children
  def widget: Widget                                                             =
    (area, buffer) =>
      tracker.recordPointer(pointerId, area)
      inner.widget.render(area, buffer)
  private[dsl] def withProps(props: ElementProps): PointerElement                = copy(inner = inner.withProps(props))
  private[dsl] override def withChildren(children: Seq[Element]): PointerElement =
    copy(inner = inner.withChildren(children))
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]       = inner.builtinKeyHandler
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler]     = inner.builtinMouseHandler
  private[dsl] override def builtinPasteHandler: Option[String => Boolean]       = inner.builtinPasteHandler
  private[dsl] override def preferredSize(direction: Direction): Constraint      = inner.preferredSize(direction)
  // load-bearing: containers recurse through `children.map(_.intrinsicHeight(...))`, so falling back to the trait
  // default here would silently change the measured height of any handler-carrying panel/row/column
  private[dsl] override def intrinsicHeight(width: Int): Option[Int]             = inner.intrinsicHeight(width)

/** The factory set: one obvious home, re-exported at the package top level so `import io.worxbend.tui.dsl.*` brings
  * every factory in.
  */
object Element:

  def text(content: String): TextElement = TextElement(content)

  def panel(title: String)(children: Element*): PanelElement = PanelElement(Some(title), children)

  def panel(children: Element*): PanelElement = PanelElement(None, children)

  def row(children: Element*): RowElement = RowElement(children)

  def column(children: Element*): ColumnElement = ColumnElement(children)

  /** Flexible blank space (fills what siblings leave over). */
  def spacer: Element = SpacerElement()

  /** Fixed blank space of exactly `cells` rows/columns. */
  def spacer(cells: Int): Element =
    SpacerElement(ElementProps(constraint = Some(Constraint.Length(cells))))

  def gauge(ratio: Double): GaugeElement = GaugeElement(ratio)

  def sparkline(data: Seq[Long]): SparklineElement = SparklineElement(data)

  def tabs(titles: Seq[String], selected: Int = 0): TabsElement = TabsElement(titles, selected)

  def table(rows: Seq[Seq[String]], widths: Constraint*): TableElement =
    TableElement(rows, widths)

  def widget(wrapped: Widget): WidgetElement = WidgetElement(wrapped)

  def input(state: w.TextInputState, placeholder: String = ""): InputElement =
    InputElement(state, placeholder)

  def checkbox(label: String, checked: Signal[Boolean]): CheckboxElement =
    CheckboxElement(label, checked)

  def toggle(label: String, on: Signal[Boolean]): ToggleElement =
    ToggleElement(label, on)

  def select(options: Seq[String], selected: Signal[Int]): SelectElement =
    SelectElement(options, selected)

  def list(items: Seq[String], state: w.ListState): ListElement =
    ListElement(items, state)

  def tree(nodes: Seq[w.TreeNode], state: w.TreeState): TreeElement =
    TreeElement(nodes, state)

  def barChart(data: Seq[(String, Long)], barWidth: Int = 3): WidgetElement =
    WidgetElement(w.BarChart(data, barWidth))

  def chart(datasets: Seq[w.Dataset], xBounds: (Double, Double), yBounds: (Double, Double)): WidgetElement =
    WidgetElement(w.Chart(datasets, xBounds, yBounds))

  def canvas(xBounds: (Double, Double), yBounds: (Double, Double))(shapes: w.Shape*): WidgetElement =
    WidgetElement(w.Canvas(xBounds, yBounds, shapes))

  def calendar(year: Int, month: Int, selected: Option[Int] = None): WidgetElement =
    WidgetElement(w.Calendar(year, month, selected))

  def pieChart(data: Seq[(String, Double)]): WidgetElement =
    WidgetElement(w.PieChart(data))

  def stackedBarChart(data: Seq[(String, Seq[Long])], barWidth: Int = 3): WidgetElement =
    WidgetElement(w.StackedBarChart(data, barWidth))

  def heatmap(values: Seq[Seq[Double]]): WidgetElement =
    WidgetElement(w.Heatmap(values))

  /** An animation frame indicator. Needs a `config.tickRate` to animate and nothing else — it reads the ambient
    * [[AnimationClock]], so there is no counter to declare, advance, or thread through.
    *
    * Colors come from the ambient [[Theme]]'s [[LoadingTheme]]; the animation from [[w.SpinnerPreset]], swappable with
    * `.preset(...)`.
    */
  def spinner(label: String = "")(using theme: Theme, scope: ReactiveScope): SpinnerElement =
    spinnerAt(AnimationClock.elapsed, label)

  /** A spinner on a clock the caller drives, for a progress animation tied to something other than wall time. */
  def spinnerAt(elapsed: FiniteDuration, label: String = "")(using theme: Theme): SpinnerElement =
    SpinnerElement(elapsed, label, w.SpinnerPreset.Dots, theme.loading.spinner, theme.loading.label)

  /** Text carrying a time-based effect, on the ambient [[AnimationClock]].
    *
    * Defaults to a travelling highlight; `.effect(...)` swaps in a typewriter, a scrolling gradient, a shimmer, or a
    * bounce. Colors come from the ambient [[Theme]].
    */
  def animatedText(content: String)(using theme: Theme, scope: ReactiveScope): AnimatedTextElement =
    animatedTextAt(content, AnimationClock.elapsed)

  /** Animated text on a clock the caller drives. */
  def animatedTextAt(content: String, elapsed: FiniteDuration)(using theme: Theme): AnimatedTextElement =
    AnimatedTextElement(content, elapsed, w.TextEffect.Wave(), theme.muted, theme.accent)

  /** One styled message line: an icon, an optional timestamp, and the message. Colors follow the level. */
  def notice(message: String, level: w.NoticeLevel = w.NoticeLevel.Info)(using theme: Theme): NoticeElement =
    NoticeElement(message, level, None, theme.primary, noticeStyle(level), theme.muted)

  /** A short inline label. Defaults to a solid badge in the theme's accent; `.outline` and `.dot` are quieter. */
  def badge(label: String)(using theme: Theme): BadgeElement =
    BadgeElement(label, w.BadgeVariant.Solid, theme.accent)

  /** A badge carrying a severity's own tag and color — `badge(NoticeLevel.Error)` reads `FAIL`. */
  def badge(level: w.NoticeLevel)(using theme: Theme): BadgeElement =
    BadgeElement(level.tag, w.BadgeVariant.Solid, noticeStyle(level))

  private def noticeStyle(level: w.NoticeLevel)(using theme: Theme): Style =
    level match
      case w.NoticeLevel.Success => theme.success
      case w.NoticeLevel.Info    => theme.accent
      case w.NoticeLevel.Warning => theme.warning
      case w.NoticeLevel.Error   => theme.error

  def dialog(title: String, message: String, buttons: Seq[String] = Seq("OK"), selected: Int = 0): WidgetElement =
    WidgetElement(w.Dialog(title, Text.raw(message), buttons, selected))

  def dualSparkline(upper: Seq[Long], lower: Seq[Long]): WidgetElement =
    WidgetElement(w.DualSparkline(upper, lower))

  /** A pulsing placeholder for content that has not arrived yet, on the ambient [[AnimationClock]]. */
  def skeleton()(using theme: Theme, scope: ReactiveScope): SkeletonElement =
    skeletonAt(AnimationClock.elapsed)

  /** A skeleton on a clock the caller drives. */
  def skeletonAt(elapsed: FiniteDuration)(using theme: Theme): SkeletonElement =
    SkeletonElement(elapsed, theme.loading.track, theme.loading.band)

  /** A figure with an arc chasing round it — a spinner big enough to fill a pane. Needs a `config.tickRate` and nothing
    * else: it reads the ambient [[AnimationClock]], so there is no counter to declare or thread through.
    *
    * Defaults to a circle fitted to its area with a quarter of it lit as a fading comet; `.radius(n)` pins the size,
    * `.path(OrbitPath.Square)` squares it off, `.markers("*")` drops it to an ASCII-safe cell grid. Colors come from
    * the ambient [[Theme]]'s [[LoadingTheme]]: the resting path is the track, the arc is the spinner.
    */
  def orbitSpinner()(using theme: Theme, scope: ReactiveScope): OrbitSpinnerElement =
    orbitSpinnerAt(AnimationClock.elapsed)

  /** An orbit spinner on a clock the caller drives. */
  def orbitSpinnerAt(elapsed: FiniteDuration)(using theme: Theme): OrbitSpinnerElement =
    OrbitSpinnerElement(elapsed, theme.loading.track, theme.loading.spinner)

  /** A head travelling a one-cell track, on the ambient [[AnimationClock]] — the row-or-column-shaped member of the
    * family, for a status line under a log pane or a column beside one.
    */
  def linearSpinner()(using theme: Theme, scope: ReactiveScope): LinearSpinnerElement =
    linearSpinnerAt(AnimationClock.elapsed)

  /** A linear spinner on a clock the caller drives. */
  def linearSpinnerAt(elapsed: FiniteDuration)(using theme: Theme): LinearSpinnerElement =
    LinearSpinnerElement(elapsed, theme.loading.track, theme.loading.spinner)

  /** A block of phase-offset spinners, on the ambient [[AnimationClock]]. `.preset(...)` picks the per-slot animation
    * from the ordinary spinner catalogue — including the ASCII ones — and `.phase(...)` decides whether the block
    * pulses, waves, or ripples.
    */
  def spinnerGrid()(using theme: Theme, scope: ReactiveScope): SpinnerGridElement =
    spinnerGridAt(AnimationClock.elapsed)

  /** A spinner grid on a clock the caller drives. */
  def spinnerGridAt(elapsed: FiniteDuration)(using theme: Theme): SpinnerGridElement =
    SpinnerGridElement(elapsed, w.SpinnerPreset.DotsRing, w.GridPhase.Diagonal(), theme.loading.spinner)

  /** A progress bar for work of unknown length, on the ambient [[AnimationClock]].
    *
    * Defaults to a bouncing segment — `.motion(...)` swaps in sweep, comet, or the quieter in-place pulse.
    */
  def indeterminateBar()(using theme: Theme, scope: ReactiveScope): IndeterminateElement =
    indeterminateBarAt(AnimationClock.elapsed)

  /** An indeterminate bar on a clock the caller drives. */
  def indeterminateBarAt(elapsed: FiniteDuration)(using theme: Theme): IndeterminateElement =
    IndeterminateElement(
      elapsed,
      w.IndeterminateMotion.Bounce,
      w.ProgressStyle.Line,
      theme.loading.track,
      theme.loading.fill,
      props = ElementProps(constraint = Some(Constraint.Length(1))),
    )

  /** A one-row determinate progress bar: a caption then a filled track.
    *
    * `ratio` is clamped to `[0, 1]`. The glyphs come from [[w.ProgressStyle]] — the default steps whole cells, and
    * `.preset(ProgressStyle.Blocks)` moves smoothly with sub-cell partials.
    */
  def progressBar(ratio: Double)(using theme: Theme): ProgressBarElement =
    ProgressBarElement(
      ratio,
      w.ProgressLabel.Percentage,
      w.ProgressStyle.Line,
      theme.loading.track,
      theme.loading.fill,
      theme.loading.fillRamp,
      ElementProps(constraint = Some(Constraint.Length(1))),
    )

  /** `progressBar` for counts rather than a fraction: `progressBar(3, 10)` is a 30% bar. */
  def progressBar(current: Int, total: Int)(using Theme): ProgressBarElement =
    progressBar(if total <= 0 then 0.0 else current.toDouble / total)

  def autocomplete(
      state: AutocompleteState,
      suggestions: Seq[String],
      onAccept: String => Unit = _ => (),
  ): AutocompleteElement =
    AutocompleteElement(state, suggestions, onAccept)

  def filePicker(state: FilePickerState): FilePickerElement =
    FilePickerElement(state)

  def radioGroup(options: Seq[String], selected: Signal[Int]): RadioGroupElement =
    RadioGroupElement(options, selected)

  def slider(value: Signal[Int], min: Int = 0, max: Int = 100, step: Int = 5): SliderElement =
    SliderElement(value, min, max, step)

  def selectionList(
      items: Seq[String],
      selected: Signal[Set[Int]],
      state: w.ListState,
  ): SelectionListElement =
    SelectionListElement(items, selected, state)

  def numberInput(state: w.TextInputState, allowDecimal: Boolean = false): NumberInputElement =
    NumberInputElement(state, allowDecimal)

  def maskedInput(state: w.TextInputState, mask: String): MaskedInputElement =
    MaskedInputElement(state, mask)

  def paginator(current: Signal[Int], total: Int): PaginatorElement =
    PaginatorElement(current, total)

  /** Scrolling ticker text, on the ambient [[AnimationClock]]. */
  def marquee(content: String)(using scope: ReactiveScope): WidgetElement =
    marqueeAt(content, AnimationClock.elapsed)

  /** A marquee on a clock the caller drives. */
  def marqueeAt(content: String, elapsed: FiniteDuration): WidgetElement =
    WidgetElement(
      w.Marquee(content, elapsed),
      ElementProps(constraint = Some(Constraint.Length(1))),
    )

  def image(source: w.Image): WidgetElement =
    WidgetElement(source)

  def link(label: String, url: String): WidgetElement =
    WidgetElement(
      w.Link(label, url),
      ElementProps(constraint = Some(Constraint.Length(1))),
    )

  def markdown(source: String): WidgetElement =
    WidgetElement(
      w.Markdown(source),
      measure = width => Some(w.Markdown.heightOf(source, width)),
    )

  def dataTable(
      table: w.DataTable,
      state: w.DataTableState,
  ): DataTableElement =
    DataTableElement(table, state)

  def directoryTree(state: w.DirectoryTreeState): DirectoryTreeElement =
    DirectoryTreeElement(state)

  def textArea(state: w.TextAreaState): TextAreaElement =
    TextAreaElement(state)

  def button(label: String)(action: => Unit): ButtonElement =
    ButtonElement(label, () => action)

  /** Later layers paint over earlier ones across the full area. */
  def layers(base: Element, overlays: Element*): LayersElement =
    LayersElement(base +: overlays)

  /** Picks a subtree from the terminal's size, re-evaluated on every resize.
    *
    * {{{
    * responsive {
    *   case size if size.width < 60 => column(header, tabbedContent(pages, active))
    *   case _                       => row(sidebar.percent(25), detail.fill)
    * }
    * }}}
    *
    * The size is the whole terminal's, the same one [[TuiApp.terminalSize]] reports — nesting this inside a `panel` or
    * a `splitPane` does not narrow what `build` sees. Branch on [[Breakpoint.of]] instead of raw columns when the named
    * bands say what you mean.
    */
  def responsive(build: Size => Element): ResponsiveElement =
    ResponsiveElement(build)

  def scrollView(
      content: Element,
      contentHeight: Int,
      state: w.ScrollViewState,
  ): ScrollViewElement =
    ScrollViewElement(content, contentHeight, state)

  /** Scroll view that measures its content's height itself (falls back to the viewport height when the content is
    * unmeasurable — fill-sized children).
    */
  def scrollView(content: Element, state: w.ScrollViewState): ScrollViewElement =
    ScrollViewElement(content, contentHeight = -1, state)

  /** `tabbedContent("One" -> pageOne, "Two" -> pageTwo)(selected)` — the selected page is picked at view construction,
    * so the tree always holds exactly the visible page.
    */
  def tabbedContent(pages: (String, Element)*)(selected: Signal[Int]): TabbedContentElement =
    val index  = math.max(0, math.min(selected.peek, pages.size - 1))
    val active = if pages.isEmpty then Element.text("") else pages(index)._2
    TabbedContentElement(pages.map(_._1), active, selected, pages.size)

  def collapsible(title: String, expanded: Signal[Boolean])(body: Element): CollapsibleElement =
    CollapsibleElement(title, body, expanded)

  def splitPane(
      first: Element,
      second: Element,
      splitPercent: Signal[Int],
      horizontal: Boolean = true,
  ): SplitPaneElement =
    SplitPaneElement(first, second, splitPercent, horizontal)

  def log(state: w.LogState): LogElement =
    LogElement(state)

  def rule(label: String = ""): WidgetElement =
    WidgetElement(
      w.Rule(Option(label).filter(_.nonEmpty)),
      ElementProps(constraint = Some(Constraint.Length(1))),
    )

  def bigText(content: String): WidgetElement =
    WidgetElement(
      w.BigText(content),
      ElementProps(constraint = Some(Constraint.Length(w.BigText.GlyphHeight))),
    )

  /** A menu / dropdown / context-menu popup over `items`; `onSelect` fires with the index chosen by Enter or a click.
    */
  def menu(items: Seq[w.MenuItem], state: w.MenuState)(onSelect: Int => Unit): MenuElement =
    MenuElement(items, state, onSelect)

  /** A bordered popup of help text sized to its content — overlay it near what it describes (see [[positioned]]). */
  def tooltip(text: String): WidgetElement =
    val tip = w.Tooltip(text)
    WidgetElement(tip, ElementProps(constraint = Some(Constraint.Length(tip.height))))

  /** Places `content` at an absolute `(dx, dy)` offset inside its area, sized `width` x `height`. */
  def positioned(dx: Int, dy: Int, width: Int, height: Int)(content: Element): PositionedElement =
    PositionedElement(dx, dy, width, height, content)
