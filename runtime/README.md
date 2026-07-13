# tui-runtime

The mid-level framework tier: the event/render loop, the render-thread
model, and the reactive state primitives.

- **`Signal[A]` / `Computed[A]` / `ReactiveScope`** — fine-grained signals:
  `get` subscribes the enclosing computation via the
  `ReactiveScope` capability, `peek` reads untracked, `set` lazily marks dependents
  stale. Dependency edges are re-established on every recomputation, so conditional
  reads subscribe exactly the branch that ran.
- **`RenderThread`** — single-render-thread contract (TamboUI-style):
  `checkRenderThread()` (no-op when no runtime is running, so plain unit tests need no
  setup), `runOnRenderThread`, `runLater`. `Signal.set` asserts it.
- **`Runner` / `TerminalRunner` / `Frame` / `RunnerConfig`** — the loop: terminal
  setup/teardown, diff-driven redraws, tick emission, resize handling.

```scala
TerminalRunner(backend, RunnerConfig(tickRate = Some(250.millis))).run(
  handleEvent = (event, handle) => ...,   // Boolean: should redraw
  render = frame => frame.renderWidget(widget, frame.area),
)
```

Testing: pair with `HeadlessBackend` (in `tui-terminal`) and the `Pilot` driver (in
`test-support/`) to drive full event/render cycles without a TTY.
