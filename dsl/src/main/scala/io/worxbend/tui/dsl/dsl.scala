package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Color, Constraint, Flex, KeyEvent, MouseEvent, Span, Style}

// One import to rule them all: `import io.worxbend.tui.dsl.*` brings in TuiApp, Element, every factory,
// the styling/layout extensions, and the core vocabulary the examples need.
export Element.{
  autocomplete,
  badge,
  barChart,
  bigText,
  button,
  calendar,
  canvas,
  chart,
  collapsible,
  checkbox,
  dialog,
  dualSparkline,
  filePicker,
  spinner,
  spinnerAt,
  spinnerGrid,
  spinnerGridAt,
  animatedText,
  animatedTextAt,
  column,
  dataTable,
  directoryTree,
  gauge,
  heatmap,
  image,
  indeterminateBar,
  indeterminateBarAt,
  input,
  layers,
  line,
  linearSpinner,
  linearSpinnerAt,
  link,
  list,
  log,
  markdown,
  maskedInput,
  marquee,
  marqueeAt,
  menu,
  notice,
  numberInput,
  orbitSpinner,
  orbitSpinnerAt,
  paginator,
  panel,
  pieChart,
  progressBar,
  positioned,
  radioGroup,
  responsive,
  row,
  rule,
  scrollView,
  select,
  selectionList,
  skeleton,
  skeletonAt,
  slider,
  spacer,
  splitPane,
  stackedBarChart,
  sparkline,
  table,
  tabbedContent,
  tabs,
  text,
  textArea,
  toggle,
  tooltip,
  tree,
  widget,
}
// every core type the exported API's own signatures mention, so a view never needs a second import
export io.worxbend.tui.core.{
  Buffer,
  Color,
  Constraint,
  Direction,
  Easing,
  Effect,
  Flex,
  KeyCode,
  KeyEvent,
  Line,
  MouseEvent,
  MouseEventKind,
  Position,
  Rect,
  Size,
  Span,
  StatefulWidget,
  Style,
  Text,
  Tween,
  Widget,
}

/** The modifier bitset carried by [[KeyEvent]] and [[MouseEvent]].
  *
  * Written out as a type alias plus a value alias rather than added to the `export` above on purpose: an `export`ed
  * *opaque* type loses its companion's extension methods at the re-export site, so
  * `KeyModifiers.Ctrl | KeyModifiers.Shift` would not compile for an application that only wrote
  * `import io.worxbend.tui.dsl.*`. The explicit pair keeps `|`, `hasAny`, `hasAll`, `names` and `show` reachable.
  *
  * Any opaque type re-exported from this package in future (`core.Modifiers`, `widgets.Borders`) has to be spelled the
  * same way, for the same reason. Plain classes and enums are unaffected, which is why everything else is an `export`.
  */
type KeyModifiers = io.worxbend.tui.core.KeyModifiers
val KeyModifiers: io.worxbend.tui.core.KeyModifiers.type = io.worxbend.tui.core.KeyModifiers

export io.worxbend.tui.runtime.{Async, Cancelable, Computed, Derived, Reactive, ReactiveScope, Signal}
// the vocabulary types an application names directly: severities, animation and bar presets, badge variants
export io.worxbend.tui.widgets.{
  Alignment,
  BadgeVariant,
  BorderType,
  ColorRamp,
  GridPhase,
  IndeterminateMotion,
  LinearAxis,
  LinearFlow,
  LinearPath,
  LinearTrail,
  Language,
  MenuEntry,
  MenuState,
  NoticeLevel,
  OrbitPath,
  OrbitTrail,
  Overflow,
  Padding,
  ProgressLabel,
  ProgressStyle,
  SliderRange,
  SpinnerPreset,
  SyntaxHighlighter,
  SyntaxTheme,
  TextEffect,
}

/** The shape of an app's `view` (and any sub-view helper): a computation, run under a tracking [[ReactiveScope]], that
  * produces the current [[Element]] tree. Reading a `Signal` inside it subscribes the next redraw. Mirrors terminus's
  * `type Program[A] = Terminal ?=> A` — one named shape every view has.
  */
type View = ReactiveScope ?=> Element

/** A styled run of text: `"OK".styled(_.withFg(Color.Green))` is the [[Span]] the [[Element.line]] factory takes.
  *
  * A plain extension method rather than an implicit `Conversion`, so no call site needs a language import — the same
  * choice `Layout.apply`'s `Int | Double | Constraint` overload makes. It lives in this package, not in `tui-core`
  * beside `Span` itself, because an extension method is only found through an `import` of the scope that defines it:
  * declared in `core` it would need a second import at every view, which is precisely the ceremony
  * `import io.worxbend.tui.dsl.*` exists to remove.
  */
extension (content: String) def styled(transform: Style => Style): Span = Span(content, transform(Style.Default))

/** Alignment and inter-child spacing for the two containers that lay children out along an axis.
  *
  * Typed to [[FlexContainer]] rather than to every element: `text("x").center` used to compile and do nothing, which is
  * the kind of silence a fluent API should not have. Each call gives back the container's own type, so the builders
  * chain in any order.
  */
extension [E <: FlexContainer](container: E)

  /** How the space children leave over is distributed. Only bites when space is actually left over — a `.fill` child
    * takes it all, deliberately.
    */
  def flex(mode: Flex): container.Self = container.withFlex(mode)

  /** Extra blank cells inserted between neighbouring children. */
  def gap(cells: Int): container.Self = container.withSpacing(cells)

  def center: container.Self       = container.withFlex(Flex.Center)
  def spaceBetween: container.Self = container.withFlex(Flex.SpaceBetween)
  def spaceAround: container.Self  = container.withFlex(Flex.SpaceAround)
  def spaceEvenly: container.Self  = container.withFlex(Flex.SpaceEvenly)
  def flexEnd: container.Self      = container.withFlex(Flex.End)

/** Fluent styling — each call returns a new element of the same type, so the builders chain in any order. */
extension [E <: Element](element: E)

  def styled(transform: Style => Style): element.Self =
    element.withProps(element.props.copy(style = transform(element.props.style)))

  def bold: element.Self      = element.styled(_.bold)
  def dim: element.Self       = element.styled(_.dim)
  def italic: element.Self    = element.styled(_.italic)
  def underline: element.Self = element.styled(_.underline)
  def reverse: element.Self   = element.styled(_.reverse)

  /** Foreground colour. Named `fg`/`bg` to match `Style.withFg`/`withBg` and the vocabulary every other terminal
    * toolkit uses; the older `.color`/`.background` pair was a fourth spelling of the same two ideas.
    */
  def fg(c: Color): element.Self = element.styled(_.withFg(c))

  /** Background colour. */
  def bg(c: Color): element.Self = element.styled(_.withBg(c))

  /** A handler returning `true` consumes the event; `false` lets it continue to the next candidate.
    */
  def onKeyEvent(handler: KeyEvent => Boolean): element.Self =
    element.withProps(element.props.copy(onKey = Some(handler)))

  def onMouseEvent(handler: MouseEvent => Boolean): element.Self =
    element.withProps(element.props.copy(onMouse = Some(handler)))

  /** Opts a non-interactive element into the tab order (interactive elements are focusable by default). */
  def focusable: element.Self =
    element.withProps(element.props.copy(focusable = true))

  /** A stable focus identity: focus follows this key across renders even when the tree changes shape (without a key,
    * focus is positional and can jump when elements appear or disappear).
    */
  def key(name: String): element.Self =
    element.withProps(element.props.copy(focusKey = Some(name)))

/** Layout constraints — how much space the element claims inside its container. */
extension [E <: Element](element: E)

  def length(cells: Int): element.Self  = constrained(element, Constraint.Length(cells))
  def percent(pct: Int): element.Self   = constrained(element, Constraint.Percentage(pct))
  def fill: element.Self                = constrained(element, Constraint.Fill(1))
  def fill(weight: Int): element.Self   = constrained(element, Constraint.Fill(weight))
  def minSize(cells: Int): element.Self = constrained(element, Constraint.Min(cells))
  def maxSize(cells: Int): element.Self = constrained(element, Constraint.Max(cells))

private def constrained(element: Element, constraint: Constraint): element.Self =
  element.withProps(element.props.copy(constraint = Some(constraint)))
