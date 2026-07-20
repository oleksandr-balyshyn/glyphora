package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Color, Constraint, Flex, KeyEvent, MouseEvent, Style}
import io.worxbend.tui.widgets.BorderType

// One import to rule them all: `import io.worxbend.tui.dsl.*` brings in TuiApp, Element, every factory,
// the styling/layout extensions, and the core vocabulary the examples need.
export Element.{
  autocomplete,
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
  waveText,
  column,
  dataTable,
  directoryTree,
  gauge,
  heatmap,
  image,
  indeterminateBar,
  input,
  layers,
  link,
  list,
  log,
  markdown,
  maskedInput,
  marquee,
  numberInput,
  paginator,
  panel,
  pieChart,
  radioGroup,
  row,
  rule,
  scrollView,
  select,
  selectionList,
  skeleton,
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
  tree,
  widget,
}
export io.worxbend.tui.core.{Color, Constraint, Flex, KeyCode, KeyEvent, KeyModifiers, MouseEvent, Style}
export io.worxbend.tui.runtime.{Async, Cancelable, Computed, Easing, Effect, ReactiveScope, Signal, Tween}

/** The shape of an app's `view` (and any sub-view helper): a computation, run under a tracking [[ReactiveScope]], that
  * produces the current [[Element]] tree. Reading a `Signal` inside it subscribes the next redraw. Mirrors terminus's
  * `type Program[A] = Terminal ?=> A` — one named shape every view has.
  */
type View = ReactiveScope ?=> Element

/** Row/Column flex alignment and inter-child spacing. `flex`/`center`/`spaceBetween`/… only bite on `row`/`column`
  * containers whose children leave leftover space (no `fill` child); elsewhere they are the identity.
  */
extension (element: Element)

  def flex(mode: Flex): Element =
    element match
      case r: RowElement    => r.copy(flex = mode)
      case c: ColumnElement => c.copy(flex = mode)
      case other            => other

  /** Extra blank cells inserted between a row/column's children. */
  def gap(cells: Int): Element =
    element match
      case r: RowElement    => r.copy(spacing = math.max(0, cells))
      case c: ColumnElement => c.copy(spacing = math.max(0, cells))
      case other            => other

  def center: Element       = flex(Flex.Center)
  def spaceBetween: Element = flex(Flex.SpaceBetween)
  def spaceAround: Element  = flex(Flex.SpaceAround)
  def spaceEvenly: Element  = flex(Flex.SpaceEvenly)
  def flexEnd: Element      = flex(Flex.End)

/** Fluent styling — each call returns a new element. */
extension (element: Element)

  def styled(transform: Style => Style): Element =
    element.withProps(element.props.copy(style = transform(element.props.style)))

  def bold: Element                 = element.styled(_.bold)
  def dim: Element                  = element.styled(_.dim)
  def italic: Element               = element.styled(_.italic)
  def underline: Element            = element.styled(_.underline)
  def reverse: Element              = element.styled(_.reverse)
  def color(c: Color): Element      = element.styled(_.withFg(c))
  def background(c: Color): Element = element.styled(_.withBg(c))

  /** Rounded borders — meaningful on panels, identity elsewhere. */
  def rounded: Element =
    element match
      case panel: PanelElement => panel.copy(borderType = BorderType.Rounded)
      case other               => other

  /** Double-line borders — meaningful on panels, identity elsewhere. */
  def doubleBorder: Element =
    element match
      case panel: PanelElement => panel.copy(borderType = BorderType.Double)
      case other               => other

  /** A handler returning `true` consumes the event; `false` lets it continue to the next candidate.
    */
  def onKeyEvent(handler: KeyEvent => Boolean): Element =
    element.withProps(element.props.copy(onKey = Some(handler)))

  def onMouseEvent(handler: MouseEvent => Boolean): Element =
    element.withProps(element.props.copy(onMouse = Some(handler)))

  /** Opts a non-interactive element into the tab order (interactive elements are focusable by default). */
  def focusable: Element =
    element.withProps(element.props.copy(focusable = true))

  /** A stable focus identity: focus follows this key across renders even when the tree changes shape (without a key,
    * focus is positional and can jump when elements appear or disappear).
    */
  def key(name: String): Element =
    element.withProps(element.props.copy(focusKey = Some(name)))

/** Layout constraints — how much space the element claims inside its container. */
extension (element: Element)

  def length(cells: Int): Element  = constrained(element, Constraint.Length(cells))
  def percent(pct: Int): Element   = constrained(element, Constraint.Percentage(pct))
  def fill: Element                = constrained(element, Constraint.Fill(1))
  def fill(weight: Int): Element   = constrained(element, Constraint.Fill(weight))
  def minSize(cells: Int): Element = constrained(element, Constraint.Min(cells))
  def maxSize(cells: Int): Element = constrained(element, Constraint.Max(cells))

private def constrained(element: Element, constraint: Constraint): Element =
  element.withProps(element.props.copy(constraint = Some(constraint)))
