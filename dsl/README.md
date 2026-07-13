# tui-dsl

The high-level declarative API — what applications are expected to use
day-to-day.

```scala
import io.worxbend.tui.dsl.*

object HelloWorld extends TuiApp:
  def view(using ReactiveScope): Element =
    panel("Hello")(
      text("Welcome!").bold.color(Color.Cyan),
      spacer,
      text("Press 'q' to quit").dim,
    ).rounded.onKeyEvent {
      case KeyEvent(KeyCode.Char('q'), _) => quit(); true
      case _                              => false
    }
```

- **`Element`** — a sealed, pattern-matchable retained-mode tree; every element renders
  through a `tui-core` `Widget` (the DSL is a faithful layer over `tui-widgets`, proven
  byte-identical in `DslFaithfulnessSpec`).
- **Factories** — `text`, `panel`, `row`, `column`, `spacer`, `gauge`, `sparkline`,
  `tabs`, `table`, `widget` (escape hatch), re-exported so one `import ...dsl.*`
  suffices.
- **Extensions** — styling (`.bold`, `.color(...)`, `.rounded`), layout (`.length(n)`,
  `.percent(n)`, `.fill`), events (`.onKeyEvent`, `.onMouseEvent` — return `true` to
  consume, `false` to bubble).
- **`TuiApp`** — state lives in `Signal`/`Computed`; `view` runs under a tracking
  `ReactiveScope`, so any signal read by the last evaluation schedules a redraw when
  set. `runWith(backend)` is the headless-test entry point; unconsumed `Ctrl+C` quits.

Focus management and mouse hit-testing land with Tier 2 widgets (step 7); key routing
is currently depth-first with stop-propagation bubbling.
