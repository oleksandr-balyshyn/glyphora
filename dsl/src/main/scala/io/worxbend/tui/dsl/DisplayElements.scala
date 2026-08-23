package io.worxbend.tui.dsl

import io.worxbend.tui.core.{CharWidth, Color, Constraint, Line, Span, Style, Text, Widget}
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
  * captioned the same way whichever one a view reaches for. `trackStyle` and `fillStyle` come from the same
  * [[LoadingTheme]] the whole progress-and-spinner family draws from, so a gauge and a `progressBar` side by side are
  * the same two colours.
  */
final case class GaugeElement(
    ratio: Double,
    label: w.ProgressLabel,
    trackStyle: Style,
    fillStyle: Style,
    fillRamp: Option[w.ColorRamp],
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = GaugeElement
  def widget: Widget = w.Gauge(ratio, label, trackStyle.patch(props.style), fillStyle.patch(props.style), fillRamp)

  /** Replaces the percentage caption with fixed text. */
  def label(text: String): GaugeElement = copy(label = w.ProgressLabel.Text(text))

  /** Shows fixed text followed by the percentage, as in `syncing 42%`. */
  def labelled(text: String): GaugeElement = copy(label = w.ProgressLabel.TextAndPercentage(text))

  /** Drops the caption entirely, leaving the bar uninterrupted. */
  def bare: GaugeElement = copy(label = w.ProgressLabel.Hidden)

  /** Colors the fill by how far along it is: `ColorRamp.Traffic` walks green through amber to red, which is what an
    * "how bad is it" meter (disk usage, an air-quality index) wants and a download bar does not.
    *
    * The same builder [[ProgressBarElement.ramp]] carries, so the two meters are colored the same way whichever one a
    * view reaches for — this used to be the one knob that forced a call site down to `widget(w.Gauge(...))`.
    */
  def ramp(chosen: w.ColorRamp): GaugeElement = copy(fillRamp = Some(chosen))

  /** A two-color ramp built inline, for the many cases with no named preset. */
  def ramp(from: Color, to: Color): GaugeElement = copy(fillRamp = Some(w.ColorRamp(from, to)))

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
  def widget: Widget = w.Sparkline(data, max, props.style)

  /** Pins the top of the scale instead of letting it float to the largest value present.
    *
    * Without this a sparkline always uses the full row height, which is what makes two of them *not* comparable: a
    * series peaking at 5 and one peaking at 5000 draw the same shape. Pin both to the same ceiling and the heights mean
    * the same thing.
    */
  def max(ceiling: Long): SparklineElement = copy(max = Some(ceiling))

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
  def widget: Widget =
    w.Table.ofStrings(rows, widths, header, style = props.style)

  /** Adds a bold caption row above the data — one label per column, in the same order as `widths`.
    *
    * The header costs one row of the area and does not scroll away with the rows, because a static table does not
    * scroll at all. Pass a computed sequence with `.header(labels*)`.
    */
  def header(labels: String*): TableElement = copy(header = Some(labels))

  private[dsl] def withProps(props: ElementProps): TableElement = copy(props = props)

/** Escape hatch: any core [[Widget]] as a leaf element (its rendering ignores the element style).
  *
  * Width-dependent content — wrapped markdown, a paragraph, an image — reports its own height to the measurement pass
  * by implementing [[io.worxbend.tui.core.Measured]] on the widget; this node needs no measurement wiring of its own.
  *
  * That measurement is *not* a layout claim, though: the two passes ask different questions and only the measurement
  * pass (scroll views, auto-sized containers) consults `Measured`. By default a wrapped widget takes every cell its
  * siblings left over, because a bare `Widget` has no axis-independent size to derive one from — `heightAt` needs a
  * width the layout solver has not decided yet, so guessing would mis-size wrapped prose rather than fix anything.
  *
  * `rows` is how the call site says otherwise: `Some(n)` means "exactly `n` rows tall, and whatever width the container
  * has", which is the shape every fixed-height widget actually wants. Use it rather than `.length(n)` — that extension
  * sets one constraint that the container applies along *whichever* axis it runs, so a `.length(1)` widget is one row
  * tall in a `column` and one *column wide* in a `row`.
  */
final case class WidgetElement(
    wrapped: Widget,
    rows: Option[Int] = None,
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = WidgetElement
  def widget: Widget = wrapped

  /** This widget rebuilt to claim exactly `count` rows of height, taking the container's full width.
    *
    * The height a fixed-height widget wants is a fact about the widget, not about the container it happens to sit in,
    * so it belongs here rather than in a `.length(count)` at the call site: `row(widget(divider).rows(1), …)` keeps its
    * full width, where `row(widget(divider).length(1), …)` would be a single column.
    */
  def rows(count: Int): WidgetElement = copy(rows = Some(math.max(0, count)))

  private[dsl] def withProps(props: ElementProps): WidgetElement = copy(props = props)
  private[dsl] override def claim: SizeClaim                     = rows.fold(SizeClaim.Fill)(SizeClaim.rows)
