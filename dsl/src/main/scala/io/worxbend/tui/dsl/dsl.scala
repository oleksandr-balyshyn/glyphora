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
  clear,
  confirmDialog,
  dialog,
  dualSparkline,
  each,
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
  dropdown,
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
  portal,
  positioned,
  radioGroup,
  responsive,
  row,
  rule,
  scrollbar,
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
  * [[Borders]] below is the third opaque type in the library and is spelled the same way for the same reason.
  */
type Modifiers = io.worxbend.tui.core.Modifiers
val Modifiers: io.worxbend.tui.core.Modifiers.type = io.worxbend.tui.core.Modifiers

/** Which sides of a `panel`'s frame are drawn — `Borders.Top | Borders.Left`, `Borders.All`, `Borders.None`.
  *
  * Named by `PanelElement.borders(sides)`, so an application that wants a half-framed pane has to be able to write the
  * value. It used to be reachable only through `widgets.Block`, which meant a second import for one bitset.
  *
  * The third opaque type in the library, and written as a type alias plus a value alias rather than an `export` for the
  * reason given on [[KeyModifiers]]: an exported opaque type loses its companion's extension methods, and without `|`
  * the sides could not be combined at all.
  */
type Borders = io.worxbend.tui.widgets.Borders
val Borders: io.worxbend.tui.widgets.Borders.type = io.worxbend.tui.widgets.Borders

/** `key"ctrl+s"` — a key spec the compiler checks, giving back the [[KeyEvent]] it names.
  *
  * The same value `KeyEvent.parse("ctrl+s")` produces, except that a spec the parser rejects — `ctlr+s`, a key name
  * that does not exist, a Ctrl combination no terminal can deliver — stops the build with the parser's own message
  * rather than throwing when the binding is first declared. Pass it wherever a `KeyEvent` is taken:
  * `.onKey(key"ctrl+s") { … }` on an element, or `binding(key"ctrl+s", "ctrl+s", "save") { … }`.
  *
  * Written out here rather than `export`ed for a mechanical reason: an extension method on `StringContext` cannot be
  * re-exported, so the one-import promise needs the forwarder. Its body is the same macro call `tui-core` declares, so
  * there is one implementation and not two. Only a literal can be checked; a spec assembled at run time keeps going
  * through [[KeyEvent.parse]].
  */
extension (inline sc: StringContext)
  inline def key(inline args: Any*): KeyEvent = ${ io.worxbend.tui.core.KeySpecLiteral.expand('sc, 'args) }

// `AsyncErrorHandler` and `QueuedTaskFailures` are here for the same reason as everything else in this block: they are
// named by signatures this package already exports. `Async.run`'s `onError` parameter takes an `AsyncErrorHandler`, and
// installing a `given` one is the documented way to decide where a background failure is reported;
// `RunnerError.QueuedTask(failures)` and `RunnerError.Backend(_, queuedTasks)` both carry a `QueuedTaskFailures`, so an
// app that pattern-matches the error `run()` hands back has to be able to name the payload. `CompletedFrame` is here for
// the same reason: an app that installs `RunnerConfig(onFrame = ...)` writes the type of that lambda's parameter in its
// own source.
export io.worxbend.tui.runtime.{
  Async,
  AsyncErrorHandler,
  Cancelable,
  CompletedFrame,
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
  Viewport,
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
  CanvasResolution,
  ColorRamp,
  ColumnSort,
  DataTable,
  DataTableState,
  Dataset,
  DirectoryTreeState,
  DropdownState,
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
  Marker,
  MarkdownTheme,
  MenuEntry,
  MenuState,
  MergeStrategy,
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

  /** Slow blink. Widely ignored, and disabled outright in some terminals and by some accessibility settings, so never
    * carry meaning in it alone — pair it with a colour or a word.
    */
  def blink: element.Self = element.styled(_.blink)

  /** Paints the text in the background colour, so it occupies its cells but cannot be read — a password field's
    * masking, a spoiler. It is *not* a security measure: the characters are still in the terminal's buffer and are
    * still copied by a selection.
    */
  def hidden: element.Self = element.styled(_.hidden)

  /** A line struck through the text — a removed line in a diff, a completed to-do. */
  def crossedOut: element.Self = element.styled(_.crossedOut)

  /** Turning an attribute *off*, which is not the same as never turning it on.
    *
    * A `Style` records what it was asked to clear as well as what it was asked to set, so `.notBold` on a child of a
    * bold container removes the bold rather than being ignored the way a `Style.Default` would be. Without these, an
    * element inside a styled ancestor had no way to opt out of one attribute while keeping the rest.
    */
  def notBold: element.Self       = element.styled(_.notBold)
  def notDim: element.Self        = element.styled(_.notDim)
  def notItalic: element.Self     = element.styled(_.notItalic)
  def notUnderline: element.Self  = element.styled(_.notUnderline)
  def notReverse: element.Self    = element.styled(_.notReverse)
  def notBlink: element.Self      = element.styled(_.notBlink)
  def notHidden: element.Self     = element.styled(_.notHidden)
  def notCrossedOut: element.Self = element.styled(_.notCrossedOut)

  /** Back to the terminal's own default foreground colour, whatever the surrounding elements set. The counterpart of
    * [[fg]], and the colour equivalent of the `not*` builders above.
    */
  def withoutFg: element.Self = element.styled(_.withoutFg)

  /** Back to the terminal's own default background colour. */
  def withoutBg: element.Self = element.styled(_.withoutBg)

  /** Foreground colour. Named `fg`/`bg` to match `Style.withFg`/`withBg` and the vocabulary every other terminal
    * toolkit uses; the older `.color`/`.background` pair was a fourth spelling of the same two ideas.
    */
  def fg(c: Color): element.Self = element.styled(_.withFg(c))

  /** Background colour. */
  def bg(c: Color): element.Self = element.styled(_.withBg(c))

  /** A handler returning `true` consumes the event; `false` lets it continue to the next candidate.
    *
    * Handlers compose rather than replace one another: the most recently added runs first, and its `false` falls
    * through to whatever was already on the element. That is what lets a helper hand back an element with a binding on
    * it and a call site add a second binding without silently erasing the first — which is what a plain overwrite did,
    * with no error and no warning to say so. `onKey` in [[Grammar]] has always composed this way; this is the raw
    * builder catching up with it.
    */
  def onKeyEvent(handler: KeyEvent => Boolean): element.Self =
    val previous = element.props.onKey
    element.withProps(element.props.copy(onKey = Some(event => handler(event) || previous.exists(_(event)))))

  /** A mouse handler, composing with any already on the element exactly as [[onKeyEvent]] does. */
  def onMouseEvent(handler: MouseEvent => Boolean): element.Self =
    val previous = element.props.onMouse
    element.withProps(element.props.copy(onMouse = Some(event => handler(event) || previous.exists(_(event)))))

  /** Opts a non-interactive element into the tab order (interactive elements are focusable by default). */
  def focusable: element.Self =
    element.withProps(element.props.copy(focusable = true))

  /** Asks for the keyboard when this element first appears — a search box on a screen that opens, the first field of a
    * form, the default button of a dialog. Without it, focus starts on whichever focusable happens to come first in the
    * tab order, and an app that wanted otherwise had to reach into the framework's own focus bookkeeping, which is not
    * public.
    *
    * It fires *once*, on the frame the element appears in, and never again while it stays in the tree: an element that
    * re-claimed focus every frame would make Tab useless, because the next render would take the keyboard straight
    * back. It also opts the element into the tab order, the way `.focusable` does, so it works on a non-interactive
    * element too.
    *
    * Pair it with [[key]]. "Has this element appeared?" is answered by the focus key when there is one and by the
    * element's position in the tab order otherwise, so an unkeyed autofocusing element that *moves* — because something
    * above it appeared — reads as a new element and claims focus a second time.
    *
    * If two elements ask at once, the first in the tab order wins.
    */
  def autofocus: element.Self =
    element.withProps(element.props.copy(focusable = true, autofocus = true))

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
