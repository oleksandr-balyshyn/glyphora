---
title: Widget catalog
description: Choose, compose, configure, and test glyphora's layout, content, input, data, visualization, and feedback widgets.
---

# Widget catalog

glyphora ships more than forty backend-agnostic widgets. The high-level DSL wraps
each widget in an `Element`, adds focus and input behavior where appropriate, and
keeps mutable interaction state owned by your application.

Use this page to choose a widget and understand its state model. The
[Scaladoc API](pathname:///api/widgets/) remains the exact-signature reference.

## Choose by job

| Job | Start with | Add when needed |
|---|---|---|
| Arrange a screen | `row`, `column`, `panel`, `spacer`, `rule` | `scrollView`, `splitPane`, `layers` |
| Show prose or records | `text`, `list`, `table`, `tabs` | `markdown`, `log`, `dataTable`, `directoryTree` |
| Collect input | `input`, `checkbox`, `toggle`, `select`, `button` | `textArea`, `autocomplete`, `filePicker`, `selectionList`, `Form` |
| Show a metric | `gauge`, `sparkline` | `lineGauge`, `dualSparkline`, `barChart` |
| Plot data | `chart`, `pieChart`, `heatmap` | `stackedBarChart`, `canvas`, `calendar` |
| Communicate progress | `spinner`, `progressBar`, `orbitSpinner`, `skeleton`, `indeterminateBar` | `marquee`, `animatedText`, effects, toasts |
| Report an outcome | `notice`, `badge` | toasts, dialogs |
| Structure an app | `scaffold`, `topBar`, `sidebar`, `statusBar` | screens, menus, command palette, dialogs |

## The state ownership rule

Interactive widgets do not hide application state. You create the state once,
retain it across renders, and pass it into the element factory:

```scala
import io.worxbend.tui.dsl.*
import io.worxbend.tui.widgets.TextInputState

private val name = TextInputState()
private val notifications = Signal(true)
private val environment = Signal(0)

def settings(using ReactiveScope): Element =
  column(
    text("Project name").dim,
    input(name, placeholder = "glyphora-app"),
    toggle("Notifications", notifications),
    select(Seq("development", "staging", "production"), environment),
    button("Save") { save(name.value, notifications.peek, environment.peek) },
  ).gap(1)
```

The focused widget's built-in key handler mutates its state and requests a redraw.
Signals add tracked reactivity where another part of the tree also needs the value.

> Construct widget state **outside** `view`. Creating `TextInputState()`,
> `ListState()`, or `DataTableState()` inside `view` would reset editing, selection,
> and scrolling on every redraw.

## Layout and chrome

Core structural elements:

- `panel(title)(children*)` / `panel(children*)` — bordered vertical container;
- `row(children*)` / `column(children*)` — constrained layout containers;
- `spacer` / `spacer(cells)` — flexible or fixed blank space;
- `rule(label)` — horizontal divider;
- `scrollView(content, state)` — measured vertical viewport with wheel/key scrolling;
- `tabbedContent("Name" -> page, ...)(selected)` — tabs plus the selected page;
- `collapsible(title, expanded)(body)` — toggleable disclosure region;
- `splitPane(first, second, splitPercent)` — keyboard/mouse-resizable panes;
- `layers(base, overlays*)` — paint later elements over earlier ones;
- `menu`, `tooltip`, and `dialog` — transient interaction surfaces.

See [Layout & style](./layout-and-style) for constraints and [The app shell](./app-shell)
for application-level composition.

## Text, documents, and logs

```scala
column(
  text("Deployment complete").bold.color(Color.Green),
  text("8 services updated · 0 failed").dim,
  rule("release notes"),
  markdown(releaseNotes),
)
```

- `text` uses a grapheme-aware paragraph renderer.
- `markdown` supports headings, lists, quotes, fenced code, inline styles, and OSC 8
  links. Its DSL element reports width-dependent height to scroll containers.
- `log(LogState)` supports append-heavy output and follow-tail behavior.
- `link(label, url)` emits a clickable OSC 8 hyperlink when the terminal supports it.
- `bigText` renders banners; `image` renders raster data with half-block cells.

## Lists and navigation

```scala
import io.worxbend.tui.widgets.ListState

private val selected = ListState()
private val services = Signal(Vector("api", "worker", "scheduler"))

def serviceList(using ReactiveScope): Element =
  list(services.get, selected).onKey(Key.char('d')) {
    selected.selected.foreach { index =>
      services.update(_.patch(index, Nil, 1))
      selected.selected = None
    }
  }
```

Use `tree(nodes, TreeState)` for in-memory hierarchy and
`directoryTree(DirectoryTreeState(root))` for the filesystem. The directory tree
loads branches lazily, caches listings, and exposes `invalidate()` when outside code
changes a directory.

```scala
import io.worxbend.tui.widgets.DirectoryTreeState
import java.nio.file.Paths

private val files = DirectoryTreeState(Paths.get("."))

def browser: Element =
  panel("Files")(directoryTree(files)).rounded
```

## Tables: simple and interactive

Use `table` for static rows:

```scala
table(
  Seq(
    Seq("api", "ready", "3"),
    Seq("worker", "scaling", "8"),
  ),
  Constraint.Length(18),
  Constraint.Fill(1),
  Constraint.Length(6),
)
```

Use `DataTable` when users need sorting, filtering, selection, scrolling, or paging:

```scala
import io.worxbend.tui.widgets.{DataTable, DataTableState}

private val tableState = DataTableState()
private val deployments = DataTable(
  columns = Seq("Service", "Status", "Replicas"),
  rows = Seq(
    Seq("api", "ready", "3"),
    Seq("worker", "scaling", "8"),
  ),
  widths = Seq(Constraint.Fill(2), Constraint.Fill(1), Constraint.Length(8)),
)

def tableView: Element = dataTable(deployments, tableState)
```

`tableState.selected` indexes `deployments.visibleRows(tableState)`, not the original
unsorted data. Use that method when opening the selected record.

## Text editing

| Widget | State | Notes |
|---|---|---|
| `input` | `TextInputState` | one line, horizontal scrolling, paste folds newlines |
| `textArea` | `TextAreaState` | multiple lines, 2D cursor, scrolling, bounded undo/redo |
| `numberInput` | `TextInputState` | numeric key filtering; optional decimals |
| `maskedInput` | `TextInputState` | shows a mask rather than raw content |
| `autocomplete` | `AutocompleteState` | input plus selectable suggestions and accept callback |
| `filePicker` | `FilePickerState` | navigable file selection |

All editing and cursor movement is grapheme-cluster-aware. A Backspace removes one
visible cluster instead of one UTF-16 code unit.

## Data visualization

A tick-driven dashboard can remain compact:

```scala
import io.worxbend.tui.runtime.RunnerConfig
import io.worxbend.tui.widgets.{Dataset, GraphType}
import scala.concurrent.duration.*

override def config = RunnerConfig(tickRate = Some(100.millis))
private val tick = Signal(0)
override def onTick(): Unit = tick.update(_ + 1)

def dashboard(using ReactiveScope): Element =
  val t = tick.get
  val load = (math.sin(t * 0.1) + 1) / 2
  val samples = Vector.tabulate(40)(i =>
    (math.sin((t + i) * 0.25) * 40 + 50).toLong
  )
  val wave = Vector.tabulate(80)(i =>
    (i.toDouble, math.sin((t + i) * 0.1) * 40 + 50)
  )

  column(
    row(
      panel("Load")(gauge(load)).percent(40),
      panel("Throughput")(sparkline(samples)).fill,
    ).length(4),
    panel("Signal")(
      chart(
        Seq(Dataset("wave", wave, graphType = GraphType.Line)),
        xBounds = (0.0, 80.0),
        yBounds = (0.0, 100.0),
      )
    ).fill,
  )
```

For custom plots, `canvas(xBounds, yBounds)(shapes*)` provides points, segments,
polylines, rectangles, and circles. Charts can use braille or half-block resolution
depending on density.

## Feedback and motion

Animations need one thing from you — a tick rate — and nothing else:

```scala
override def config: RunnerConfig = RunnerConfig(tickRate = Some(80.millis))

def view(using ReactiveScope): Element = spinner("deploying")
```

There is no counter to declare, advance, or thread through. The animated elements
read the ambient `AnimationClock`, and because that read is *tracked*, only a view
actually rendering an animation is repainted by the ticks — a screen with no spinner
on it is left alone. Hand-rolling `ticks.get` in the view repaints the whole app
forever instead, whether anything is moving or not.

- `spinner(label)` and `animatedText(content)` animate on the ambient clock;
- `skeleton()`, `indeterminateBar()`, and `marquee(content)` show work without known
  progress;
- `progressBar(ratio)`, `progressBar(done, total)` and `gauge(ratio)` show bounded
  progress;
- toasts, splash screens, and post-render effects live in the app/runtime layer.

Every animation is a pure function of elapsed time, so each of these has a
`…At(elapsed)` twin — `spinnerAt`, `skeletonAt`, `indeterminateBarAt`, `marqueeAt`,
`animatedTextAt` — for driving from a clock of your own. `AnimationClock.freezeAt(...)`
pins the clock so a test can assert an exact frame without waiting for wall time.

Speeds are wall-clock, not tick counts. A preset that holds each frame for 80ms looks
the same in an app ticking every 50ms and one ticking every 200ms; expressing speed in
ticks would make the same spinner read as sluggish in one and frantic in the other.

Use a real percentage when you know one; use an indeterminate widget only when you
do not. Respect reduced-motion needs by offering a config option or static fallback —
`IndeterminateMotion.Pulse` and `SpinnerPreset.Ellipsis` are the quietest built-ins.

### Spinner presets

`spinner` defaults to `SpinnerPreset.Dots`; `.preset(...)` swaps the animation.

```scala
spinner("deploying").preset(SpinnerPreset.Arc)
```

Presets are grouped by the glyph repertoire they need, which is what decides whether
one is safe on a given terminal:

| Catalogue | Presets | Needs |
|---|---|---|
| `AsciiPresets` | `line`, `ellipsis`, `bouncing-bar` | nothing — safe anywhere, including a log |
| `BraillePresets` | `dots`, `dots-orbit`, `dots-pulse`, `dots-ring`, `dots-wave` | a font with the braille block |
| `BlockPresets` | `grow-vertical`, `grow-horizontal`, `quadrant`, `square`, `circle-halves`, `circle-quarters`, `arc`, `triangle`, `pipe`, `star`, `toggle`, `points`, `arrow`, `bouncing-ball`, `layers`, `balloon` | block, box-drawing and geometric-shape glyphs |
| `EmojiPresets` | `moon`, `earth`, `clock`, `hearts` | color emoji; **two columns per frame** |

A preset carries its own speed as `frameDuration`. `.slowedBy(2)`, `.reversed`,
`.atFps(8)` and `.withFrameDuration(...)` derive variants — `.slowedBy` and `.atFps`
are on the element too, so `spinner("x").atFps(4)` needs no preset value. 
`SpinnerPreset.byName("arc")` resolves one from a config file or a `--spinner` flag.

Frames are padded to the widest frame in the set, so a preset with ragged frames
(`ellipsis`, `bouncing-bar`) never shoves its label back and forth.

### Progress-bar styles

`ProgressStyle` is the glyph vocabulary a bar draws with; `.preset(...)` swaps it.

| Style | Look | Sub-cell |
|---|---|---|
| `Line` (default) | `━━━━────` | no |
| `Blocks` | `████▌` | yes — eighth blocks |
| `BlocksShaded` | `████▌░░░` | yes |
| `Shaded` | `███▓░░░` | yes |
| `Ascii` | `####----` | no |
| `Arrow` | `===>----` | no |
| `Dots` | `⣿⣿⣷⢀⢀` | yes |
| `Baseline` | `▄▄▄▁▁▁` | no |
| `Minimal` | `▪▪▪···` | no |

A style with `partials` draws progress finer than its own cell grid — eight partial
blocks turn a 20-column bar into 160 distinguishable positions. That also changes the
rounding, deliberately: sub-cell styles **floor** the fill and render the remainder as
the boundary glyph, so the bar never claims progress that has not happened, while
whole-cell styles round to nearest because that is the closest a cell can get.

`indeterminateBar` draws from the same vocabulary and adds `.motion(...)`:

| Motion | Behaviour |
|---|---|
| `Bounce` (default) | a segment sliding to the far edge and back |
| `Sweep` | a segment leaving one edge and reappearing at the other |
| `Comet` | a bright head with a tail fading behind it |
| `Pulse` | the whole track brightening in place — no travel, the quietest option |

`.period(duration)` sets how long one full traverse takes, so several bars can be made
to move in step regardless of their widths.

### Captions

A bar's caption is a `ProgressLabel`, not a nullable string:

| Element call | Renders |
|---|---|
| `progressBar(r)` | `42%` — the default |
| `progressBar(r).label("syncing")` | `syncing` |
| `progressBar(r).labelled("syncing")` | `syncing 42%` |
| `progressBar(r).bare` | nothing; the bar takes the whole row |

### Theming the animations

Colors come from the ambient `Theme`'s `loading` palette, resolved where the element
is written, so re-theming an app re-themes its spinners and bars with no call-site
change:

```scala
final case class LoadingTheme(
    spinner: Style,   // the moving glyph
    label: Style,     // the caption beside it
    track: Style,     // the unfilled part of a bar
    fill: Style,      // the filled part
    band: Style,      // a skeleton's sweeping highlight
    fillRamp: Option[ColorRamp] = None,
)
```

A custom theme gets a coherent palette for free from its own semantic styles:

```scala
val mine = Theme.Dark.copy(
  name = "mine",
  loading = LoadingTheme.from(accent = myAccent, muted = myMuted, surface = mySurface),
)
```

Anything set at the call site layers on top of the theme, and the glyph and the label
style independently:

```scala
spinner("syncing").color(Color.Magenta)              // recolors the glyph, not the label
progressBar(used, total).ramp(ColorRamp.Traffic)     // green → amber → red as it fills
```

`ColorRamp` takes any number of stops, which matters: interpolating straight from green
to red passes through a muddy brown, so the common progress ramp needs amber in the
middle. Built-ins are `Traffic` (green→amber→red), `Recovery` (its reverse), and `Heat`.

A ramp is opt-in rather than a theme default because its meaning is call-site specific:
`Traffic` reads as "filling up towards trouble", which is right for disk usage and
wrong for a download.

The showcase's **Loading** tab renders every preset at once — run
`./mill examples.showcase.run` to pick one by eye.

### Shape spinners

`spinner` says "working" inside one glyph. When there is a pane to fill rather than a
status line, three widgets say it in an area — all on the same ambient clock, all pure
functions of elapsed time.

```scala
orbitSpinner()                              // a circle with a comet arc, fitted to its area
orbitSpinner().path(OrbitPath.Square).radius(4)
linearSpinner().bouncing                    // a head travelling a one-cell track
spinnerGrid().preset(SpinnerPreset.DotsRing)
```

**`orbitSpinner`** draws a figure at sub-cell resolution with a bright arc chasing round
it. `radius` is in **dots**, not cells, because that is the resolution the shape is drawn
at — a radius in cells could not express the difference between the two smallest legible
rings. `sweep` is the fraction of the lap that is lit, not a dot count, so the arc
subtends the same angle at every radius.

| Method | Effect |
|---|---|
| `.path(OrbitPath.Circle)` or `.path(OrbitPath.Square)` | the loop the arc travels |
| `.radius(dots)` | pins the size; unset, it fills its area |
| `.sweep(fraction)` | how much of the lap is lit — `.sweep(1.0).solid` is a static "queued" ring |
| `.solid` or `.ramp(ColorRamp.Heat)` | a uniform window, or a graded comet tail |
| `.thickness(dots)` | thickens arc and path inwards; worth it above ~radius 8 |
| `.reversed`, `.period(d)` | direction and revolution time |
| `.markers("*")` or `.halfBlocks` | the ASCII and no-braille-font floors |

**One style per cell — the constraint this widget is shaped around.** A braille cell packs
eight dots but carries one `Style`, so dot resolution is 2×4 per cell while *colour*
resolution is 1 per cell. Two rules follow, both chosen rather than fallen into: masks are
OR-ed, so a cell holding both arc and path keeps every dot and the resting ring never
erodes; styles take the brightest dot, so such a cell reads as arc and the head is never
overwritten by its own tail. The budget, concretely: a radius-4 braille ring is 24 dots but
only 11 cells, so a ramped comet shows 11 shades, not 24. The alternative — one dot per
cell, so colour and geometry agree — throws away the resolution braille was chosen for.

A consequence worth knowing: because the mask never erodes, the **glyphs are static** and
the entire animation lives in the per-cell style. On a terminal rendering neither colour
nor dim, an orbit spinner is a still ring. Reach for `spinnerGrid` there instead.

**`linearSpinner`** runs a head along a one-cell track, clipping itself to a single row or
column so it cannot smear. Its three knobs are deliberately orthogonal — `LinearPath` is
the walk, `LinearTrail` is the shading, `trailSlots` is the length — so a bouncing head
with a comet tail and a wrapping window of solid slots are the same widget with different
arguments, rather than two modes in which half the options are silently ignored. A bounce
has no dwell frame at the turn; the extreme slots are visited once per cycle and the
interior twice.

**`spinnerGrid`** turns any `SpinnerPreset` into an area-filling block by giving each slot
a time offset. It *consumes* the preset catalogue rather than extending it, which is the
point of it existing: a preset is a function of time alone and can never carry a spatial
offset, yet every preset already in the catalogue becomes an area animation the moment it
is put here. `.phase(...)` chooses `Uniform` (lockstep — the reduced-motion member),
`Diagonal`, or `Radial`. It is also the one place in this family where per-slot colour is
free, because every slot holds exactly one frame — `.ramp(...)` shades the block by phase
with none of the compromise above.

The showcase's **Loading** tab renders all of them animating; the gallery scrolls.

## Animated text

`animatedText(content)` applies a time-based effect to a string, on the same ambient
clock as the spinners. `.effect(...)` chooses which:

| Effect | What it does | Notes |
|---|---|---|
| `Wave(crestWidth, cellsPerSecond)` | a bright crest travelling through the text | the default; survives on a terminal with no color, because it moves emphasis rather than hue |
| `Typewriter(charactersPerSecond, cursor)` | reveals one grapheme at a time behind a blinking cursor | the cursor stops blinking once the text is complete, so a finished line reads as finished |
| `Gradient(ramp, cellsPerSecond)` | colors each grapheme from a `ColorRamp`, scrolling the ramp | the text stays put; the ramp moves through it |
| `Shimmer(sparkle, period)` | a highlight sweeping across with a sparkle at its head | rests between sweeps, which is what stops it reading as a strobe |
| `Bounce(trail, cyclesPerSecond)` | a vertical wave through the text with a dimmed after-image | the only effect that needs more than one row; at one row it degrades to a flat line |

```scala
animatedText("Deploying…").effect(TextEffect.Typewriter())
animatedText("glyphora").effect(TextEffect.Gradient(ColorRamp.Heat)).length(1)
animatedText("LOADING").effect(TextEffect.Bounce(trail = 2)).length(4)
```

Each effect carries its own knobs rather than `AnimatedText` carrying the union of all
of them, so you cannot set a typewriter's cursor on a shimmer and have it quietly
ignored. `preferredHeight` reports the rows an effect needs — only `Bounce` asks for
more than one.

## Notices and badges

`NoticeLevel` — `Success`, `Info`, `Warning`, `Error` — is the single severity
vocabulary, shared by notices, badges, and `TuiApp.notify`'s toasts. Its `icon` is one
column wide for every level so a stacked column of notices stays aligned, and its `tag`
(`OK`, `INFO`, `WARN`, `FAIL`) is the short form for a badge.

```scala
notice("8 services updated", NoticeLevel.Success).at(LocalTime.now())
notice("could not reach the registry", NoticeLevel.Error).wrapped
```

Renders as `[12:04:31] ✔ 8 services updated`. The timestamp is **passed in**, never read
by the widget: a widget that called `LocalTime.now()` would render a different frame on
every repaint, so a redraw triggered by something else entirely would silently change
the displayed time. Stamp the message when the event happens, which is the moment the
reader cares about. `.wrapped` lets a long message grow instead of clipping.

`badge(label)` is a short inline label, in three variants that read at different
volumes:

| Variant | Draws | Use when |
|---|---|---|
| `Solid` (default) | reversed text, padded — ` NEW ` | the status must not be missed |
| `.outline` | bracketed, no block of color — `[NEW]` | it sits inside a line of prose |
| `.dot` | a colored dot then plain text — `● ready` | every row in a list has one and none should shout |

```scala
row(text("api"), badge(NoticeLevel.Error).outline)   // api [FAIL]
row(text("worker"), badge("3 pending").dot)
badge("BETA").color(Color.Magenta)
```

`badge(level)` uses the level's own tag and color; `badge(label)` takes the theme's
accent and accepts any `.color(...)`. Both report a `preferredWidth`, so a caller can
size a column for them rather than guessing.

## Drop to a raw Widget

The DSL is not a separate renderer. Any core `Widget` can become a leaf:

```scala
import io.worxbend.tui.core.*
import io.worxbend.tui.widgets.Paragraph

val raw = Paragraph(Text.raw("Rendered directly"), wrap = true)
val element = widget(raw).fill
```

Or use widgets without the DSL at all:

```scala
val buffer = Buffer(Rect(0, 0, 40, 5))
raw.render(buffer.area, buffer)
```

That escape hatch is useful for embedding, custom render loops, and widget library
tests. See [Architecture](./architecture) and [Testing](./testing).

## Catalog by tier

The internal tiers document dependency order, not quality:

1. **Foundation** — block, row/column, paragraph, list, table, tabs, gauge,
   sparkline, text input, checkbox, toggle, select, tree.
2. **Visualization** — canvas, chart, bar chart, calendar.
3. **Rich content** — spinner, wave text, dialog, markdown, dual sparkline.
4. **Application-scale state** — data table, directory tree, text area, loading
   widgets, advanced inputs, scroll views, image, links, and menus.

Every built-in has render-to-`Buffer` tests. Interactive DSL wrappers additionally
have focus and event-routing tests.
