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
  horizontalBarChart,
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
// Every core type the exported API's own signatures mention, so a view never needs a second import.
//
// The rule this block and the two below are kept to: if a name appears in the *signature* of anything this package
// exports, it is re-exported here. The regression test is the examples directory — nine of the ten example apps take
// `io.worxbend.tui.dsl.*` and nothing else from glyphora, and the tenth adds only
// `io.worxbend.tui.macros.{deriveForm, Field}`, a genuinely separate module a form-less app never touches. An example
// that needs a second glyphora import means this list is short.
//
// `core.Progress` is the deliberate omission from the motion group (`Easing`, `Effect`, `Spring`, `Tween`): it is the
// shared time-to-position arithmetic those four and the animated widgets are built *from*, so it belongs to whoever
// writes a widget, not to whoever writes a view. Widget authors already import `io.worxbend.tui.core`.
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
  Masked,
  MediaKey,
  ModifierKey,
  MouseButton,
  MouseEvent,
  MouseEventKind,
  Position,
  Rect,
  Size,
  Span,
  Spring,
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
  * [[Modifiers]] below is spelled the same way for the same reason. Plain classes and enums are unaffected, which is
  * why everything else here is an `export`.
  */
type KeyModifiers = io.worxbend.tui.core.KeyModifiers
val KeyModifiers: io.worxbend.tui.core.KeyModifiers.type = io.worxbend.tui.core.KeyModifiers

/** The text-attribute bitset carried by [[Style]] — bold, dim, italic, underline, reverse, and the rest.
  *
  * Named by the exported API's own signatures (`Style.modifiers` returns one, `Style.without(flags: Modifiers)` takes
  * one), so leaving it out would make those two members unusable from a `dsl`-only import. Written as a type alias plus
  * a value alias, not an `export`, for the reason given on [[KeyModifiers]]: it is an opaque type, and an exported
  * opaque type loses its companion's extension methods (`|`, `hasAny`, `hasAll`, `names`, `show`).
  *
  * `widgets.Borders` is the third opaque type in the library and is deliberately *not* re-exported: no signature the
  * DSL exposes names it — per-side borders are a `tui-widgets`-level knob on `Block`, and `panel` exposes border
  * *style* (`BorderType`) instead. An application that reaches for `Block` directly is already importing
  * `io.worxbend.tui.widgets` for `Block` itself.
  */
type Modifiers = io.worxbend.tui.core.Modifiers
val Modifiers: io.worxbend.tui.core.Modifiers.type = io.worxbend.tui.core.Modifiers

// `AsyncErrorHandler` and `QueuedTaskFailures` are here for the same reason as everything else in this block: they are
// named by signatures this package already exports. `Async.run`'s `onError` parameter takes an `AsyncErrorHandler`, and
// installing a `given` one is the documented way to decide where a background failure is reported;
// `RunnerError.QueuedTask(failures)` and `RunnerError.Backend(_, queuedTasks)` both carry a `QueuedTaskFailures`, so an
// app that pattern-matches the error `run()` hands back has to be able to name the payload.
export io.worxbend.tui.runtime.{
  Async,
  AsyncErrorHandler,
  Cancelable,
  Computed,
  Derived,
  QueuedTaskFailures,
  Reactive,
  ReactiveScope,
  RenderTaskErrorHandler,
  RenderThread,
  RunnerConfig,
  RunnerError,
  Signal,
}
// The terminal vocabulary [[TuiApp]]'s own lifecycle seams are typed in: `runWith` takes a `Backend`, `createBackend`
// returns `Either[BackendError, Backend]`, and `colorDepth` returns a `ColorDepth`. Overriding either seam — the
// documented way to draw on something other than JLine's controlling terminal, or to pin a palette — is a supported
// thing for an application to do, so the names those overrides have to write belong here rather than behind a second
// import. `JLine3Backend` and `HeadlessBackend` are deliberately left out: neither is named by a signature this
// package exposes, and the code that constructs one (a `main` wiring a custom terminal, a test wiring `Pilot`) is
// already reaching into `tui-terminal` or `tui-test` on purpose.
export io.worxbend.tui.terminal.{Backend, BackendError, ColorDepth}
// The widget-level vocabulary an application names directly, in two groups:
//   * the enums and presets a call site passes by name (severities, animation presets, badge variants), and
//   * every caller-owned `*State` and content value a DSL factory *requires* — `list(items, state)` cannot be called
//     without naming `ListState`, so omitting it from here would break the one-import promise above outright.
export io.worxbend.tui.widgets.{
  Alignment,
  BadgeVariant,
  BlockTitle,
  BorderType,
  ColorRamp,
  ColumnSort,
  DataTable,
  DataTableState,
  Dataset,
  DirectoryTreeState,
  GraphType,
  GridPhase,
  HighlightSpacing,
  Image,
  IndeterminateMotion,
  LinearAxis,
  LinearFlow,
  LinearPath,
  LinearTrail,
  Language,
  ListDirection,
  ListState,
  LogState,
  MarkdownTheme,
  MenuEntry,
  MenuState,
  NoticeLevel,
  OrbitPath,
  OrbitTrail,
  Overflow,
  Padding,
  Paging,
  Painter,
  ProgressLabel,
  ProgressPreset,
  ScrollViewState,
  Shape,
  SliderRange,
  SortDirection,
  SparkDirection,
  SpinnerPreset,
  SyntaxHighlighter,
  SyntaxTheme,
  TextAreaState,
  TextEffect,
  TextInputState,
  TitlePosition,
  TreeNode,
  TreeState,
}

/** The shape of an app's `view` (and any sub-view helper): a computation, run under a tracking [[ReactiveScope]] *and*
  * the app's [[Theme]], that produces the current [[Element]] tree. Reading a `Signal` inside it subscribes the next
  * redraw. Mirrors terminus's `type Program[A] = Terminal ?=> A` — one named shape every view has.
  *
  * The theme is part of the shape rather than something `TuiApp` installs around the call, because a given installed
  * around the call is not in scope *inside* the body: an app that overrode `theme` and then wrote `statusBar(bindings)`
  * in its own `view` used to silently resolve against `Theme.Dark`. Carrying it in the type means the compiler hands
  * every themed helper the app's own theme, wherever the helper is written.
  */
type View = (ReactiveScope, Theme) ?=> Element

/** A styled run of text: `"OK".styled(_.withFg(Color.Green))` is the [[Span]] the [[Element.line]] factory takes.
  *
  * A plain extension method rather than an implicit `Conversion`, so no call site needs a language import — the same
  * choice `Layout.apply`'s `Int | Double | Constraint` overload makes. It lives in this package, not in `tui-core`
  * beside `Span` itself, because an extension method is only found through an `import` of the scope that defines it:
  * declared in `core` it would need a second import at every view, which is precisely the ceremony
  * `import io.worxbend.tui.dsl.*` exists to remove.
  */
extension (content: String) def styled(transform: Style => Style): Span = Span(content, transform(Style.Default))

/** Alignment and inter-child spacing for the containers that lay children out along an axis — `row`, `column`, and
  * `panel`, which stacks its children with the same widget `column` does.
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

  /** An exact fraction of the container: `.ratio(1, 3)` claims a third. The whole-number companion to [[percent]], for
    * the splits a percentage cannot say without truncating — `Layout`'s own advice for exact thirds.
    */
  def ratio(numerator: Int, denominator: Int): element.Self =
    constrained(element, Constraint.Ratio(numerator, denominator))

private def constrained(element: Element, constraint: Constraint): element.Self =
  element.withProps(element.props.copy(constraint = Some(constraint)))
