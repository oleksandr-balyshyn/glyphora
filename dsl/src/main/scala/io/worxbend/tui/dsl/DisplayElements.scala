package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Alignment, CharWidth, Color, Constraint, Flex, Line, Span, Style, Text, Widget}
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
final case class LineElement(spans: Seq[Span], align: Option[Alignment] = None, props: ElementProps = ElementProps())
    extends Element:
  type Self = LineElement
  def widget: Widget                                           =
    w.Paragraph(Text(Seq(Line(spans.map(span => span.copy(style = props.style.patch(span.style))), align))))
  private[dsl] def withProps(props: ElementProps): LineElement = copy(props = props)

  /** Places this row inside whatever width it is given; without one of these it sits at the left edge.
    *
    * The alignment is carried by the `Line` itself rather than by the paragraph that draws it, so stacking a plain
    * `line(...)` above a `line(...).rightAligned` in one `column` puts a label on the left and a total on the right
    * with no hand-counted padding in between.
    */
  def aligned(alignment: Alignment): LineElement = copy(align = Some(alignment))

  /** This row pinned to the left edge — the default, spelled out. */
  def leftAligned: LineElement = aligned(Alignment.Left)

  /** This row centred in the columns it is given. */
  def centered: LineElement = aligned(Alignment.Center)

  /** This row pinned to the right edge, which is what a column of numbers wants. */
  def rightAligned: LineElement = aligned(Alignment.Right)

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
    direction: w.SparkDirection = w.SparkDirection.LeftToRight,
    props: ElementProps = ElementProps(),
) extends Element:
  type Self = SparklineElement
  def widget: Widget = w.Sparkline(data, max, direction, props.style)

  /** Pins the top of the scale instead of letting it float to the largest value present.
    *
    * Without this a sparkline always uses the full row height, which is what makes two of them *not* comparable: a
    * series peaking at 5 and one peaking at 5000 draw the same shape. Pin both to the same ceiling and the heights mean
    * the same thing.
    */
  def max(ceiling: Long): SparklineElement = copy(max = Some(ceiling))

  /** Anchors the series to the right edge, so the newest reading is always in the last column.
    *
    * By default a sparkline keeps the oldest points and clips the newest off the right, which is the wrong end for a
    * live metric: as readings arrive the one you care about is the one that disappears. With this the history scrolls
    * off the left instead and the latest value stays put, so the caller no longer has to trim the window by hand.
    */
  def rightToLeft: SparklineElement = copy(direction = w.SparkDirection.RightToLeft)

  /** Anchors the series to either edge — see [[rightToLeft]] for why that matters. */
  def direction(anchor: w.SparkDirection): SparklineElement = copy(direction = anchor)

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

/** A static table of rows under fixed column widths.
  *
  * A [[FlexContainer]], so the alignment builders every `row` and `column` has work here too — `.center` and `.flexEnd`
  * place the block of columns inside the area when fixed-width columns leave space over, and `.gap(n)` sets the blank
  * cells between neighbouring columns. Its "children" are its columns rather than nested elements, which is why it
  * carries the trait without carrying a `children` list.
  */
final case class TableElement(
    rows: Seq[Seq[String]],
    widths: Seq[Constraint],
    header: Option[Seq[String]] = None,
    footer: Option[Seq[String]] = None,
    columnSpacing: Int = 1,
    flex: Flex = Flex.Start,
    props: ElementProps = ElementProps(),
) extends FlexContainer:
  type Self = TableElement
  def widget: Widget =
    w.Table.ofStrings(rows, widths, header, footer, columnSpacing, flex, props.style)

  /** This table rebuilt with the leftover width placed differently.
    *
    * Fixed-width columns — `Constraint.Length(8)` and friends — can add up to less than the space the table was given.
    * That leftover always used to trail off the right-hand side, because the widget passed no flex to the layout at
    * all. `Flex.Center` centres the block of columns instead and `Flex.End` pushes it right. It changes nothing when a
    * `Fill` or `Min` column is already soaking up the slack, because then there is no leftover to place.
    */
  def withFlex(mode: Flex): TableElement = copy(flex = mode)

  /** This table rebuilt with `cells` blank columns between neighbouring columns (negative counts clamp to zero). */
  def withSpacing(cells: Int): TableElement = copy(columnSpacing = math.max(0, cells))

  /** Adds a bold caption row above the data — one label per column, in the same order as `widths`.
    *
    * The header costs one row of the area and does not scroll away with the rows, because a static table does not
    * scroll at all. Pass a computed sequence with `.header(labels*)`.
    */
  def header(labels: String*): TableElement = copy(header = Some(labels))

  /** Adds a bold summary row pinned to the *bottom* of the table's area — one cell per column.
    *
    * Pinned, not appended: a totals row that followed the last data row would float in the middle of a pane the rows do
    * not fill. It is laid out on the same solved column widths as the body, which is the thing a second `table` stacked
    * underneath could not promise — that one had to repeat the width constraints, and drifted the moment they changed.
    * It costs one row of the area, like `.header(...)`.
    */
  def footer(labels: String*): TableElement = copy(footer = Some(labels))

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
