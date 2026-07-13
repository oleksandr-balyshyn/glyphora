# tui-widgets

Built-in widgets. Depends only on `tui-core` — widgets are
terminal-backend-agnostic and render into a `Buffer`, nothing else.

Implemented:

- **Tier 0**: `Block` (borders: plain/rounded/double/thick + title), `Row`/`Column`
  (+`LayoutItem`) layout containers, `Spacer`, `Scrollbar` (+`ScrollbarState`).
- **Tier 1**: `Paragraph` (alignment, cluster-safe wrapping), `ListView` (+`ListState`;
  named to avoid colliding with `scala.List`), `Table`, `Tabs`, `Gauge`, `LineGauge`,
  `Sparkline`.
- **Tier 2**: `TextInput` (+`TextInputState`, grapheme-cluster-safe editing/cursor),
  `Checkbox`, `Toggle`, `Select`, `Tree` (+`TreeNode`/`TreeState`). The `Form` widget
  lives in `tui-dsl` (`Form`/`FormState`), composed from these.
- **Tier 3**: `Canvas` + shapes (`Points`/`SegmentShape`/`Polyline`/`RectangleShape`/
  `CircleShape`), `BarChart`, `Chart` (+`Dataset`, line/scatter), `Calendar`.
- **Tier 4**: `Spinner`, `WaveText`, `Dialog`, `DualSparkline`, `Markdown` (subset:
  headings, lists, quotes, code fences, inline styles — no links/images/tables).
- **Tier 5**: `DataTable` (+`DataTableState`; sortable with numeric-aware compare,
  filterable, selectable), `DirectoryTree` (+`DirectoryTreeState`; lazy cached
  filesystem listings), `TextArea` (+`TextAreaState`; multi-line cluster-safe editing,
  bounded undo, 2D cursor/scroll — no syntax highlighting).
- Plus: form controls (`RadioGroup`, `Slider`, `Paginator`), loading states
  (`Skeleton`, `IndeterminateBar`, `Marquee`), viz extras (`PieChart`,
  `StackedBarChart`, `Heatmap`), `ScrollView`, `Image` (half-block raster), `Link`.

Every widget has a render-to-`Buffer` test (`BufferAssertions` from `test-support/`).
`RenderLoopBench` (in test sources) is the render-loop benchmark:
`./mill widgets.test.runMain io.worxbend.tui.widgets.RenderLoopBench`.

```scala
import io.worxbend.tui.widgets.*
import io.worxbend.tui.core.*

val ui = Column(Seq(
  LayoutItem(Constraint.Length(3), Block(title = Some(Line.raw("Status")))),
  LayoutItem(Constraint.Fill(1), Paragraph(Text.raw("Hello"), wrap = true)),
))
ui.render(buffer.area, buffer)
```
