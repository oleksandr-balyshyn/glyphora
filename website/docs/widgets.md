---
title: Widget catalog
---

# Widget catalog

`tui-widgets` depends only on `tui-core` — every widget is backend-agnostic and
renders into a `Buffer`. The DSL (`tui-dsl`) wraps each one in an `Element` factory;
see the [full API](pathname:///api/widgets/) for exact signatures.

| | |
|---|---|
| **Layout & chrome** | `Block` (per-side borders, padding), `Row`/`Column`, `Spacer`, `Rule`, `Scrollbar`, `ScrollView`, `TabbedContent`, `Collapsible`, `SplitPane`, `layers` |
| **Content** | `Paragraph` (cluster-safe wrap), `ListView`, `Table`, `Tabs`, `BigText`, `Log` (follow-tail), `Markdown`, `Link` (OSC 8), `Image` (half-block) |
| **Input** | `TextInput`, `TextArea` (undo), `Checkbox`, `Toggle`, `Select`, `RadioGroup`, `Slider`, `NumberInput`, `MaskedInput`, `SelectionList`, `Autocomplete`, `FilePicker`, `Button`, `Form` (compile-time derived) |
| **Data viz** | `Gauge`, `LineGauge`, `Sparkline`, `DualSparkline`, `BarChart`, `StackedBarChart`, `Chart` (braille/half-block), `PieChart`, `Heatmap`, `Canvas` + shapes, `Calendar`, `DataTable` (sort/filter) |
| **Motion & feedback** | `Spinner`, `Skeleton`, `IndeterminateBar`, `Marquee`, `WaveText`, `Dialog`, toasts, splash screens, the `Effect` engine |

## Tiers

Widgets shipped in dependency order — later tiers build on earlier ones and get
progressively richer state management:

- **Tier 0** — `Block` (borders: plain/rounded/double/thick + title), `Row`/`Column`
  (+ `LayoutItem`) layout containers, `Spacer`, `Scrollbar` (+ `ScrollbarState`).
- **Tier 1** — `Paragraph` (alignment, cluster-safe wrapping), `ListView`
  (+ `ListState`; named to avoid colliding with `scala.List`), `Table`, `Tabs`,
  `Gauge`, `LineGauge`, `Sparkline`.
- **Tier 2** — `TextInput` (+ `TextInputState`, grapheme-cluster-safe editing/cursor),
  `Checkbox`, `Toggle`, `Select`, `Tree` (+ `TreeNode`/`TreeState`). `Form` lives in
  `tui-dsl` (composed from these — see `deriveForm` in [Architecture](./architecture)).
- **Tier 3** — `Canvas` + shapes (`Points`/`SegmentShape`/`Polyline`/`RectangleShape`/
  `CircleShape`), `BarChart`, `Chart` (+ `Dataset`, line/scatter), `Calendar`.
- **Tier 4** — `Spinner`, `WaveText`, `Dialog`, `DualSparkline`, `Markdown` (subset:
  headings, lists, quotes, code fences, inline styles, links via OSC 8 — no
  images/tables).
- **Tier 5** — `DataTable` (+ `DataTableState`; sortable with numeric-aware compare,
  filterable, selectable, page windowing), `DirectoryTree` (+ `DirectoryTreeState`;
  lazy cached filesystem listings), `TextArea` (+ `TextAreaState`; multi-line
  cluster-safe editing, bounded undo/redo, 2D cursor/scroll — no syntax highlighting).
- Plus: form controls (`RadioGroup`, `Slider`, `Paginator`), loading states
  (`Skeleton`, `IndeterminateBar`, `Marquee`), viz extras (`PieChart`,
  `StackedBarChart`, `Heatmap`), `ScrollView`, `Image` (half-block raster), `Link`.

## Raw widget example

```scala
import io.worxbend.tui.widgets.*
import io.worxbend.tui.core.*

val ui = Column(Seq(
  LayoutItem(Constraint.Length(3), Block(title = Some(Line.raw("Status")))),
  LayoutItem(Constraint.Fill(1), Paragraph(Text.raw("Hello"), wrap = true)),
))
ui.render(buffer.area, buffer)
```

Every widget has a render-to-`Buffer` test using `BufferAssertions` (from
`test-support`, see [Testing](./testing)). The render-loop benchmark lives alongside
the widgets:

```bash
./mill widgets.test.runMain io.worxbend.tui.widgets.RenderLoopBench
```

## Adding a new widget

See the checklist in [Contributing](./contributing).
