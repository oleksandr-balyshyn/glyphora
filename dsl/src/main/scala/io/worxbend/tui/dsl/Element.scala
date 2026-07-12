package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Constraint, KeyCode, KeyEvent, KeyModifiers, Line, Style, Text, Widget}
import io.worxbend.tui.runtime.Signal
import io.worxbend.tui.widgets as w

/** A node of the retained-mode UI tree (SPEC.md §5.1).
  *
  * Every element ultimately renders through a `tui-core` [[Widget]] — the DSL is a declarative layer over
  * `tui-widgets`, never a parallel rendering path. The sealed hierarchy is plain data: construction tests can
  * pattern-match it, and styling extensions rebuild nodes instead of mutating them.
  */
sealed trait Element:
  def props: ElementProps
  def style: Style = props.style
  def widget: Widget
  def children: Seq[Element] = Seq.empty
  private[dsl] def withProps(props: ElementProps): Element

  /** Containers rebuild with transformed children during the focus pass; leaves ignore it. */
  private[dsl] def withChildren(children: Seq[Element]): Element = this

  /** Framework behavior an interactive element performs when focused (editing, toggling, cycling) — runs
    * after the user's own `onKeyEvent` handler declined the event.
    */
  private[dsl] def builtinKeyHandler: Option[KeyEvent => Boolean] = None

  /** The space this element claims inside a container when the user set nothing explicit. */
  private[dsl] def defaultConstraint: Constraint = Constraint.Fill(1)

  private[dsl] final def layoutItem: w.LayoutItem =
    w.LayoutItem(props.constraint.getOrElse(defaultConstraint), widget)

final case class TextElement(content: String, props: ElementProps = ElementProps()) extends Element:
  def widget: Widget = w.Paragraph(Text.styled(content, props.style))
  private[dsl] def withProps(props: ElementProps): TextElement = copy(props = props)
  private[dsl] override def defaultConstraint: Constraint =
    Constraint.Length(content.split("\n", -1).length)

final case class PanelElement(
    title: Option[String],
    override val children: Seq[Element],
    borderType: w.BorderType = w.BorderType.Plain,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget =
    (area, buffer) =>
      val block = w.Block(title.map(text => Line.styled(text, props.style)), borderType, props.style)
      block.render(area, buffer)
      w.Column(children.map(_.layoutItem)).render(block.inner(area), buffer)
  private[dsl] def withProps(props: ElementProps): PanelElement = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): PanelElement = copy(children = children)

final case class RowElement(
    override val children: Seq[Element],
    spacing: Int = 0,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget = w.Row(children.map(_.layoutItem), spacing)
  private[dsl] def withProps(props: ElementProps): RowElement = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): RowElement = copy(children = children)

final case class ColumnElement(
    override val children: Seq[Element],
    spacing: Int = 0,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget = w.Column(children.map(_.layoutItem), spacing)
  private[dsl] def withProps(props: ElementProps): ColumnElement = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): ColumnElement = copy(children = children)

final case class SpacerElement(props: ElementProps = ElementProps()) extends Element:
  def widget: Widget = w.Spacer
  private[dsl] def withProps(props: ElementProps): SpacerElement = copy(props = props)

final case class GaugeElement(
    ratio: Double,
    label: Option[String] = None,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget = w.Gauge(ratio, label, props.style, filledStyle = props.style.reverse)
  private[dsl] def withProps(props: ElementProps): GaugeElement = copy(props = props)
  private[dsl] override def defaultConstraint: Constraint = Constraint.Length(1)

final case class SparklineElement(
    data: Seq[Long],
    max: Option[Long] = None,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget = w.Sparkline(data, max, props.style)
  private[dsl] def withProps(props: ElementProps): SparklineElement = copy(props = props)
  private[dsl] override def defaultConstraint: Constraint = Constraint.Length(1)

final case class TabsElement(
    titles: Seq[String],
    selected: Int = 0,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget = w.Tabs(titles.map(Line.raw), selected, props.style)
  private[dsl] def withProps(props: ElementProps): TabsElement = copy(props = props)
  private[dsl] override def defaultConstraint: Constraint = Constraint.Length(1)

final case class TableElement(
    rows: Seq[Seq[String]],
    widths: Seq[Constraint],
    header: Option[Seq[String]] = None,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget =
    w.Table(rows.map(_.map(Line.raw)), widths, header.map(_.map(Line.raw)), style = props.style)
  private[dsl] def withProps(props: ElementProps): TableElement = copy(props = props)

/** Escape hatch: any core [[Widget]] as a leaf element (its rendering ignores the element style). */
final case class WidgetElement(wrapped: Widget, props: ElementProps = ElementProps()) extends Element:
  def widget: Widget = wrapped
  private[dsl] def withProps(props: ElementProps): WidgetElement = copy(props = props)

// ---- interactive (focusable) elements ----

/** Single-line text input. Editing state (value + cursor) is app-owned; editing keys are handled by the
  * built-in handler while focused, and any consumed key triggers a redraw.
  */
final case class InputElement(
    state: w.TextInputState,
    placeholder: String = "",
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget =
    val input = w.TextInput(placeholder, showCursor = props.focused, style = props.style)
    (area, buffer) => input.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): InputElement = copy(props = props)
  private[dsl] override def defaultConstraint: Constraint = Constraint.Length(1)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean] = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    if !props.focused then false
    else
      event.code match
        case KeyCode.Char(c) if event.modifiers.isEmpty || event.modifiers == KeyModifiers.Shift =>
          state.insert(c.toString)
          true
        case KeyCode.Backspace =>
          state.backspace()
          true
        case KeyCode.Delete =>
          state.delete()
          true
        case KeyCode.Left =>
          state.moveLeft()
          true
        case KeyCode.Right =>
          state.moveRight()
          true
        case KeyCode.Home =>
          state.moveHome()
          true
        case KeyCode.End =>
          state.moveEnd()
          true
        case _ => false

final case class CheckboxElement(
    label: String,
    checked: Signal[Boolean],
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget = w.Checkbox(label, checked.peek, focusStyled(props))
  private[dsl] def withProps(props: ElementProps): CheckboxElement = copy(props = props)
  private[dsl] override def defaultConstraint: Constraint = Constraint.Length(1)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean] =
    Some(toggleOnActivate(props, () => checked.update(value => !value)))

final case class ToggleElement(
    label: String,
    on: Signal[Boolean],
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget = w.Toggle(label, on.peek, focusStyled(props))
  private[dsl] def withProps(props: ElementProps): ToggleElement = copy(props = props)
  private[dsl] override def defaultConstraint: Constraint = Constraint.Length(1)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean] =
    Some(toggleOnActivate(props, () => on.update(value => !value)))

final case class SelectElement(
    options: Seq[String],
    selected: Signal[Int],
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget = w.Select(options, selected.peek, focusStyled(props))
  private[dsl] def withProps(props: ElementProps): SelectElement = copy(props = props)
  private[dsl] override def defaultConstraint: Constraint = Constraint.Length(1)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean] = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    if !props.focused || options.isEmpty then false
    else
      event.code match
        case KeyCode.Left =>
          selected.update(index => (index - 1 + options.size) % options.size)
          true
        case KeyCode.Right =>
          selected.update(index => (index + 1) % options.size)
          true
        case _ => false

final case class ListElement(
    items: Seq[String],
    state: w.ListState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget =
    // no whole-body focus styling: the selection highlight is the focus cue for scrollable widgets
    val view = w.ListView(items.map(Line.raw), style = props.style)
    (area, buffer) => view.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): ListElement = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean] = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    if !props.focused then false
    else
      event.code match
        case KeyCode.Down =>
          state.selectNext(items.size)
          true
        case KeyCode.Up =>
          state.selectPrevious(items.size)
          true
        case _ => false

final case class TreeElement(
    nodes: Seq[w.TreeNode],
    state: w.TreeState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget =
    // no whole-body focus styling: the selection highlight is the focus cue for scrollable widgets
    val tree = w.Tree(nodes, style = props.style)
    (area, buffer) => tree.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): TreeElement = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean] = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    if !props.focused then false
    else
      event.code match
        case KeyCode.Down =>
          state.selectNext(nodes)
          true
        case KeyCode.Up =>
          state.selectPrevious(nodes)
          true
        case KeyCode.Enter =>
          state.toggle(nodes)
          true
        case _ => false

/** Wraps a focusable element during the focus pass so its rendered area is recorded for click-to-focus
  * hit-testing. Transparent for everything else: props, children, and handlers delegate to the wrapped node.
  */
private[dsl] final case class TrackedElement(inner: Element, index: Int, tracker: FocusTracker) extends Element:
  def props: ElementProps = inner.props
  override def children: Seq[Element] = inner.children
  def widget: Widget =
    (area, buffer) =>
      tracker.record(index, area)
      inner.widget.render(area, buffer)
  private[dsl] def withProps(props: ElementProps): TrackedElement = copy(inner = inner.withProps(props))
  private[dsl] override def withChildren(children: Seq[Element]): TrackedElement =
    copy(inner = inner.withChildren(children))
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean] = inner.builtinKeyHandler
  private[dsl] override def defaultConstraint: Constraint = inner.defaultConstraint

/** Space/Enter activates a focused two-state control. */
private def toggleOnActivate(props: ElementProps, activate: () => Unit): KeyEvent => Boolean =
  event =>
    if !props.focused then false
    else
      event.code match
        case KeyCode.Char(' ') | KeyCode.Enter =>
          activate()
          true
        case _ => false

/** Focused interactive elements render reversed so the user can see where keystrokes go. */
private def focusStyled(props: ElementProps): Style =
  if props.focused then props.style.reverse else props.style

/** The factory set (TamboUI-Toolkit-style, `RESEARCH.md`): one obvious home, re-exported at the package top
  * level so `import io.worxbend.tui.dsl.*` brings every factory in.
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

  // parameter types fully qualified: these factories are re-exported at the package top level alongside
  // re-exports of the core types themselves, and unqualified names here would form a resolution cycle
  def table(rows: Seq[Seq[String]], widths: io.worxbend.tui.core.Constraint*): TableElement =
    TableElement(rows, widths)

  def widget(wrapped: io.worxbend.tui.core.Widget): WidgetElement = WidgetElement(wrapped)

  def input(state: io.worxbend.tui.widgets.TextInputState, placeholder: String = ""): InputElement =
    InputElement(state, placeholder)

  def checkbox(label: String, checked: io.worxbend.tui.runtime.Signal[Boolean]): CheckboxElement =
    CheckboxElement(label, checked)

  def toggle(label: String, on: io.worxbend.tui.runtime.Signal[Boolean]): ToggleElement =
    ToggleElement(label, on)

  def select(options: Seq[String], selected: io.worxbend.tui.runtime.Signal[Int]): SelectElement =
    SelectElement(options, selected)

  def list(items: Seq[String], state: io.worxbend.tui.widgets.ListState): ListElement =
    ListElement(items, state)

  def tree(nodes: Seq[io.worxbend.tui.widgets.TreeNode], state: io.worxbend.tui.widgets.TreeState): TreeElement =
    TreeElement(nodes, state)
