# tui-widgets

Built-in widgets (`PLAN.md` §6 backlog). Depends only on `tui-core` — widgets are
terminal-backend-agnostic and render into a `Buffer`, nothing else.

Implemented:

- **Tier 0**: `Block` (borders: plain/rounded/double/thick + title), `Row`/`Column`
  (+`LayoutItem`) layout containers, `Spacer`, `Scrollbar` (+`ScrollbarState`).
- **Tier 1**: `Paragraph` (alignment, cluster-safe wrapping), `ListView` (+`ListState`;
  named to avoid colliding with `scala.List`), `Table`, `Tabs`, `Gauge`, `LineGauge`,
  `Sparkline`.

Every widget has a render-to-`Buffer` test (`BufferAssertions` from `test-support/`).
`RenderLoopBench` (in test sources) is the PLAN §12 render-loop benchmark:
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
