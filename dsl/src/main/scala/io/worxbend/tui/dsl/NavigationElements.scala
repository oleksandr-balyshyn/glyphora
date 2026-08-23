package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Constraint, Direction, KeyCode, KeyEvent, Line, MouseEventKind, Size, Widget}
import io.worxbend.tui.widgets as w

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
  * `resolved` is filled in by [[ResponsivePass]] on every render, never by user code — the same contract [[FocusState]]
  * has. While it is empty the node still renders: it falls back to building against its own area, so a construction
  * test that draws `element.widget` straight into a buffer without a [[TuiApp]] behind it shows content rather than
  * blank space. That fallback has no focus pass behind it, so focusables inside it are inert — which is why the pass
  * exists rather than doing this at render time.
  */
final case class ResponsiveElement(
    build: Size => Element,
    resolved: Option[Element] = None,
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = ResponsiveElement
  override def children: Seq[Element]                                               = resolved.toSeq
  def widget: Widget                                                                =
    resolved match
      case Some(branch) => branch.widget
      case None         => (area, buffer) => build(area.size).widget.render(area, buffer)
  private[dsl] def withProps(props: ElementProps): ResponsiveElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): ResponsiveElement =
    copy(resolved = children.headOption.orElse(resolved))

  /** This node holding what `build` produces at `size` — [[ResponsivePass]]'s single mutation. */
  private[dsl] def resolvedAt(branch: Element): ResponsiveElement = copy(resolved = Some(branch))

  private[dsl] override def claim: SizeClaim = resolved.map(_.claim).getOrElse(SizeClaim.Fill)

  private[dsl] override def intrinsicHeight(width: Int): Option[Int] =
    props.constraint match
      case Some(Constraint.Length(cells)) => Some(cells)
      case Some(_)                        => None
      case None                           => resolved.flatMap(_.intrinsicHeight(width))

/** A scrollable viewport over taller-than-the-screen content. Up/Down/PageUp/PageDown scroll while focused.
  *
  * `contentHeight` is the caller's own measurement; `None` means "measure the content each frame", which is what the
  * two-argument `scrollView(content, state)` factory asks for.
  */
final case class ScrollViewElement(
    content: Element,
    contentHeight: Option[Int],
    state: w.ScrollViewState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = ScrollViewElement
  override def children: Seq[Element]                                               = Seq(content)
  def widget: Widget                                                                =
    (area, buffer) =>
      // `area.height` is the last resort, and it is a lie the caller cannot see: content that reports no height
      // measures as exactly one viewport tall, the offset clamps to zero, and everything below the fold becomes
      // unreachable rather than merely mis-scrolled. There is no better answer at render time — the content has
      // already been asked — so the fix belongs upstream: give the content a `.length(n)`, pass `contentHeight`, or
      // make its widget answer `Measured.heightAt`.
      val rows =
        contentHeight
          .filter(_ > 0)
          .getOrElse(content.intrinsicHeight(math.max(1, area.width - 1)).getOrElse(area.height))
      // the content renders into an offscreen buffer, so it cannot see where this scroll view sits on screen; the
      // focus pass's viewport wrapper needs that to translate the areas recorded underneath it
      content match
        case viewport: ScrollViewportElement => viewport.publishScreenArea(area)
        case _                               => ()
      w.ScrollView(content.widget, rows).render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): ScrollViewElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): ScrollViewElement =
    copy(content = children.headOption.getOrElse(content))
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler]            =
    Some(scrollKeys(rows => state.scrollUp(rows), rows => state.scrollDown(rows)))
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler]        =
    Some(wheelScrolls(() => state.scrollUp(), () => state.scrollDown()))

/** A tab row plus the selected page (Textual's `TabbedContent`): Left/Right switch pages while focused. Only the active
  * page's focusables participate in the tab order.
  */
final case class TabbedContentElement(
    titles: Seq[String],
    activePage: Element,
    selected: Int,
    onSelect: Int => Unit,
    pageCount: Int,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = TabbedContentElement
  override def children: Seq[Element]                                                  = Seq(activePage)
  def widget: Widget                                                                   =
    val tabs = w.Tabs(titles.map(Line.raw), selected, focusStyled(props))
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
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler]               =
    Some(stepsWrapping(pageCount, selected, onSelect))

/** A toggleable section: `▸ title` collapsed, `▾ title` plus the body expanded; Enter/Space toggle while focused.
  * Collapsed bodies leave the tab order entirely.
  */
final case class CollapsibleElement(
    title: String,
    body: Element,
    expanded: Boolean,
    onToggle: Boolean => Unit,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = CollapsibleElement
  override def children: Seq[Element]                                 = if expanded then Seq(body) else Seq.empty
  def widget: Widget                                                  =
    val marker         = if expanded then "▾ " else "▸ "
    val header: Widget = (area, buffer) => buffer.setString(area.x, area.y, marker + title, focusStyled(props))
    (area, buffer) =>
      if expanded then
        w.Column(
          Seq(w.LayoutItem(Constraint.Length(1), header), w.LayoutItem(Constraint.Fill(1), body.widget))
        ).render(area, buffer)
      else header.render(area, buffer)
  private[dsl] def withProps(props: ElementProps): CollapsibleElement = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): CollapsibleElement =
    copy(body = children.headOption.getOrElse(body))
  private[dsl] override def claim: SizeClaim                                         =
    if expanded then SizeClaim.Fill else SizeClaim.OneRow
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler]             =
    Some(toggleOnActivate(() => onToggle(!expanded)))

/** Two panes split by an adjustable divider: `[`/`]` shift the split while the pane itself is focused. */
final case class SplitPaneElement(
    first: Element,
    second: Element,
    splitPercent: Int,
    onSplit: Int => Unit,
    axis: Direction = Direction.Horizontal,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = SplitPaneElement
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler]       =
    Some { (event, area) =>
      if event.kind == MouseEventKind.Drag then
        val fraction = axis match
          case Direction.Horizontal => (event.position.x - area.x).toDouble / math.max(1, area.width)
          case Direction.Vertical   => (event.position.y - area.y).toDouble / math.max(1, area.height)
        onSplit(clampSplit(math.round(fraction * 100).toInt))
        true
      else false
    }
  override def children: Seq[Element]                                              = Seq(first, second)
  def widget: Widget                                                               =
    val percent = clampSplit(splitPercent)
    val items   = Seq(
      first.layoutItem(axis).copy(constraint = Constraint.Percentage(percent)),
      second.layoutItem(axis).copy(constraint = Constraint.Fill(1)),
    )
    axis match
      case Direction.Horizontal => w.Row(items, spacing = 1)
      case Direction.Vertical   => w.Column(items, spacing = 0)
  private[dsl] def withProps(props: ElementProps): SplitPaneElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): SplitPaneElement =
    children match
      case Seq(a, b) => copy(first = a, second = b)
      case _         => this
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler]           =
    Some(
      keys {
        case KeyEvent(KeyCode.Char('['), _) => onSplit(clampSplit(splitPercent - SplitStep))
        case KeyEvent(KeyCode.Char(']'), _) => onSplit(clampSplit(splitPercent + SplitStep))
      }
    )
