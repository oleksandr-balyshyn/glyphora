package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Cell, Direction, Flex, Line, Rect, Style, Widget}
import io.worxbend.tui.widgets as w

/** A container that arranges its children along an axis, so `.gap`/`.center`/`.spaceBetween` can be typed to the
  * containers they actually do something on instead of silently being the identity everywhere else.
  *
  * `withFlex`/`withSpacing` are the plain-method form of those extensions; both give back this node's own type.
  */
trait FlexContainer extends Element:

  /** This container rebuilt with a different alignment for the space its children leave over. */
  def withFlex(mode: Flex): Self

  /** This container rebuilt with `cells` blank cells between neighbouring children (negative counts clamp to zero). */
  def withSpacing(cells: Int): Self

/** A bordered box with an optional title, stacking its children vertically inside the border.
  *
  * Two captions are available, and neither costs a content row: `title` is written into the top border at the left,
  * `titleBottom` into the bottom border at the right — the name-above / status-below shape most panes in a real
  * application want. [[padding]] reserves blank cells between the border and the children.
  *
  * The children are stacked by the same `w.Column` a [[ColumnElement]] builds, so a panel is a [[FlexContainer]] too:
  * `.gap`/`.center`/`.spaceBetween` work inside the border without wrapping the children in a `column` first.
  */
final case class PanelElement(
    title: Option[String],
    override val children: Seq[Element],
    borderType: w.BorderType = w.BorderType.Plain,
    titleBottom: Option[String] = None,
    titleStyle: Option[Style] = None,
    padding: w.Padding = w.Padding.zero,
    spacing: Int = 0,
    flex: Flex = Flex.Start,
    mergeBorders: w.MergeStrategy = w.MergeStrategy.Replace,
    props: ElementProps = ElementProps(),
) extends FlexContainer:
  type Self = PanelElement

  def widget: Widget =
    (area, buffer) =>
      val block =
        w.Block(
          blockTitles,
          padding = padding,
          borderStyle = props.style,
          borderType = borderType,
          mergeBorders = mergeBorders,
        )
      block.render(area, buffer)
      w.Column(children.map(_.layoutItem(Direction.Vertical)), spacing, flex).render(block.inner(area), buffer)

  def withFlex(mode: Flex): PanelElement    = copy(flex = mode)
  def withSpacing(cells: Int): PanelElement = copy(spacing = math.max(0, cells))

  /** The captions handed to `w.Block`.
    *
    * The titles and the border are styled separately — `titleStyle` when the caller set one, the element's own style
    * otherwise — so `panel("Errors")(...).fg(Color.Red)` reddens the frame and `.titleStyle(_.withFg(Color.Red))`
    * reddens only the name. Passing one style for both, as this node used to, made the second of those impossible to
    * express.
    */
  private def blockTitles: Seq[w.BlockTitle] =
    val captionStyle = titleStyle.getOrElse(props.style)
    title.map(text => w.BlockTitle.top(Line.styled(text, captionStyle))).toSeq ++
      titleBottom.map(text => w.BlockTitle.bottom(Line.styled(text, captionStyle), w.Alignment.Right)).toSeq

  /** A second caption, written into the bottom border at the right — a status, a count, a keybinding hint. */
  def titleBottom(text: String): PanelElement = copy(titleBottom = Some(text))

  /** Styles the captions independently of the border, which keeps the element's own style. */
  def titleStyle(transform: Style => Style): PanelElement =
    copy(titleStyle = Some(transform(titleStyle.getOrElse(props.style))))

  /** Blank cells between the border and the children, per side. */
  def padded(cells: w.Padding): PanelElement = copy(padding = cells)

  /** Padding that reads as `cells` even all the way round: `cells` rows above and below, twice that many columns either
    * side, because a terminal cell is about twice as tall as it is wide. See `w.Padding.proportional`.
    */
  def padding(cells: Int): PanelElement = padded(w.Padding.proportional(cells))

  private[dsl] def withProps(props: ElementProps): PanelElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): PanelElement = copy(children = children)
  private[dsl] override def intrinsicHeight(width: Int): Option[Int] =
    // the same fact twice: `w.Block`'s border eats a column on each side and a row at the top and bottom, and the
    // padding eats whatever it was asked for on top of that
    val chrome  = PanelBorderCells + padding.horizontalCells
    val gaps    = spacing * math.max(0, children.size - 1)
    val heights = children.map(_.intrinsicHeight(math.max(0, width - chrome)))
    if heights.forall(_.nonEmpty) then Some(heights.flatten.sum + gaps + PanelBorderCells + padding.verticalCells)
    else None

/** Border glyph sets, on the one node type where a border exists at all.
  *
  * `w.BorderType` has four members — the square `┌─┐`, the rounded `╭─╮`, the double `╔═╗` and the heavy `┏━┓` — and
  * all four are single-column glyphs, so choosing one changes only the look and never the space left for content.
  */
extension (panel: PanelElement)

  /** Rounded corners instead of square ones. */
  def rounded: PanelElement = panel.copy(borderType = w.BorderType.Rounded)

  /** A double-line border — the conventional "this box is important" cue. */
  def doubleBorder: PanelElement = panel.copy(borderType = w.BorderType.Double)

  /** A heavy single-line border (`┏━┓`): the same emphasis a double border gives, in one stroke rather than two, which
    * reads better next to plain-bordered neighbours.
    */
  def thick: PanelElement = panel.copy(borderType = w.BorderType.Thick)

  /** Any border glyph set by name, for code that picks one from a value rather than writing it out — a theme setting,
    * a user preference, a `match` on some state. The three named builders above are shorthands for this.
    */
  def borderType(glyphs: w.BorderType): PanelElement = panel.copy(borderType = glyphs)
  /** Joins this panel's border to any border already drawn where it lands, instead of overwriting it.
    *
    * Two panels laid side by side share a column. By default the second one drawn simply wins there, which gives one
    * straight wall but leaves the corners unjoined — the seam reads as `┐` above `┘` where a single frame would show
    * `┬` above `┴`. With `MergeStrategy.Exact` the two glyphs are combined into the one that shows both. `Fuzzy` does
    * the same and additionally accepts a lighter joint where Unicode has no exact one, which is what a double-walled
    * panel meeting a thick-walled one needs.
    *
    * Merging only looks at what is already in the buffer, so it depends on draw order: the panel drawn second is the
    * one that has something to join to.
    */
  def mergeBorders(strategy: w.MergeStrategy): PanelElement = panel.copy(mergeBorders = strategy)

/** Children laid out side by side, left to right. */
final case class RowElement(
    override val children: Seq[Element],
    spacing: Int = 0,
    flex: Flex = Flex.Start,
    props: ElementProps = ElementProps(),
) extends FlexContainer:
  type Self = RowElement
  def widget: Widget                      = w.Row(children.map(_.layoutItem(Direction.Horizontal)), spacing, flex)
  def withFlex(mode: Flex): RowElement    = copy(flex = mode)
  def withSpacing(cells: Int): RowElement = copy(spacing = math.max(0, cells))
  private[dsl] def withProps(props: ElementProps): RowElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): RowElement = copy(children = children)
  private[dsl] override def intrinsicHeight(width: Int): Option[Int]         =
    val heights = children.map(_.intrinsicHeight(width))
    if heights.forall(_.nonEmpty) then heights.flatten.maxOption else None

/** Children stacked top to bottom. */
final case class ColumnElement(
    override val children: Seq[Element],
    spacing: Int = 0,
    flex: Flex = Flex.Start,
    props: ElementProps = ElementProps(),
) extends FlexContainer:
  type Self = ColumnElement
  def widget: Widget                         = w.Column(children.map(_.layoutItem(Direction.Vertical)), spacing, flex)
  def withFlex(mode: Flex): ColumnElement    = copy(flex = mode)
  def withSpacing(cells: Int): ColumnElement = copy(spacing = math.max(0, cells))
  private[dsl] override def intrinsicHeight(width: Int): Option[Int]            =
    val heights = children.map(_.intrinsicHeight(width))
    if heights.forall(_.nonEmpty) then Some(heights.flatten.sum + spacing * math.max(0, children.size - 1))
    else None
  private[dsl] def withProps(props: ElementProps): ColumnElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): ColumnElement = copy(children = children)

/** Blank space: flexible by default, fixed once `.length(n)` sets a constraint. */
final case class SpacerElement(props: ElementProps = ElementProps()) extends Element:
  type Self = SpacerElement
  def widget: Widget                                             = w.Spacer
  private[dsl] def withProps(props: ElementProps): SpacerElement = copy(props = props)

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
  type Self = PositionedElement
  override def children: Seq[Element]                                               = Seq(content)
  def widget: Widget                                                                =
    (area, buffer) =>
      val target = Rect(area.x + dx, area.y + dy, width, height).intersection(area)
      if !target.isEmpty then content.widget.render(target, buffer)
  private[dsl] def withProps(props: ElementProps): PositionedElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): PositionedElement =
    copy(content = children.headOption.getOrElse(content))

/** Fills its whole area with `fill` (a solid background) before rendering `inner` — what the chrome bars use to read as
  * continuous surfaces. Transparent to focus, measurement and event routing.
  */
final case class FilledElement(
    inner: Element,
    fill: Style,
    override val props: ElementProps = ElementProps(),
) extends DecoratingElement:
  type Self = FilledElement
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
    copy(inner = children.headOption.getOrElse(inner))

/** Z-ordered stacking: every child renders over the full area in order, so later children paint over earlier ones — the
  * primitive under dialogs, toasts, palettes, and splash overlays.
  */
final case class LayersElement(
    override val children: Seq[Element],
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = LayersElement
  def widget: Widget                                                            =
    (area, buffer) => children.foreach(_.widget.render(area, buffer))
  private[dsl] def withProps(props: ElementProps): LayersElement                = copy(props = props)
  private[dsl] override def withChildren(children: Seq[Element]): LayersElement = copy(children = children)
