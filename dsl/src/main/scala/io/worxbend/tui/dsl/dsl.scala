package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Color, Constraint, KeyEvent, MouseEvent, Style}
import io.worxbend.tui.widgets.BorderType

// One import to rule them all: `import io.worxbend.tui.dsl.*` brings in TuiApp, Element, every factory,
// the styling/layout extensions, and the core vocabulary the examples need (SPEC.md §5.1).
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
export io.worxbend.tui.core.{Color, Constraint, KeyCode, KeyEvent, KeyModifiers, MouseEvent, Style}
export io.worxbend.tui.runtime.{Computed, Easing, Effect, ReactiveScope, Signal, Tween}

/** Fluent styling — each call returns a new element (SPEC.md §5.2). */
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

  /** A handler returning `true` consumes the event; `false` lets it continue to the next candidate (bubbling, SPEC.md
    * §5.4).
    */
  def onKeyEvent(handler: KeyEvent => Boolean): Element =
    element.withProps(element.props.copy(onKey = Some(handler)))

  def onMouseEvent(handler: MouseEvent => Boolean): Element =
    element.withProps(element.props.copy(onMouse = Some(handler)))

  /** Opts a non-interactive element into the tab order (interactive elements are focusable by default). */
  def focusable: Element =
    element.withProps(element.props.copy(focusable = true))

/** Layout constraints — how much space the element claims inside its container (SPEC.md §5.2). */
extension (element: Element)

  def length(cells: Int): Element  = constrained(element, Constraint.Length(cells))
  def percent(pct: Int): Element   = constrained(element, Constraint.Percentage(pct))
  def fill: Element                = constrained(element, Constraint.Fill(1))
  def fill(weight: Int): Element   = constrained(element, Constraint.Fill(weight))
  def minSize(cells: Int): Element = constrained(element, Constraint.Min(cells))
  def maxSize(cells: Int): Element = constrained(element, Constraint.Max(cells))

private def constrained(element: Element, constraint: Constraint): Element =
  element.withProps(element.props.copy(constraint = Some(constraint)))
