package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Constraint, Direction, Size, Span, Style, Text, Widget}
import io.worxbend.tui.runtime.{ReactiveScope, Signal}
import io.worxbend.tui.widgets as w

import scala.concurrent.duration.FiniteDuration

/** The factory set behind `object Element`, which mixes it in and adds nothing else.
  *
  * It is a trait rather than the object itself for one mechanical reason: a companion object has to share a file with
  * its trait, and `trait Element` plus 350 lines of factories in one file is what this split exists to undo. Call
  * everything here as `Element.text(...)`, or unqualified after `import io.worxbend.tui.dsl.*` — the package re-exports
  * every factory.
  *
  * A factory that takes a `Signal` also takes a [[ReactiveScope]] and reads the signal *through* it, so the view that
  * built the element is subscribed to the value the control draws. The node then holds a plain value plus a writer,
  * which is why a background task setting that signal repaints the control instead of leaving it stale until the next
  * unrelated keystroke.
  */
private[dsl] trait ElementFactories:

  def text(content: String): TextElement = TextElement(content)

  /** One terminal row assembled from differently-styled runs:
    *
    * {{{
    * line("Status: ".styled(identity), "OK".styled(_.withFg(Color.Green)))
    * }}}
    *
    * `"...".styled(...)` is the [[Span]] builder this package adds to `String`. Use this instead of a `row` of `text`
    * elements with hand-counted `.length(n)` widths: the row is measured in display columns, so it stays correct when
    * the text is translated or contains CJK or emoji characters that occupy two columns each.
    */
  def line(parts: Span*): LineElement = LineElement(parts)

  def panel(title: String)(children: Element*): PanelElement = PanelElement(Some(title), children)

  def panel(children: Element*): PanelElement = PanelElement(None, children)

  def row(children: Element*): RowElement = RowElement(children)

  def column(children: Element*): ColumnElement = ColumnElement(children)

  /** Flexible blank space (fills what siblings leave over). */
  def spacer: SpacerElement = SpacerElement()

  /** Fixed blank space of exactly `cells` rows/columns. */
  def spacer(cells: Int): SpacerElement =
    SpacerElement(ElementProps(constraint = Some(Constraint.Length(cells))))

  def gauge(ratio: Double): GaugeElement = GaugeElement(ratio)

  def sparkline(data: Seq[Long]): SparklineElement = SparklineElement(data)

  def tabs(titles: Seq[String], selected: Int = 0): TabsElement = TabsElement(titles, selected)

  def table(rows: Seq[Seq[String]], widths: Constraint*): TableElement =
    TableElement(rows, widths)

  def widget(wrapped: Widget): WidgetElement = WidgetElement(wrapped)

  def input(state: w.TextInputState, placeholder: String = ""): InputElement =
    InputElement(state, placeholder)

  /** A labelled checkbox over a caller-owned `Signal`. The signal is read tracked, so any writer repaints it. */
  def checkbox(label: String, checked: Signal[Boolean])(using ReactiveScope): CheckboxElement =
    CheckboxElement(label, checked.get, checked.set)

  /** A labelled on/off switch over a caller-owned `Signal`. */
  def toggle(label: String, on: Signal[Boolean])(using ReactiveScope): ToggleElement =
    ToggleElement(label, on.get, on.set)

  /** A one-row option cycler over a caller-owned selection index. */
  def select(options: Seq[String], selected: Signal[Int])(using ReactiveScope): SelectElement =
    SelectElement(options, selected.get, selected.set)

  def list(items: Seq[String], state: w.ListState): ListElement =
    ListElement(items, state)

  def tree(nodes: Seq[w.TreeNode], state: w.TreeState): TreeElement =
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

  /** An animation frame indicator. Needs a `config.tickRate` to animate and nothing else — it reads the ambient
    * [[AnimationClock]], so there is no counter to declare, advance, or thread through.
    *
    * Colors come from the ambient [[Theme]]'s [[LoadingTheme]]; the animation from [[w.SpinnerPreset]], swappable with
    * `.preset(...)`.
    */
  def spinner(label: String = "")(using theme: Theme, scope: ReactiveScope): SpinnerElement =
    spinnerAt(AnimationClock.elapsed, label)

  /** A spinner on a clock the caller drives, for a progress animation tied to something other than wall time. */
  def spinnerAt(elapsed: FiniteDuration, label: String = "")(using theme: Theme): SpinnerElement =
    SpinnerElement(elapsed, label, w.SpinnerPreset.Dots, theme.loading.spinner, theme.loading.label)

  /** Text carrying a time-based effect, on the ambient [[AnimationClock]].
    *
    * Defaults to a travelling highlight; `.effect(...)` swaps in a typewriter, a scrolling gradient, a shimmer, or a
    * bounce. Colors come from the ambient [[Theme]].
    */
  def animatedText(content: String)(using theme: Theme, scope: ReactiveScope): AnimatedTextElement =
    animatedTextAt(content, AnimationClock.elapsed)

  /** Animated text on a clock the caller drives. */
  def animatedTextAt(content: String, elapsed: FiniteDuration)(using theme: Theme): AnimatedTextElement =
    AnimatedTextElement(content, elapsed, w.TextEffect.Wave(), theme.muted, theme.accent)

  /** One styled message line: an icon, an optional timestamp, and the message. Colors follow the level. */
  def notice(message: String, level: w.NoticeLevel = w.NoticeLevel.Info)(using theme: Theme): NoticeElement =
    NoticeElement(message, level, None, theme.primary, noticeStyle(level), theme.muted)

  /** A short inline label. Defaults to a solid badge in the theme's accent; `.outline` and `.dot` are quieter. */
  def badge(label: String)(using theme: Theme): BadgeElement =
    BadgeElement(label, w.BadgeVariant.Solid, theme.accent)

  /** A badge carrying a severity's own tag and color — `badge(NoticeLevel.Error)` reads `FAIL`. */
  def badge(level: w.NoticeLevel)(using theme: Theme): BadgeElement =
    BadgeElement(level.tag, w.BadgeVariant.Solid, noticeStyle(level))

  private def noticeStyle(level: w.NoticeLevel)(using theme: Theme): Style =
    level match
      case w.NoticeLevel.Success => theme.success
      case w.NoticeLevel.Info    => theme.accent
      case w.NoticeLevel.Warning => theme.warning
      case w.NoticeLevel.Error   => theme.error

  def dialog(title: String, message: String, buttons: Seq[String] = Seq("OK"), selected: Int = 0): WidgetElement =
    WidgetElement(w.Dialog(title, Text.raw(message), buttons, selected))

  def dualSparkline(upper: Seq[Long], lower: Seq[Long]): WidgetElement =
    WidgetElement(w.DualSparkline(upper, lower))

  /** A pulsing placeholder for content that has not arrived yet, on the ambient [[AnimationClock]]. */
  def skeleton()(using theme: Theme, scope: ReactiveScope): SkeletonElement =
    skeletonAt(AnimationClock.elapsed)

  /** A skeleton on a clock the caller drives. */
  def skeletonAt(elapsed: FiniteDuration)(using theme: Theme): SkeletonElement =
    SkeletonElement(elapsed, theme.loading.track, theme.loading.band)

  /** A figure with an arc chasing round it — a spinner big enough to fill a pane. Needs a `config.tickRate` and nothing
    * else: it reads the ambient [[AnimationClock]], so there is no counter to declare or thread through.
    *
    * Defaults to a circle fitted to its area with a quarter of it lit as a fading comet; `.radius(n)` pins the size,
    * `.path(OrbitPath.Square)` squares it off, `.markers("*")` drops it to an ASCII-safe cell grid. Colors come from
    * the ambient [[Theme]]'s [[LoadingTheme]]: the resting path is the track, the arc is the spinner.
    */
  def orbitSpinner()(using theme: Theme, scope: ReactiveScope): OrbitSpinnerElement =
    orbitSpinnerAt(AnimationClock.elapsed)

  /** An orbit spinner on a clock the caller drives. */
  def orbitSpinnerAt(elapsed: FiniteDuration)(using theme: Theme): OrbitSpinnerElement =
    OrbitSpinnerElement(elapsed, theme.loading.track, theme.loading.spinner)

  /** A head travelling a one-cell track, on the ambient [[AnimationClock]] — the row-or-column-shaped member of the
    * family, for a status line under a log pane or a column beside one.
    */
  def linearSpinner()(using theme: Theme, scope: ReactiveScope): LinearSpinnerElement =
    linearSpinnerAt(AnimationClock.elapsed)

  /** A linear spinner on a clock the caller drives. */
  def linearSpinnerAt(elapsed: FiniteDuration)(using theme: Theme): LinearSpinnerElement =
    LinearSpinnerElement(elapsed, theme.loading.track, theme.loading.spinner)

  /** A block of phase-offset spinners, on the ambient [[AnimationClock]]. `.preset(...)` picks the per-slot animation
    * from the ordinary spinner catalogue — including the ASCII ones — and `.phase(...)` decides whether the block
    * pulses, waves, or ripples.
    */
  def spinnerGrid()(using theme: Theme, scope: ReactiveScope): SpinnerGridElement =
    spinnerGridAt(AnimationClock.elapsed)

  /** A spinner grid on a clock the caller drives. */
  def spinnerGridAt(elapsed: FiniteDuration)(using theme: Theme): SpinnerGridElement =
    SpinnerGridElement(elapsed, w.SpinnerPreset.DotsRing, w.GridPhase.Diagonal(), theme.loading.spinner)

  /** A progress bar for work of unknown length, on the ambient [[AnimationClock]].
    *
    * Defaults to a bouncing segment — `.motion(...)` swaps in sweep, comet, or the quieter in-place pulse.
    */
  def indeterminateBar()(using theme: Theme, scope: ReactiveScope): IndeterminateElement =
    indeterminateBarAt(AnimationClock.elapsed)

  /** An indeterminate bar on a clock the caller drives. */
  def indeterminateBarAt(elapsed: FiniteDuration)(using theme: Theme): IndeterminateElement =
    IndeterminateElement(
      elapsed,
      w.IndeterminateMotion.Bounce,
      w.ProgressStyle.Line,
      theme.loading.track,
      theme.loading.fill,
      props = ElementProps(constraint = Some(Constraint.Length(1))),
    )

  /** A one-row determinate progress bar: a caption then a filled track.
    *
    * `ratio` is clamped to `[0, 1]`. The glyphs come from [[w.ProgressStyle]] — the default steps whole cells, and
    * `.preset(ProgressStyle.Blocks)` moves smoothly with sub-cell partials.
    */
  def progressBar(ratio: Double)(using theme: Theme): ProgressBarElement =
    ProgressBarElement(
      ratio,
      w.ProgressLabel.Percentage,
      w.ProgressStyle.Line,
      theme.loading.track,
      theme.loading.fill,
      theme.loading.fillRamp,
      ElementProps(constraint = Some(Constraint.Length(1))),
    )

  /** `progressBar` for counts rather than a fraction: `progressBar(3, 10)` is a 30% bar. */
  def progressBar(current: Int, total: Int)(using Theme): ProgressBarElement =
    progressBar(if total <= 0 then 0.0 else current.toDouble / total)

  def autocomplete(
      state: AutocompleteState,
      suggestions: Seq[String],
      onAccept: String => Unit = _ => (),
  ): AutocompleteElement =
    AutocompleteElement(state, suggestions, onAccept)

  /** A file chooser over an app-owned [[FilePickerState]]. The accepted path is read tracked, so accepting one — or
    * setting `state.chosen` from anywhere else — repaints the footer line.
    */
  def filePicker(state: FilePickerState)(using ReactiveScope): FilePickerElement =
    FilePickerElement(state, state.chosen.get)

  /** Mutually exclusive options over a caller-owned selection index. */
  def radioGroup(options: Seq[String], selected: Signal[Int])(using ReactiveScope): RadioGroupElement =
    RadioGroupElement(options, selected.get, selected.set)

  /** A value slider over a caller-owned `Signal`; `range` carries the bounds and the per-press step together — build
    * one with `SliderRange.of(min, max, step)`.
    */
  def slider(value: Signal[Int], range: w.SliderRange = w.SliderRange.Percent)(using
      ReactiveScope
  ): SliderElement =
    SliderElement(value.get, value.set, range)

  /** A multi-select list: `selected` holds the chosen row indices. */
  def selectionList(
      items: Seq[String],
      selected: Signal[Set[Int]],
      state: w.ListState,
  )(using ReactiveScope): SelectionListElement =
    SelectionListElement(
      items,
      selected.get,
      row => selected.update(current => if current.contains(row) then current - row else current + row),
      state,
    )

  /** A text input restricted to whole numbers; `.decimal` also accepts a single decimal point. */
  def numberInput(state: w.TextInputState): NumberInputElement =
    NumberInputElement(state)

  def maskedInput(state: w.TextInputState, mask: String): MaskedInputElement =
    MaskedInputElement(state, mask)

  /** A page indicator over a caller-owned page index. */
  def paginator(current: Signal[Int], total: Int)(using ReactiveScope): PaginatorElement =
    PaginatorElement(current.get, total, current.set)

  /** Scrolling ticker text, on the ambient [[AnimationClock]]. */
  def marquee(content: String)(using scope: ReactiveScope): WidgetElement =
    marqueeAt(content, AnimationClock.elapsed)

  /** A marquee on a clock the caller drives. */
  def marqueeAt(content: String, elapsed: FiniteDuration): WidgetElement =
    WidgetElement(
      w.Marquee(content, elapsed),
      ElementProps(constraint = Some(Constraint.Length(1))),
    )

  def image(source: w.Image): WidgetElement =
    WidgetElement(source)

  def link(label: String, url: String): WidgetElement =
    WidgetElement(
      w.Link(label, url),
      ElementProps(constraint = Some(Constraint.Length(1))),
    )

  def markdown(source: String): WidgetElement =
    WidgetElement(w.Markdown(source))

  def dataTable(
      table: w.DataTable,
      state: w.DataTableState,
  ): DataTableElement =
    DataTableElement(table, state)

  def directoryTree(state: w.DirectoryTreeState): DirectoryTreeElement =
    DirectoryTreeElement(state)

  def textArea(state: w.TextAreaState): TextAreaElement =
    TextAreaElement(state)

  def button(label: String)(action: => Unit): ButtonElement =
    ButtonElement(label, () => action)

  /** Later layers paint over earlier ones across the full area. */
  def layers(base: Element, overlays: Element*): LayersElement =
    LayersElement(base +: overlays)

  /** Picks a subtree from the terminal's size, re-evaluated on every resize.
    *
    * {{{
    * responsive {
    *   case size if size.width < 60 => column(header, tabbedContent(pages, active))
    *   case _                       => row(sidebar.percent(25), detail.fill)
    * }
    * }}}
    *
    * The size is the whole terminal's, the same one [[TuiApp.terminalSize]] reports — nesting this inside a `panel` or
    * a `splitPane` does not narrow what `build` sees. Branch on [[Breakpoint.of]] instead of raw columns when the named
    * bands say what you mean.
    */
  def responsive(build: Size => Element): ResponsiveElement =
    ResponsiveElement(build)

  def scrollView(
      content: Element,
      contentHeight: Int,
      state: w.ScrollViewState,
  ): ScrollViewElement =
    ScrollViewElement(content, Some(contentHeight), state)

  /** Scroll view that measures its content's height itself (falls back to the viewport height when the content is
    * unmeasurable — fill-sized children).
    */
  def scrollView(content: Element, state: w.ScrollViewState): ScrollViewElement =
    ScrollViewElement(content, contentHeight = None, state)

  /** `tabbedContent("One" -> pageOne, "Two" -> pageTwo)(selected)` — the selected page is picked at view construction,
    * so the tree always holds exactly the visible page.
    */
  def tabbedContent(pages: (String, Element)*)(selected: Signal[Int])(using ReactiveScope): TabbedContentElement =
    val index  = math.max(0, math.min(selected.get, pages.size - 1))
    val active = if pages.isEmpty then Element.text("") else pages(index)._2
    TabbedContentElement(pages.map(_._1), active, index, selected.set, pages.size)

  /** A section that folds away; `expanded` is caller-owned so the app can open or close it itself. */
  def collapsible(title: String, expanded: Signal[Boolean])(body: Element)(using
      ReactiveScope
  ): CollapsibleElement =
    CollapsibleElement(title, body, expanded.get, expanded.set)

  /** Two panes divided by a draggable split. `axis` is the axis the panes are laid out along: [[Direction.Horizontal]]
    * puts them side by side, [[Direction.Vertical]] stacks them.
    */
  def splitPane(
      first: Element,
      second: Element,
      splitPercent: Signal[Int],
      axis: Direction = Direction.Horizontal,
  )(using ReactiveScope): SplitPaneElement =
    SplitPaneElement(first, second, splitPercent.get, splitPercent.set, axis)

  def log(state: w.LogState): LogElement =
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

  /** A menu / dropdown / context-menu popup over `items`; `onSelect` fires with the index chosen by Enter or a click.
    */
  def menu(items: Seq[w.MenuEntry], state: w.MenuState)(onSelect: Int => Unit): MenuElement =
    MenuElement(items, state, onSelect)

  /** A bordered popup of help text sized to its content — overlay it near what it describes (see [[positioned]]). */
  def tooltip(text: String): WidgetElement =
    val tip = w.Tooltip(text)
    // the constraint is what makes a container hand the tooltip exactly its own rows rather than a share of the area
    WidgetElement(tip, ElementProps(constraint = tip.heightAt(0).map(Constraint.Length.apply)))

  /** Places `content` at an absolute `(dx, dy)` offset inside its area, sized `width` x `height`. */
  def positioned(dx: Int, dy: Int, width: Int, height: Int)(content: Element): PositionedElement =
    PositionedElement(dx, dy, width, height, content)
