package io.worxbend.tui.dsl

import io.worxbend.tui.core.{
  CharWidth,
  Constraint,
  Direction,
  KeyCode,
  KeyEvent,
  KeyModifiers,
  Line,
  Style,
  Text,
  Widget,
}
import io.worxbend.tui.runtime.Signal
import io.worxbend.tui.widgets as w

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
  private[dsl] def builtinMouseHandler
      : Option[(io.worxbend.tui.core.MouseEvent, io.worxbend.tui.core.Rect) => Boolean] =
    None

  /** Framework paste behavior: how a bracketed paste lands in this element while focused. */
  private[dsl] def builtinPasteHandler: Option[String => Boolean] = None

  /** The space this element claims along `direction` inside a container when the user set nothing explicit — one-row
    * widgets claim one row vertically but their natural width (or a fill) horizontally.
    */
  private[dsl] def preferredSize(direction: Direction): Constraint =
    val _ = direction
    Constraint.Fill(1)

  /** The rows this element needs at `width`, when knowable — the measurement pass scroll views and
    * auto-sizing containers use. Defaults to a fixed vertical preferred size; content-driven elements
    * override it.
    */
  private[dsl] def intrinsicHeight(width: Int): Option[Int] =
    val _ = width
    props.constraint.getOrElse(preferredSize(Direction.Vertical)) match
      case Constraint.Length(cells) => Some(cells)
      case _                        => None

  private[dsl] final def layoutItem(direction: Direction): w.LayoutItem =
    w.LayoutItem(props.constraint.getOrElse(preferredSize(direction)), widget)

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
  private[dsl] override def intrinsicHeight(width: Int): Option[Int] =
    val heights = children.map(_.intrinsicHeight(math.max(0, width - 2)))
    if heights.forall(_.nonEmpty) then Some(heights.flatten.sum + 2) else None

final case class RowElement(
    override val children: Seq[Element],
    spacing: Int = 0,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget = w.Row(children.map(_.layoutItem(Direction.Horizontal)), spacing)
  private[dsl] def withProps(props: ElementProps): RowElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): RowElement = copy(children = children)
  private[dsl] override def intrinsicHeight(width: Int): Option[Int] =
    val heights = children.map(_.intrinsicHeight(width))
    if heights.forall(_.nonEmpty) then heights.flatten.maxOption else None

final case class ColumnElement(
    override val children: Seq[Element],
    spacing: Int = 0,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget = w.Column(children.map(_.layoutItem(Direction.Vertical)), spacing)
  private[dsl] override def intrinsicHeight(width: Int): Option[Int] =
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
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    direction match
      case Direction.Vertical   => Constraint.Length(1)
      case Direction.Horizontal => Constraint.Fill(1)

final case class SparklineElement(
    data: Seq[Long],
    max: Option[Long] = None,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget                                                        = w.Sparkline(data, max, props.style)
  private[dsl] def withProps(props: ElementProps): SparklineElement         = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    direction match
      case Direction.Vertical   => Constraint.Length(1)
      case Direction.Horizontal => Constraint.Fill(1)

final case class TabsElement(
    titles: Seq[String],
    selected: Int = 0,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget                                           = w.Tabs(titles.map(Line.raw), selected, props.style)
  private[dsl] def withProps(props: ElementProps): TabsElement = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    direction match
      case Direction.Vertical   => Constraint.Length(1)
      case Direction.Horizontal => Constraint.Fill(1)

final case class TableElement(
    rows: Seq[Seq[String]],
    widths: Seq[Constraint],
    header: Option[Seq[String]] = None,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget                                            =
    w.Table(rows.map(_.map(Line.raw)), widths, header.map(_.map(Line.raw)), style = props.style)
  private[dsl] def withProps(props: ElementProps): TableElement = copy(props = props)

/** Escape hatch: any core [[Widget]] as a leaf element (its rendering ignores the element style). `measure`
  * lets width-dependent content (wrapped markdown, images) report its height to the measurement pass.
  */
final case class WidgetElement(
    wrapped: Widget,
    props: ElementProps = ElementProps(),
    measure: Int => Option[Int] = _ => None,
) extends Element:
  def widget: Widget                                             = wrapped
  private[dsl] def withProps(props: ElementProps): WidgetElement = copy(props = props)
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
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    direction match
      case Direction.Vertical   => Constraint.Length(1)
      case Direction.Horizontal => Constraint.Fill(1)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]  = Some(handleKey)
  private[dsl] override def builtinPasteHandler: Option[String => Boolean]  = Some { text =>
    if props.focused then
      state.insert(text.replace("\r", "").replace("\n", " ")) // single-line input: fold newlines to spaces
      true
    else false
  }

  private def handleKey(event: KeyEvent): Boolean =
    if !props.focused then false
    else
      event.code match
        case KeyCode.Char(c) if event.modifiers.isEmpty || event.modifiers == KeyModifiers.Shift =>
          state.insert(c.toString)
          true
        case KeyCode.Backspace                                                                   =>
          state.backspace()
          true
        case KeyCode.Delete                                                                      =>
          state.delete()
          true
        case KeyCode.Left                                                                        =>
          state.moveLeft()
          true
        case KeyCode.Right                                                                       =>
          state.moveRight()
          true
        case KeyCode.Home                                                                        =>
          state.moveHome()
          true
        case KeyCode.End                                                                         =>
          state.moveEnd()
          true
        case _                                                                                   => false

final case class CheckboxElement(
    label: String,
    checked: Signal[Boolean],
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget                                               = w.Checkbox(label, checked.peek, focusStyled(props))
  private[dsl] def withProps(props: ElementProps): CheckboxElement = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    direction match
      case Direction.Vertical   => Constraint.Length(1)
      case Direction.Horizontal => Constraint.Fill(1)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]  =
    Some(toggleOnActivate(props, () => checked.update(value => !value)))
  private[dsl] override def builtinMouseHandler
      : Option[(io.worxbend.tui.core.MouseEvent, io.worxbend.tui.core.Rect) => Boolean] =
    Some(clickActivates(() => checked.update(value => !value)))

final case class ToggleElement(
    label: String,
    on: Signal[Boolean],
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget                                             = w.Toggle(label, on.peek, focusStyled(props))
  private[dsl] def withProps(props: ElementProps): ToggleElement = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    direction match
      case Direction.Vertical   => Constraint.Length(1)
      case Direction.Horizontal => Constraint.Fill(1)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]  =
    Some(toggleOnActivate(props, () => on.update(value => !value)))
  private[dsl] override def builtinMouseHandler
      : Option[(io.worxbend.tui.core.MouseEvent, io.worxbend.tui.core.Rect) => Boolean] =
    Some(clickActivates(() => on.update(value => !value)))

final case class SelectElement(
    options: Seq[String],
    selected: Signal[Int],
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  private[dsl] override def builtinMouseHandler
      : Option[(io.worxbend.tui.core.MouseEvent, io.worxbend.tui.core.Rect) => Boolean] =
    Some(clickActivates(() => if options.nonEmpty then selected.update(index => (index + 1) % options.size)))
  def widget: Widget                                             = w.Select(options, selected.peek, focusStyled(props))
  private[dsl] def withProps(props: ElementProps): SelectElement = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    direction match
      case Direction.Vertical   => Constraint.Length(1)
      case Direction.Horizontal => Constraint.Fill(1)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]  = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    if !props.focused || options.isEmpty then false
    else
      event.code match
        case KeyCode.Left  =>
          selected.update(index => (index - 1 + options.size) % options.size)
          true
        case KeyCode.Right =>
          selected.update(index => (index + 1) % options.size)
          true
        case _             => false

final case class ListElement(
    items: Seq[String],
    state: w.ListState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  private[dsl] override def builtinMouseHandler
      : Option[(io.worxbend.tui.core.MouseEvent, io.worxbend.tui.core.Rect) => Boolean] =
    Some(wheelScrolls(() => state.selectPrevious(items.size), () => state.selectNext(items.size)))
  def widget: Widget =
    // no whole-body focus styling: the selection highlight is the focus cue for scrollable widgets
    val view = w.ListView(items.map(Line.raw), style = props.style)
    (area, buffer) => view.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): ListElement             = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean] = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    if !props.focused then false
    else
      event.code match
        case KeyCode.Down =>
          state.selectNext(items.size)
          true
        case KeyCode.Up   =>
          state.selectPrevious(items.size)
          true
        case _            => false

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
    if !props.focused then false
    else
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
          buffer.set(x, y, io.worxbend.tui.core.Cell(" ", fill))
          x += 1
        y += 1
      inner.widget.render(area, buffer)
  private[dsl] def withProps(props: ElementProps): FilledElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): FilledElement =
    copy(inner = inner.withChildren(children))
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]      = inner.builtinKeyHandler
  private[dsl] override def builtinMouseHandler
      : Option[(io.worxbend.tui.core.MouseEvent, io.worxbend.tui.core.Rect) => Boolean] =
    inner.builtinMouseHandler
  private[dsl] override def builtinPasteHandler: Option[String => Boolean] = inner.builtinPasteHandler
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

/** A scrollable viewport over taller-than-the-screen content. Up/Down/PageUp/PageDown scroll while focused. */
final case class ScrollViewElement(
    content: Element,
    contentHeight: Int,
    state: w.ScrollViewState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  override def children: Seq[Element]                                               = Seq(content)
  def widget: Widget =
    (area, buffer) =>
      val resolved =
        if contentHeight > 0 then contentHeight
        else content.intrinsicHeight(math.max(1, area.width - 1)).getOrElse(area.height)
      w.ScrollView(content.widget, resolved).render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): ScrollViewElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): ScrollViewElement =
    copy(content = children.headOption.getOrElse(content))
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]          = Some(handleKey)
  private[dsl] override def builtinMouseHandler
      : Option[(io.worxbend.tui.core.MouseEvent, io.worxbend.tui.core.Rect) => Boolean] =
    Some(wheelScrolls(() => state.scrollUp(), () => state.scrollDown()))

  private def handleKey(event: KeyEvent): Boolean =
    if !props.focused then false
    else
      event.code match
        case KeyCode.Up       =>
          state.scrollUp()
          true
        case KeyCode.Down     =>
          state.scrollDown()
          true
        case KeyCode.PageUp   =>
          state.scrollUp(10)
          true
        case KeyCode.PageDown =>
          state.scrollDown(10)
          true
        case _                => false

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
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]             = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    if !props.focused || pageCount == 0 then false
    else
      event.code match
        case KeyCode.Left  =>
          selected.update(index => (index - 1 + pageCount) % pageCount)
          true
        case KeyCode.Right =>
          selected.update(index => (index + 1) % pageCount)
          true
        case _             => false

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
    Some(toggleOnActivate(props, () => expanded.update(open => !open)))

/** Two panes split by an adjustable divider: `[`/`]` shift the split while the pane itself is focused. */
final case class SplitPaneElement(
    first: Element,
    second: Element,
    splitPercent: Signal[Int],
    horizontal: Boolean = true,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  private[dsl] override def builtinMouseHandler
      : Option[(io.worxbend.tui.core.MouseEvent, io.worxbend.tui.core.Rect) => Boolean] =
    Some { (event, area) =>
      if event.kind == io.worxbend.tui.core.MouseEventKind.Drag then
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
    if !props.focused then false
    else
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
    val query = state.input.value.toLowerCase
    if query.isEmpty then Seq.empty
    else
      suggestions
        .filter { candidate =>
          var i = 0
          candidate.toLowerCase.foreach(c => if i < query.length && query.charAt(i) == c then i += 1)
          i == query.length
        }
        .take(maxSuggestions)

  def widget: Widget                                                        =
    val visible   = matches
    val highlight = math.max(0, math.min(state.highlighted, math.max(0, visible.size - 1)))
    val input     = w.TextInput(showCursor = props.focused, style = props.style)
    (area, buffer) =>
      input.render(io.worxbend.tui.core.Rect(area.x, area.y, area.width, 1), buffer, state.input)
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
    if !props.focused then false
    else
      event.code match
        case KeyCode.Char(c) if event.modifiers.isEmpty || event.modifiers == KeyModifiers.Shift =>
          state.input.insert(c.toString)
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
      val treeArea   = io.worxbend.tui.core.Rect(area.x, area.y, area.width, math.max(0, area.height - 1))
      tree.render(treeArea, buffer, state.tree)
      val chosenLine = state.chosen.peek.map(path => s"→ $path").getOrElse("→ (nothing selected)")
      buffer.setString(area.x, area.bottom - 1, chosenLine, props.style.dim)
  private[dsl] def withProps(props: ElementProps): FilePickerElement       = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean] = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    if !props.focused then false
    else
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
    if !props.focused || options.isEmpty then false
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
  private[dsl] override def builtinMouseHandler
      : Option[(io.worxbend.tui.core.MouseEvent, io.worxbend.tui.core.Rect) => Boolean] =
    Some { (event, area) =>
      event.kind match
        case io.worxbend.tui.core.MouseEventKind.Down | io.worxbend.tui.core.MouseEventKind.Drag =>
          if area.width > 3 then
            val fraction = (event.x - area.x - 1).toDouble / (area.width - 3)
            value.set(min + math.round(math.max(0.0, math.min(1.0, fraction)) * (max - min)).toInt)
          true
        case _                                                                                   => false
    }
  def widget: Widget = w.Slider(value.peek, min, max, props.style, focusStyled(props).bold)
  private[dsl] def withProps(props: ElementProps): SliderElement            = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    direction match
      case Direction.Vertical   => Constraint.Length(1)
      case Direction.Horizontal => Constraint.Fill(1)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]  = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    if !props.focused then false
    else
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
    if !props.focused then false
    else
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
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    direction match
      case Direction.Vertical   => Constraint.Length(1)
      case Direction.Horizontal => Constraint.Fill(1)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]  = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    if !props.focused then false
    else
      event.code match
        case KeyCode.Char(c) if event.modifiers.isEmpty =>
          if accepts(c) then state.insert(c.toString)
          true // swallow rejected characters too: they must not bubble as global keys while typing
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

  private def accepts(c: Char): Boolean =
    if c.isDigit then true
    else if c == '-' then state.cursor == 0 && !state.value.startsWith("-")
    else if c == '.' then allowDecimal && !state.value.contains('.')
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
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    direction match
      case Direction.Vertical   => Constraint.Length(1)
      case Direction.Horizontal => Constraint.Fill(1)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]  = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    if !props.focused then false
    else
      event.code match
        case KeyCode.Char(c) if event.modifiers.isEmpty =>
          typeChar(c)
          true
        case KeyCode.Backspace                          =>
          eraseSlot()
          true
        case _                                          => false

  private def typeChar(c: Char): Unit =
    state.moveEnd()
    var position = currentLength
    // literals between fillable slots insert themselves
    while position < mask.length && !isSlot(mask.charAt(position)) do
      state.insert(mask.charAt(position).toString)
      position += 1
    if position < mask.length && slotAccepts(mask.charAt(position), c) then state.insert(c.toString)

  private def eraseSlot(): Unit =
    state.moveEnd()
    state.backspace()
    while currentLength > 0 && !isSlot(mask.charAt(currentLength - 1)) do state.backspace()

  private def currentLength: Int = CharWidth.graphemeClusters(state.value).size

  private def isSlot(m: Char): Boolean = m == '#' || m == 'A'

  private def slotAccepts(m: Char, c: Char): Boolean =
    (m == '#' && c.isDigit) || (m == 'A' && c.isLetter)

/** A page indicator: Left/Right change the page while focused. */
final case class PaginatorElement(
    current: Signal[Int],
    total: Int,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  def widget: Widget = w.Paginator(current.peek, total, props.style, focusStyled(props).bold)
  private[dsl] def withProps(props: ElementProps): PaginatorElement         = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    direction match
      case Direction.Vertical   => Constraint.Length(1)
      case Direction.Horizontal => Constraint.Fill(1)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]  = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    if !props.focused || total == 0 then false
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
  def widget: Widget                                                        = w.Button(label, focusStyled(props))
  private[dsl] def withProps(props: ElementProps): ButtonElement            = copy(props = props)
  private[dsl] override def preferredSize(direction: Direction): Constraint =
    direction match
      case Direction.Vertical   => Constraint.Length(1)
      case Direction.Horizontal => Constraint.Fill(1)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean]  =
    Some(toggleOnActivate(props, action))
  private[dsl] override def builtinMouseHandler
      : Option[(io.worxbend.tui.core.MouseEvent, io.worxbend.tui.core.Rect) => Boolean] =
    Some(clickActivates(action))

/** A scrollable log panel: Up/Down (and PageUp/PageDown) scroll while focused; the tail re-follows when scrolled back
  * to the bottom.
  */
final case class LogElement(
    state: w.LogState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  private[dsl] override def builtinMouseHandler
      : Option[(io.worxbend.tui.core.MouseEvent, io.worxbend.tui.core.Rect) => Boolean] =
    Some(wheelScrolls(() => state.scrollUp(), () => state.scrollDown()))
  def widget: Widget                                                       =
    val log = w.Log(props.style)
    (area, buffer) => log.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): LogElement              = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[KeyEvent => Boolean] = Some(handleKey)

  private def handleKey(event: KeyEvent): Boolean =
    if !props.focused then false
    else
      event.code match
        case KeyCode.Up       =>
          state.scrollUp()
          true
        case KeyCode.Down     =>
          state.scrollDown()
          true
        case KeyCode.PageUp   =>
          state.scrollUp(10)
          true
        case KeyCode.PageDown =>
          state.scrollDown(10)
          true
        case _                => false

/** Multi-line editor element. While focused it consumes printable characters, Enter (newline), Backspace, Delete,
  * arrows, Home/End, and Ctrl+Z (undo) — Tab stays free for focus traversal.
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
    if !props.focused then false
    else
      event match
        case KeyEvent(KeyCode.Char('z'), modifiers) if modifiers.has(KeyModifiers.Ctrl)                   =>
          state.undo()
          true
        case KeyEvent(KeyCode.Char(c), modifiers) if modifiers.isEmpty || modifiers == KeyModifiers.Shift =>
          state.insert(c.toString)
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
    if !props.focused then false
    else
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
    if !props.focused then false
    else
      event.code match
        case KeyCode.Down =>
          state.selectNext(table.visibleRows(state).size)
          true
        case KeyCode.Up   =>
          state.selectPrevious(table.visibleRows(state).size)
          true
        case _            => false

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
  private[dsl] override def builtinMouseHandler
      : Option[(io.worxbend.tui.core.MouseEvent, io.worxbend.tui.core.Rect) => Boolean] =
    inner.builtinMouseHandler
  private[dsl] override def builtinPasteHandler: Option[String => Boolean] = inner.builtinPasteHandler
  private[dsl] override def preferredSize(direction: Direction): Constraint      = inner.preferredSize(direction)

/** A mouse press activates the control (focus already moved on the press). */
private def clickActivates(
    activate: () => Unit
): (io.worxbend.tui.core.MouseEvent, io.worxbend.tui.core.Rect) => Boolean =
  (event, _) =>
    if event.kind == io.worxbend.tui.core.MouseEventKind.Down then
      activate()
      true
    else false

/** Wheel events scroll by one step. */
private def wheelScrolls(
    up: () => Unit,
    down: () => Unit,
): (io.worxbend.tui.core.MouseEvent, io.worxbend.tui.core.Rect) => Boolean =
  (event, _) =>
    event.kind match
      case io.worxbend.tui.core.MouseEventKind.ScrollUp   =>
        up()
        true
      case io.worxbend.tui.core.MouseEventKind.ScrollDown =>
        down()
        true
      case _                                              => false

/** Space/Enter activates a focused two-state control. */
private def toggleOnActivate(props: ElementProps, activate: () => Unit): KeyEvent => Boolean =
  event =>
    if !props.focused then false
    else
      event.code match
        case KeyCode.Char(' ') | KeyCode.Enter =>
          activate()
          true
        case _                                 => false

/** Focused interactive elements render with the focus style (the theme's, once the focus pass ran) layered
  * over their own, so the user can see where keystrokes go.
  */
private def focusStyled(props: ElementProps): Style =
  if props.focused then props.style.patch(props.focusStyle) else props.style

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

  def spinner(frame: Int, label: String = ""): WidgetElement =
    WidgetElement(w.Spinner(frame, label))

  def waveText(content: String, phase: Int): WidgetElement =
    WidgetElement(w.WaveText(content, phase))

  def dialog(title: String, message: String, buttons: Seq[String] = Seq("OK"), selected: Int = 0): WidgetElement =
    WidgetElement(w.Dialog(title, io.worxbend.tui.core.Text.raw(message), buttons, selected))

  def dualSparkline(upper: Seq[Long], lower: Seq[Long]): WidgetElement =
    WidgetElement(w.DualSparkline(upper, lower))

  def skeleton(phase: Int): WidgetElement =
    WidgetElement(w.Skeleton(phase))

  def indeterminateBar(phase: Int): WidgetElement =
    WidgetElement(
      w.IndeterminateBar(phase),
      ElementProps(constraint = Some(Constraint.Length(1))),
    )

  def autocomplete(
      state: AutocompleteState,
      suggestions: Seq[String],
      onAccept: String => Unit = _ => (),
  ): AutocompleteElement =
    AutocompleteElement(state, suggestions, onAccept)

  def filePicker(state: FilePickerState): FilePickerElement =
    FilePickerElement(state)

  def radioGroup(options: Seq[String], selected: io.worxbend.tui.runtime.Signal[Int]): RadioGroupElement =
    RadioGroupElement(options, selected)

  def slider(value: io.worxbend.tui.runtime.Signal[Int], min: Int = 0, max: Int = 100, step: Int = 5): SliderElement =
    SliderElement(value, min, max, step)

  def selectionList(
      items: Seq[String],
      selected: io.worxbend.tui.runtime.Signal[Set[Int]],
      state: io.worxbend.tui.widgets.ListState,
  ): SelectionListElement =
    SelectionListElement(items, selected, state)

  def numberInput(state: io.worxbend.tui.widgets.TextInputState, allowDecimal: Boolean = false): NumberInputElement =
    NumberInputElement(state, allowDecimal)

  def maskedInput(state: io.worxbend.tui.widgets.TextInputState, mask: String): MaskedInputElement =
    MaskedInputElement(state, mask)

  def paginator(current: io.worxbend.tui.runtime.Signal[Int], total: Int): PaginatorElement =
    PaginatorElement(current, total)

  def marquee(content: String, phase: Int): WidgetElement =
    WidgetElement(
      w.Marquee(content, phase),
      ElementProps(constraint = Some(Constraint.Length(1))),
    )

  def image(source: io.worxbend.tui.widgets.Image): WidgetElement =
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
      table: io.worxbend.tui.widgets.DataTable,
      state: io.worxbend.tui.widgets.DataTableState,
  ): DataTableElement =
    DataTableElement(table, state)

  def directoryTree(state: io.worxbend.tui.widgets.DirectoryTreeState): DirectoryTreeElement =
    DirectoryTreeElement(state)

  def textArea(state: io.worxbend.tui.widgets.TextAreaState): TextAreaElement =
    TextAreaElement(state)

  def button(label: String)(action: => Unit): ButtonElement =
    ButtonElement(label, () => action)

  /** Later layers paint over earlier ones across the full area. */
  def layers(base: Element, overlays: Element*): LayersElement =
    LayersElement(base +: overlays)

  def scrollView(
      content: Element,
      contentHeight: Int,
      state: io.worxbend.tui.widgets.ScrollViewState,
  ): ScrollViewElement =
    ScrollViewElement(content, contentHeight, state)

  /** Scroll view that measures its content's height itself (falls back to the viewport height when the
    * content is unmeasurable — fill-sized children).
    */
  def scrollView(content: Element, state: io.worxbend.tui.widgets.ScrollViewState): ScrollViewElement =
    ScrollViewElement(content, contentHeight = -1, state)

  /** `tabbedContent("One" -> pageOne, "Two" -> pageTwo)(selected)` — the selected page is picked at view construction,
    * so the tree always holds exactly the visible page.
    */
  def tabbedContent(pages: (String, Element)*)(selected: io.worxbend.tui.runtime.Signal[Int]): TabbedContentElement =
    val index  = math.max(0, math.min(selected.peek, pages.size - 1))
    val active = if pages.isEmpty then Element.text("") else pages(index)._2
    TabbedContentElement(pages.map(_._1), active, selected, pages.size)

  def collapsible(title: String, expanded: io.worxbend.tui.runtime.Signal[Boolean])(body: Element): CollapsibleElement =
    CollapsibleElement(title, body, expanded)

  def splitPane(
      first: Element,
      second: Element,
      splitPercent: io.worxbend.tui.runtime.Signal[Int],
      horizontal: Boolean = true,
  ): SplitPaneElement =
    SplitPaneElement(first, second, splitPercent, horizontal)

  def log(state: io.worxbend.tui.widgets.LogState): LogElement =
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
