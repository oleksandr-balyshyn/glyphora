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

  /** A block of plain text; one row per newline-separated line, clipped at the area's edges. Style it with the usual
    * extensions: `text("hi").bold.fg(Color.Cyan)`. For a single row assembled from differently-styled runs, use
    * [[line]] instead — it measures in display columns rather than needing hand-counted widths.
    */
  def text(content: String): TextElement = TextElement(content)

  /** One terminal row assembled from differently-styled runs:
    *
    * {{{
    * line("Status: ", "OK".styled(_.withFg(Color.Green)))
    * }}}
    *
    * A part is either a plain `String`, drawn in the element's own style, or a [[Span]] built with the
    * `"...".styled(...)` extension this package adds to `String`; the two mix freely in one call, the same shape
    * `listView` takes for its items. Before the `String` case existed an unstyled run had to be spelled
    * `"Status: ".styled(identity)`, which is ceremony on the commonest part of any row.
    *
    * Use this instead of a `row` of `text` elements with hand-counted `.length(n)` widths: the row is measured in
    * display columns, so it stays correct when the text is translated or contains CJK or emoji characters that occupy
    * two columns each.
    */
  def line(parts: (String | Span)*): LineElement =
    LineElement(parts.map {
      case content: String => Span.raw(content)
      case span: Span      => span
    })

  /** A bordered box around `children`, captioned `title`.
    *
    * The frame is drawn in the ambient [[Theme]]'s `border` style and the caption in its `primary` style, so a panel
    * looks like the rest of the app without being told to. `.fg(...)`/`.styled(...)` recolour the frame;
    * `.titleStyle(...)` recolours only the caption.
    */
  def panel(title: String)(children: Element*)(using theme: Theme): PanelElement =
    PanelElement(Some(title), children, titleStyle = Some(theme.primary), props = ElementProps(style = theme.border))

  /** An untitled bordered box — see [[panel(title:String)*]] for the styling. */
  def panel(children: Element*)(using theme: Theme): PanelElement =
    PanelElement(None, children, props = ElementProps(style = theme.border))

  /** Lays `children` out left to right, splitting the width between them.
    *
    * Each child's share comes from the layout extension it carries — `.length(n)`, `.percent(n)`, `.fill`,
    * `.minSize(n)`, `.maxSize(n)` — and a child that carries none claims its natural width. `.gap(n)` inserts blank
    * columns between neighbours and `.center` / `.spaceBetween` / `.spaceEvenly` decide where leftover space goes (see
    * [[io.worxbend.tui.core.Flex]]: leftover only exists when no child is greedily filling).
    */
  def row(children: Element*): RowElement = RowElement(children)

  /** Lays `children` out top to bottom, splitting the height between them. The same constraint and flex vocabulary as
    * [[row]], one axis over.
    */
  def column(children: Element*): ColumnElement = ColumnElement(children)

  /** Flexible blank space (fills what siblings leave over). */
  def spacer: SpacerElement = SpacerElement()

  /** Fixed blank space of exactly `cells` rows/columns. */
  def spacer(cells: Int): SpacerElement =
    SpacerElement(ElementProps(constraint = Some(Constraint.Length(cells))))

  /** A one-row filled bar with a caption written across it, filling the whole area height.
    *
    * `ratio` is a fraction in `[0, 1]` — 0 empty, 1 full — and is clamped, so an out-of-range value is a visual bug
    * rather than a crash. `NaN` reads as no progress at all. For counts rather than a fraction, divide, or reach for
    * `progressBar(current, total)`.
    *
    * The caption defaults to the percentage; `.label("…")`, `.labelled("…")` and `.bare` change it, and `.ramp(...)`
    * colours the fill by how far along it is. See [[w.Gauge]].
    */
  def gauge(ratio: Double)(using theme: Theme): GaugeElement =
    GaugeElement(
      ratio,
      w.ProgressLabel.Percentage,
      theme.loading.track,
      theme.loading.fill,
      theme.loading.fillRamp,
    )

  /** A one-row dense chart: one column per data point, oldest on the left, drawn with the eight block glyphs.
    *
    * The scale runs from zero to the largest value present, so a series is always drawn using the full row height and
    * two sparklines side by side are *not* comparable unless you pin the ceiling with `.max(n)`. Points that do not fit
    * the width are clipped on the right. See [[w.Sparkline]].
    */
  def sparkline(data: Seq[Long]): SparklineElement = SparklineElement(data)

  /** A one-row tab strip with `selected` highlighted. Purely presentational — it draws the titles and nothing else. For
    * tabs that actually switch content, use [[tabbedContent]].
    */
  def tabs(titles: Seq[String], selected: Int = 0): TabsElement = TabsElement(titles, selected)

  /** A static table: one element of `rows` per line, one `widths` constraint per column.
    *
    * Columns are solved by the same [[io.worxbend.tui.core.Constraint]] vocabulary as [[row]], and cells are clipped,
    * never wrapped. Only the visible rows are rendered, so handing it ten thousand rows costs the height of the area,
    * not the length of the list. Add a header with `.header(...)`.
    *
    * This one is a picture. For sorting, filtering, paging and a selection, use [[dataTable]].
    */
  def table(rows: Seq[Seq[String]], widths: Constraint*): TableElement =
    TableElement(rows, widths)

  /** Any `tui-core` [[Widget]] as a leaf element — the escape hatch down a level when a widget knob has no DSL builder
    * yet, or when the widget is your own.
    *
    * `Widget` is a SAM type, so a lambda is enough: `widget((area, buffer) => …)`. The element's own style is *not*
    * applied — the wrapped widget draws exactly what it draws.
    *
    * '''Say how big it is.''' A wrapped widget claims *all* the space its container has left over, because a bare
    * `Widget` has no way to say otherwise — implementing [[io.worxbend.tui.core.Measured]] on it does not change this,
    * as that is consulted by the scroll-view measurement pass and not by the layout solver. So
    * `column(text("a"), widget(oneRowThing), text("b"))` gives the custom widget every row the two captions did not
    * take. Say the height with `.rows(n)`: `widget(oneRowThing).rows(1)` is one row tall and full width in a `column`
    * *and* in a `row`. Reach for `.length(n)` only when the number really is the container'''s decision, because that
    * extension applies along whichever axis the container runs — in a `row`, `.length(1)` is one column wide.
    */
  def widget(wrapped: Widget): WidgetElement = WidgetElement(wrapped)

  /** A single-line text field over caller-owned [[w.TextInputState]], showing `placeholder` while empty.
    *
    * Create the state once, outside `view` — a `TextInputState()` built inside the view is a new empty editor on every
    * frame, which is the "my input keeps resetting" bug. It is render-thread-only, and writing to it from outside an
    * event handler does not by itself schedule a frame: see [[w.TextInputState]].
    *
    * Editing keys (typing, arrows, Home/End, Backspace/Delete) are handled while focused; `Enter` is left unconsumed so
    * the enclosing app can decide what submitting means.
    */
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

  /** A scrollable single-selection list over caller-owned [[w.ListState]].
    *
    * The state must be created once, outside `view` — a `ListState` built inside the view would be a fresh one every
    * frame and the selection would never move. It is a plain mutable object read and written on the render thread only,
    * and mutating it does not by itself schedule a frame: see [[w.ListState]].
    *
    * The selected row is drawn in the app [[Theme]]'s `focus` style, which the focus pass stamps onto every element —
    * focused or not — so a list keeps the app's highlight even while the keyboard is somewhere else.
    */
  def list(items: Seq[String], state: w.ListState): ListElement =
    ListElement(items, state)

  /** A collapsible tree over caller-owned [[w.TreeState]] — same state ownership rules as [[list]]. */
  def tree(nodes: Seq[w.TreeNode], state: w.TreeState): TreeElement =
    TreeElement(nodes, state)

  /** Vertical bars, one per `(label, value)`, each `barWidth` columns wide with the label underneath.
    *
    * Bars are scaled against the largest value in `data`, so the tallest always reaches the top of the area; the top of
    * each bar uses a partial block glyph for sub-cell precision. See [[w.BarChart]] for the styling and gap knobs.
    */
  def barChart(data: Seq[(String, Long)], barWidth: Int = 3): WidgetElement =
    WidgetElement(w.BarChart(data, barWidth))

  /** An x/y plot of one or more [[w.Dataset]]s with axes, over an explicit world window.
    *
    * `xBounds` and `yBounds` are `(min, max)` in the data's own units — they are *not* derived from the points, so a
    * series outside the window is simply not drawn. A `GraphType.Line` dataset joins its points in the order they are
    * listed rather than sorting by x, so an unsorted series draws as a zig-zag.
    */
  def chart(datasets: Seq[w.Dataset], xBounds: (Double, Double), yBounds: (Double, Double)): WidgetElement =
    WidgetElement(w.Chart(datasets, xBounds, yBounds))

  /** A free-form drawing surface: shapes describe themselves in world coordinates (`xBounds` increasing rightward,
    * `yBounds` increasing *upward*, unlike buffer coordinates) and the canvas maps them onto the cell grid. See
    * [[w.Canvas]] for the sub-cell resolutions (cell, half-block, braille).
    */
  def canvas(xBounds: (Double, Double), yBounds: (Double, Double))(shapes: w.Shape*): WidgetElement =
    WidgetElement(w.Canvas(xBounds, yBounds, shapes))

  /** A month grid for `month` (1–12) of `year`, weeks starting Monday, with `selected` (a day of the month)
    * highlighted. Needs 20 columns and up to 8 rows; anything smaller clips. See [[w.Calendar]].
    */
  def calendar(year: Int, month: Int, selected: Option[Int] = None): WidgetElement =
    WidgetElement(w.Calendar(year, month, selected))

  /** A filled pie with a legend: each `(label, value)` gets a sector proportional to its share of the total, so the
    * values are read as parts of a whole and need no normalising. Non-positive values are ignored. The disc corrects
    * for the cell aspect ratio so it looks round rather than oval. See [[w.PieChart]].
    */
  def pieChart(data: Seq[(String, Double)]): WidgetElement =
    WidgetElement(w.PieChart(data))

  /** [[barChart]] with each column split into one segment per series: `(label, values)` stacks `values` bottom-up.
    * Columns are scaled against the tallest *stack*, so the columns are comparable to each other. See
    * [[w.StackedBarChart]].
    */
  def stackedBarChart(data: Seq[(String, Seq[Long])], barWidth: Int = 3): WidgetElement =
    WidgetElement(w.StackedBarChart(data, barWidth))

  /** A value grid drawn as shade intensity — outer sequence is rows (top first), inner is columns.
    *
    * Each cell is shaded by its value *relative to the largest value in the whole grid*, so the scale is per-heatmap
    * and two heatmaps are not comparable to each other. An all-zero (or empty) grid renders nothing. See [[w.Heatmap]].
    */
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

  /** A centered modal box: a title, a message, and a row of buttons with `selected` highlighted.
    *
    * A picture of a dialog, not a controller — it has no keys and no callbacks of its own. Drive `selected` from the
    * app's own state and overlay it with [[layers]] (or push it as a modal [[Screen]]). Colours come from the ambient
    * [[Theme]]: the frame and text in `primary`, the selected button in `focus`.
    */
  def dialog(title: String, message: String, buttons: Seq[String] = Seq("OK"), selected: Int = 0)(using
      theme: Theme
  ): WidgetElement =
    WidgetElement(
      w.Dialog(title, Text.raw(message), buttons, selected, style = theme.primary, highlightStyle = theme.focus)
    )

  /** Two sparklines sharing one area — `upper` in the top half, `lower` in the bottom — for comparing a pair of series
    * such as ingress against egress. Each series is scaled independently, so the shapes are comparable but the heights
    * are not. See [[w.DualSparkline]].
    */
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
      w.ProgressPreset.Line,
      theme.loading.track,
      theme.loading.fill,
    )

  /** A one-row determinate progress bar: a caption then a filled track.
    *
    * `ratio` is clamped to `[0, 1]`. The glyphs come from [[w.ProgressPreset]] — the default steps whole cells, and
    * `.preset(ProgressPreset.Blocks)` moves smoothly with sub-cell partials.
    */
  def progressBar(ratio: Double)(using theme: Theme): ProgressBarElement =
    ProgressBarElement(
      ratio,
      w.ProgressLabel.Percentage,
      w.ProgressPreset.Line,
      theme.loading.track,
      theme.loading.fill,
      theme.loading.fillRamp,
    )

  /** `progressBar` for counts rather than a fraction: `progressBar(3, 10)` is a 30% bar. */
  def progressBar(current: Int, total: Int)(using Theme): ProgressBarElement =
    progressBar(if total <= 0 then 0.0 else current.toDouble / total)

  /** A text field with a filtered suggestion list under it; `onAccept` fires with the chosen suggestion.
    *
    * `suggestions` is the full candidate list — the element filters it against what has been typed, so the caller does
    * not have to. The [[AutocompleteState]] is caller-owned and must be created once outside `view`; the content-then-
    * state order is the one every list-like factory here uses (`list(items, state)`, `menu(items, state)`).
    */
  def autocomplete(
      suggestions: Seq[String],
      state: AutocompleteState,
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

  /** A text field that renders every character as `mask` (a password box: `maskedInput(state, "•")`). The state still
    * holds the real text — this hides it on screen, it does not encrypt or protect it. Caller-owned state, created once
    * outside `view`, as for [[input]].
    */
  def maskedInput(state: w.TextInputState, mask: String): MaskedInputElement =
    MaskedInputElement(state, mask)

  /** A page indicator over a caller-owned page index. */
  def paginator(current: Signal[Int], total: Int)(using ReactiveScope): PaginatorElement =
    PaginatorElement(current.get, total, current.set)

  /** Scrolling ticker text, on the ambient [[AnimationClock]] — one row, wrapping round with a run of blanks between
    * repetitions.
    *
    * `.speed(n)` sets the reading rate in cells per second (`.period(d)` says the same thing as a lap time) and
    * `.gap(n)` widens the run of blanks between laps.
    */
  def marquee(content: String)(using scope: ReactiveScope): MarqueeElement =
    marqueeAt(content, AnimationClock.elapsed)

  /** A marquee on a clock the caller drives. */
  def marqueeAt(content: String, elapsed: FiniteDuration): MarqueeElement =
    MarqueeElement(content, elapsed)

  /** A raster image drawn with half-block cells, so one cell shows two vertical pixels. Scaled to the area by
    * nearest-neighbour sampling — pre-scale the pixels if quality matters. See [[w.Image]] for how to build one.
    */
  def image(source: w.Image): WidgetElement =
    WidgetElement(source)

  /** A one-row clickable hyperlink (OSC 8): `label` underlined with `url` attached. Terminals without OSC 8 support
    * show the styled text and nothing is lost.
    */
  def link(label: String, url: String): WidgetElement =
    WidgetElement(w.Link(label, url), rows = Some(1))

  /** Renders a pragmatic Markdown subset — headings, bullets, quotes, fenced (and syntax-highlighted) code, inline
    * emphasis and links. Prose always wraps at the available width; the node reports its own wrapped height, so it
    * measures correctly inside a [[scrollView]].
    *
    * Styling comes from the ambient [[Theme]]'s `markdown` palette, which is why `Theme.Light` renders a document in
    * dark-on-white rather than the widget-level defaults' cyan-on-black. See [[w.Markdown]] for exactly which
    * constructs are supported.
    */
  def markdown(source: String)(using theme: Theme): WidgetElement =
    WidgetElement(w.Markdown(source, theme.markdown))

  /** A sortable, filterable, pageable table: `table` is the data and column definitions, `state` is the view over it.
    *
    * The state must be created once outside `view` — it holds the sort, the substring filter, the selection and the
    * scroll offset, and rebuilding it every frame would reset all four. It is render-thread-only and writing to it does
    * not by itself schedule a frame: see [[w.DataTableState]].
    *
    * Up/Down move the selection while focused, and PageUp/PageDown turn the page once `state.paging` is set. Sorting
    * and filtering have no built-in keys on purpose — drive `state.sortBy` / `state.setFilter` from the app's own
    * bindings, so the keys appear in the status bar and the palette.
    *
    * This is the one selection element whose highlight the theme does not reach: `table` is a widget value the caller
    * built, so its `highlightStyle` is the caller's to set and overriding it here would silently discard an explicit
    * choice. Pass `highlightStyle = theme.focus` when building the [[w.DataTable]] to line it up with `list` and the
    * rest.
    */
  def dataTable(
      table: w.DataTable,
      state: w.DataTableState,
  ): DataTableElement =
    DataTableElement(table, state)

  /** A filesystem browser rooted at the state's path, expanding branches on demand.
    *
    * Caller-owned state, created once outside `view`, render-thread-only. Note that expanding a branch reads the
    * directory *on the render thread*: harmless on a local disk, a visible stall on a network mount or a directory with
    * very many entries — [[w.DirectoryTreeState]] explains how to pre-warm the cache.
    */
  def directoryTree(state: w.DirectoryTreeState): DirectoryTreeElement =
    DirectoryTreeElement(state)

  /** A multi-line editor over caller-owned [[w.TextAreaState]], with undo (`Ctrl+Z`) and redo (`Ctrl+Y`).
    *
    * Create the state once outside `view`, as for [[input]]; it is render-thread-only, and writing to it from outside
    * an event handler does not by itself schedule a frame.
    */
  def textArea(state: w.TextAreaState): TextAreaElement =
    TextAreaElement(state)

  /** A focusable button; `action` runs on Enter or Space while focused, or on a click. The body is by-name, so it is
    * evaluated when the button is pressed rather than when the view is built.
    */
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

  /** A scrollable viewport over `content`, whose full height you state yourself as `contentHeight` rows.
    *
    * Use this overload when the content's height is known but not measurable — a list of `n` rows you generated, a
    * fixed-height diagram. The other overload measures the content itself. Getting `contentHeight` wrong does not
    * corrupt anything: too small and the tail is unreachable, too large and the view scrolls past the end into blanks.
    *
    * The [[w.ScrollViewState]] holds the offset and is caller-owned: create it once outside `view`. It is
    * render-thread-only, and writing to it does not by itself schedule a frame.
    */
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

  /** An append-only scrolling panel for build output, chat, or a live log, over caller-owned [[w.LogState]].
    *
    * The state holds a bounded ring of lines and follows the tail by default; scrolling up detaches, scrolling back to
    * the bottom re-attaches. Create it once outside `view`. Appending a line to it is a mutation the reactive layer
    * cannot see, so pair it with a `Signal` write or `requestRedraw()`.
    *
    * Up/Down and PageUp/PageDown scroll while focused; the wheel scrolls on hover.
    */
  def log(state: w.LogState): LogElement =
    LogElement(state)

  /** A one-row horizontal divider, optionally captioned. Drawn in the ambient [[Theme]]'s `border` style, with the
    * caption in `muted`, so it matches the frames around it.
    */
  def rule(label: String = "")(using theme: Theme): WidgetElement =
    WidgetElement(
      w.Rule(Option(label).filter(_.nonEmpty), style = theme.border, labelStyle = theme.muted),
      rows = Some(1),
    )

  /** Banner text drawn from a built-in 3x5 block font — the splash-screen and header building block.
    *
    * Covers A–Z, 0–9 and common punctuation; lowercase is folded to uppercase and anything unknown renders blank. The
    * node claims exactly the font's glyph height, so it does not need a `.length(...)`.
    */
  def bigText(content: String): WidgetElement =
    WidgetElement(w.BigText(content), rows = Some(w.BigText.GlyphHeight))

  /** A menu / dropdown / context-menu popup over `items`; `onSelect` fires with the index chosen by Enter or a click.
    */
  def menu(items: Seq[w.MenuEntry], state: w.MenuState)(onSelect: Int => Unit): MenuElement =
    MenuElement(items, state, onSelect)

  /** A bordered popup of help text sized to its content — overlay it near what it describes (see [[positioned]]). */
  def tooltip(text: String): WidgetElement =
    val tip = w.Tooltip(text)
    // the row claim is what makes a container hand the tooltip exactly its own height rather than a share of the area
    WidgetElement(tip, rows = tip.heightAt(0))

  /** Places `content` at an absolute `(dx, dy)` offset inside its area, sized `width` x `height`. */
  def positioned(dx: Int, dy: Int, width: Int, height: Int)(content: Element): PositionedElement =
    PositionedElement(dx, dy, width, height, content)
