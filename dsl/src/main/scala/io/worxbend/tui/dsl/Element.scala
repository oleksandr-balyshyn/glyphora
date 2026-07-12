package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Constraint, Line, Style, Text, Widget}
import io.worxbend.tui.widgets.{
  Block,
  BorderType,
  Column,
  Gauge,
  LayoutItem,
  Paragraph,
  Row,
  Sparkline,
  Spacer,
  Table,
  Tabs,
}

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

  /** The space this element claims inside a container when the user set nothing explicit. */
  private[dsl] def defaultConstraint: Constraint = Constraint.Fill(1)

  private[dsl] final def layoutItem: LayoutItem =
    LayoutItem(props.constraint.getOrElse(defaultConstraint), widget)

final case class TextElement(content: String, props: ElementProps = ElementProps()) extends Element:
  def widget: Widget = Paragraph(Text.styled(content, props.style))
  private[dsl] def withProps(props: ElementProps): TextElement = copy(props = props)
  private[dsl] override def defaultConstraint: Constraint =
    Constraint.Length(content.split("\n", -1).length)

final case class PanelElement(
    title: Option[String],
    override val children: Seq[Element],
    borderType: BorderType = BorderType.Plain,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget =
    (area, buffer) =>
      val block = Block(title.map(text => Line.styled(text, props.style)), borderType, props.style)
      block.render(area, buffer)
      Column(children.map(_.layoutItem)).render(block.inner(area), buffer)
  private[dsl] def withProps(props: ElementProps): PanelElement = copy(props = props)

final case class RowElement(
    override val children: Seq[Element],
    spacing: Int = 0,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget = Row(children.map(_.layoutItem), spacing)
  private[dsl] def withProps(props: ElementProps): RowElement = copy(props = props)

final case class ColumnElement(
    override val children: Seq[Element],
    spacing: Int = 0,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget = Column(children.map(_.layoutItem), spacing)
  private[dsl] def withProps(props: ElementProps): ColumnElement = copy(props = props)

final case class SpacerElement(props: ElementProps = ElementProps()) extends Element:
  def widget: Widget = Spacer
  private[dsl] def withProps(props: ElementProps): SpacerElement = copy(props = props)

final case class GaugeElement(
    ratio: Double,
    label: Option[String] = None,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget = Gauge(ratio, label, props.style, filledStyle = props.style.reverse)
  private[dsl] def withProps(props: ElementProps): GaugeElement = copy(props = props)
  private[dsl] override def defaultConstraint: Constraint = Constraint.Length(1)

final case class SparklineElement(
    data: Seq[Long],
    max: Option[Long] = None,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget = Sparkline(data, max, props.style)
  private[dsl] def withProps(props: ElementProps): SparklineElement = copy(props = props)
  private[dsl] override def defaultConstraint: Constraint = Constraint.Length(1)

final case class TabsElement(
    titles: Seq[String],
    selected: Int = 0,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget = Tabs(titles.map(Line.raw), selected, props.style)
  private[dsl] def withProps(props: ElementProps): TabsElement = copy(props = props)
  private[dsl] override def defaultConstraint: Constraint = Constraint.Length(1)

final case class TableElement(
    rows: Seq[Seq[String]],
    widths: Seq[Constraint],
    header: Option[Seq[String]] = None,
    props: ElementProps = ElementProps(),
) extends Element:
  def widget: Widget =
    Table(rows.map(_.map(Line.raw)), widths, header.map(_.map(Line.raw)), style = props.style)
  private[dsl] def withProps(props: ElementProps): TableElement = copy(props = props)

/** Escape hatch: any core [[Widget]] as a leaf element (its rendering ignores the element style). */
final case class WidgetElement(wrapped: Widget, props: ElementProps = ElementProps()) extends Element:
  def widget: Widget = wrapped
  private[dsl] def withProps(props: ElementProps): WidgetElement = copy(props = props)

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
