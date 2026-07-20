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
| Communicate progress | `spinner`, `skeleton`, `indeterminateBar` | `marquee`, `waveText`, effects, toasts |
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

- `spinner(frame, label)` and `waveText(content, phase)` use a caller-provided frame;
- `skeleton(phase)`, `indeterminateBar(phase)`, and `marquee(content, phase)` show
  work without known progress;
- `gauge(ratio)` and `lineGauge` show bounded progress;
- toasts, splash screens, and post-render effects live in the app/runtime layer.

Use a real percentage when you know one; use an indeterminate widget only when you
do not. Respect reduced-motion needs by offering a config option or static fallback.

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
