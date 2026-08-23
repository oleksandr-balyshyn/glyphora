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
- **`Async` / `Cancelable`** — background work: `run` / `runCatching` off-thread with
  the continuation marshalled back to the render loop that armed it, `after` / `every`
  for one-shot and repeating timers. A failure in `run`'s work goes back to that same
  loop, so the runner reports it as `RunnerError.QueuedTask` rather than printing a
  stack trace over the alternate screen.
- **`Stopwatch` / `Timer` / `TickDriven`** — caller-owned tick clocks. They produce the
  `elapsed` that `tui-core`'s motion values (`Progress`, `Easing`, `Tween`, `Spring`,
  `Effect`) are pure functions of; the curves themselves live in `tui-core` so that
  `tui-widgets` can reach them too.

```scala
TerminalRunner(backend, RunnerConfig(tickRate = Some(250.millis))).run(
  onStart = handle => ...,                // render thread, before the first frame
  handleEvent = (event, handle) => ...,   // EventOutcome.Redraw / EventOutcome.Ignored
  render = frame => frame.renderWidget(widget, frame.area),
)
```

Testing: pair with `HeadlessBackend` (in `tui-terminal`) and the `Pilot` driver (in
`tui-test`) to drive full event/render cycles without a TTY.
