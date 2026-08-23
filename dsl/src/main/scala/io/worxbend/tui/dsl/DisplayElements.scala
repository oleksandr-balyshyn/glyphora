package io.worxbend.tui.dsl

import io.worxbend.tui.core.{CharWidth, Constraint, Line, Span, Style, Text, Widget}
import io.worxbend.tui.widgets as w

import java.time.LocalTime

/** A block of plain text; one row per newline-separated line. */
final case class TextElement(content: String, props: ElementProps = ElementProps()) extends Element:
  type Self = TextElement
  def widget: Widget                                           = w.Paragraph(Text.styled(content, props.style))
  private[dsl] def withProps(props: ElementProps): TextElement = copy(props = props)
  private[dsl] override def claim: SizeClaim                   =
    val lines = content.split("\n", -1)
    SizeClaim.box(lines.map(CharWidth.of).maxOption.getOrElse(0), lines.length)

/** One terminal row of differently-styled runs — the mixed-style counterpart of [[TextElement]].
  *
  * `text(...)` paints a whole block in one style, which cannot say "Status: " in the default colour and "OK" in green.
  * Before this node the way to get that was `row(text("Status: ").length(8), text("OK").fg(Color.Green))`, i.e. a hand
  * counted display width in the source — wrong the moment the label is translated, and wrong today for any CJK or emoji
  * text, where one character is two columns. Here the widths are measured, not declared.
  *
  * The element's own style is the base each span layers onto, so `line(...).dim` dims the whole row while a span that
  * set its own colour keeps it.
  */
final case class LineElement(spans: Seq[Span], props: ElementProps = ElementProps()) extends Element:
  type Self = LineElement
  def widget: Widget                                           =
    w.Paragraph(Text(Seq(Line(spans.map(span => span.copy(style = props.style.patch(span.style)))))))
  private[dsl] def withProps(props: ElementProps): LineElement = copy(props = props)

  /** Exactly one row, and exactly as wide as the spans measure — in display columns, via `CharWidth`. */
  private[dsl] override def claim: SizeClaim = SizeClaim.box(spans.map(_.width).sum, 1)

/** A one-row filled bar with a caption over it; the caption defaults to the percentage.
  *
  * `.label`/`.labelled`/`.bare` are the same trio [[ProgressBarElement]] carries, so the two progress meters are
  * captioned the same way whichever one a view reaches for.
  */
final case class GaugeElement(
    ratio: Double,
    label: w.ProgressLabel = w.ProgressLabel.Percentage,
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = GaugeElement
  def widget: Widget = w.Gauge(ratio, label, props.style, filledStyle = props.style.reverse)

  /** Replaces the percentage caption with fixed text. */
  def label(text: String): GaugeElement = copy(label = w.ProgressLabel.Text(text))

  /** Shows fixed text followed by the percentage, as in `syncing 42%`. */
  def labelled(text: String): GaugeElement = copy(label = w.ProgressLabel.TextAndPercentage(text))

  /** Drops the caption entirely, leaving the bar uninterrupted. */
  def bare: GaugeElement = copy(label = w.ProgressLabel.Hidden)

  private[dsl] def withProps(props: ElementProps): GaugeElement = copy(props = props)
  private[dsl] override def claim: SizeClaim                    = SizeClaim.OneRow

/** One styled message line, themed at construction. */
final case class NoticeElement(
    message: String,
    level: w.NoticeLevel,
    timestamp: Option[LocalTime],
    messageStyle: Style,
    accentStyle: Style,
    timestampStyle: Style,
    overflow: w.Overflow = w.Overflow.Clip,
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = NoticeElement
  def widget: Widget =
    w.Notice(
      message,
      level,
      timestamp,
      messageStyle.patch(props.style),
      accentStyle,
      timestampStyle,
      overflow = overflow,
    )

  /** Stamps the notice with the moment the event happened. Pass the time explicitly — a widget that read the clock
    * itself would render a different frame on every repaint.
    */
  def at(time: LocalTime): NoticeElement = copy(timestamp = Some(time))

  /** Lets a long message wrap onto further rows instead of clipping. */
  def wrapped: NoticeElement = copy(overflow = w.Overflow.Wrap)

  private[dsl] def withProps(props: ElementProps): NoticeElement = copy(props = props)

/** A short inline label, themed at construction. */
final case class BadgeElement(
    label: String,
    variant: w.BadgeVariant,
    badgeStyle: Style,
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = BadgeElement
  def widget: Widget = w.Badge(label, variant, badgeStyle.patch(props.style))

  /** Bracketed text with no block of colour behind it — for a badge sitting inside prose. */
  def outline: BadgeElement = copy(variant = w.BadgeVariant.Outline)

  /** A coloured dot before plain text: carries the colour without the emphasis. */
  def dot: BadgeElement = copy(variant = w.BadgeVariant.Dot)

  private[dsl] def withProps(props: ElementProps): BadgeElement = copy(props = props)

  /** One row, and exactly as wide as the badge paints itself — asking the widget is what keeps the claimed box from
    * drifting away from the drawn one.
    */
  private[dsl] override def claim: SizeClaim =
    w.Badge(label, variant).widthAt(1).fold(SizeClaim.OneRow)(width => SizeClaim.box(width, 1))

/** A one-row dense line chart of recent values. */
final case class SparklineElement(
    data: Seq[Long],
    max: Option[Long] = None,
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = SparklineElement
  def widget: Widget                                                = w.Sparkline(data, max, props.style)
  private[dsl] def withProps(props: ElementProps): SparklineElement = copy(props = props)
  private[dsl] override def claim: SizeClaim                        = SizeClaim.OneRow

/** A one-row tab strip. Purely presentational — see [[TabbedContentElement]] for the interactive version. */
final case class TabsElement(
    titles: Seq[String],
    selected: Int = 0,
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = TabsElement
  def widget: Widget                                           = w.Tabs(titles.map(Line.raw), selected, props.style)
  private[dsl] def withProps(props: ElementProps): TabsElement = copy(props = props)
  private[dsl] override def claim: SizeClaim                   = SizeClaim.OneRow

/** A static table of rows under fixed column widths. */
final case class TableElement(
    rows: Seq[Seq[String]],
    widths: Seq[Constraint],
    header: Option[Seq[String]] = None,
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = TableElement
  def widget: Widget                                            =
    w.Table.ofStrings(rows, widths, header, style = props.style)
  private[dsl] def withProps(props: ElementProps): TableElement = copy(props = props)

/** Escape hatch: any core [[Widget]] as a leaf element (its rendering ignores the element style).
  *
  * Width-dependent content — wrapped markdown, a paragraph, an image — reports its own height to the measurement pass
  * by implementing [[io.worxbend.tui.core.Measured]] on the widget; this node needs no measurement wiring of its own.
  */
final case class WidgetElement(
    wrapped: Widget,
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = WidgetElement
  def widget: Widget                                             = wrapped
  private[dsl] def withProps(props: ElementProps): WidgetElement = copy(props = props)
