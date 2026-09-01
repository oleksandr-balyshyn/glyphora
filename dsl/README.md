# tui-dsl

The high-level declarative API — what applications are expected to use
day-to-day.

```scala
import io.worxbend.tui.dsl.*

object HelloWorld extends TuiApp:
  def view(using ReactiveScope, Theme): Element =
    panel("Hello")(
      text("Welcome!").bold.fg(Color.Cyan),
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
- **Factories** — `text`, `line`, `panel`, `row`, `column`, `spacer`, `gauge`,
  `sparkline`, `tabs`, `table`, `widget` (escape hatch), re-exported so one
  `import ...dsl.*` suffices. `text` paints one style over a whole block; `line` takes
  plain `String`s and `Span`s in any mix, so `line("Status: ", "OK".styled(_.withFg(Color.Green)))`
  puts two styles on one row without any hand-counted column widths.
- **Extensions** — styling (`.bold`, `.fg(...)`, `.bg(...)`, `.rounded`), layout
  (`.length(n)`, `.percent(n)`, `.fill`), panel chrome (`.padding(cells)`,
  `.padded(Padding(...))`, `.titleBottom(text)`, `.titleStyle(f)`), events
  (`.onKeyEvent`, `.onMouseEvent` — return `true` to consume, `false` to bubble).
- **`TuiApp`** — state lives in `Signal`/`Computed`; `view` runs under a tracking
  `ReactiveScope`, so any signal read by the last evaluation schedules a redraw when
  set. `runWith(backend)` is the headless-test entry point; unconsumed `Ctrl+C` quits.
- **Focus** — every frame, `FocusPass.decorate` walks the tree and hands the run's
  `FocusTracker` the focusable elements in depth-first view order. `Tab`/`Shift+Tab`
  move through that order, elements under a modal layer drop out of it, and the
  focused element is the one keys start at.
- **Mouse** — the same pass records each element's rendered `Rect`, so
  `tracker.hitTest(position)` resolves a click to the innermost element under the
  pointer: a press focuses it, and press/drag/release/scroll reach its
  `.onMouseEvent` before bubbling to its ancestors. `Pilot.click`/`drag`/`scrollUp`/`scrollDown`
  drive all of that headlessly. See [`website/docs/mouse.md`](../website/docs/mouse.md).

- **`KeyBindings`** — the app's global commands, declared once as key-spec strings
  (`binding("ctrl+s", "save")(…)`) and reused for dispatch, the status-bar hints, the
  help overlay and the `Ctrl+P` palette. One binding can answer to several keys —
  `binding(Seq("down", "j"), "next")(…)` fires on both and is still advertised once,
  under the first spec. The specs are read by `KeyEvent.parse` in `tui-core`, the same
  parser `Pilot.press` uses.

Key routing is depth-first from the focused element outwards, with `true` consuming
the event and `false` letting it keep bubbling.
