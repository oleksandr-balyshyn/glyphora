# tui-widgets

Built-in widgets. Depends only on `tui-core` — widgets are
terminal-backend-agnostic and render into a `Buffer`, nothing else.

Implemented, grouped by what they are for:

- **Frames and layout**: `Block` (borders: plain/rounded/double/thick, any number of
  `BlockTitle`s on the top and bottom borders, per-side `Padding`), `Row`/`Column`
  (+`LayoutItem`) layout containers, `Spacer`, `ScrollView`, `Scrollbar` (stateless: the
  caller passes content length and offset).
- **Text**: `Paragraph` (alignment, cluster-safe wrapping), `Markdown` (subset:
  headings, lists, quotes, code fences, inline styles — no links/images/tables),
  `Link`, `WaveText`, `TextArea` (+`TextAreaState`; multi-line cluster-safe editing,
  bounded undo, 2D cursor/scroll — no syntax highlighting).
- **Lists and tables**: `ListView` (+`ListState`; named to avoid colliding with
  `scala.List`; items are `String | Line`, so plain text needs no wrapping), `Table`
  (`Table.ofStrings` for a grid of plain text), `Tabs`, `Tree` (+`TreeNode`/`TreeState`), `DataTable`
  (+`DataTableState` with `ColumnSort`/`Paging`; sortable with numeric-aware compare,
  filterable, selectable),
  `DirectoryTree` (+`DirectoryTreeState`; lazy cached filesystem listings).
- **Input controls**: `TextInput` (+`TextInputState`, grapheme-cluster-safe
  editing/cursor), `Checkbox`, `Toggle`, `Select`, `RadioGroup`, `Slider`
  (+`SliderRange`), `Paginator`. The `Form` widget lives in `tui-dsl` (`Form`/`FormState`), composed
  from these.
- **Progress and motion**: `Gauge`, `LineGauge`, `Spinner`, `Skeleton`,
  `IndeterminateBar`, `Marquee` — all pure functions of an elapsed time, which they
  turn into a position through `tui-core`'s `Progress`.
- **Visualization**: `Sparkline`, `DualSparkline`, `BarChart`, `StackedBarChart`,
  `PieChart`, `Chart` (+`Dataset`, line/scatter), `Heatmap`, `Calendar`, `Canvas` +
  shapes (`Points`/`SegmentShape`/`Polyline`/`RectangleShape`/`CircleShape`),
  `Image` (half-block raster).
- **Overlays**: `Dialog`.

Widgets that know how much room their content needs — `Paragraph`, `Markdown`, `Notice`,
`Badge`, `Spinner`, `BigText`, `AnimatedText`, `Tooltip` — answer through `tui-core`'s `Measured`
(`heightAt(width)` / `widthAt(height)`), so a caller never has to know which per-widget
method to reach for. `Overflow` (`Clip`/`Wrap`) is the shared name for what a widget does
with content wider than its area, and a wrapping widget measures itself from the same
field it renders with.

Every widget has a render-to-`Buffer` test (`BufferAssertions` from `test-support/`).
`RenderLoopBench` (in test sources) is the render-loop benchmark:
`./mill widgets.test.runMain io.worxbend.tui.widgets.RenderLoopBench`.

```scala
import io.worxbend.tui.widgets.*
import io.worxbend.tui.core.*

val ui = Column(Seq(
  LayoutItem(Constraint.Length(3), Block(Seq(BlockTitle.top(Line("Status"))))),
  LayoutItem(Constraint.Fill(1), Paragraph(Text.raw("Hello"), overflow = Overflow.Wrap)),
))
ui.render(buffer.area, buffer)
```
