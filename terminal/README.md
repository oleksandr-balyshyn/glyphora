# tui-terminal

The terminal backend layer (`SPEC.md` §3.2): the `Backend` trait plus the JLine 3
implementation. Everything above (`tui-runtime`, widgets, DSL) talks to `Backend` only.

- **`Backend`** — raw mode, alternate screen, cursor visibility, mouse capture,
  diff-based `draw(buffer)`, `readEvent(timeout)`. All fallible operations return
  `Either[BackendError, A]`.
- **`JLine3Backend`** — the production implementation over `org.jline:jline` 3.30.x
  (pinned; do not move to JLine 4.x for v1, `SPEC.md` §9.1). Keeps a snapshot of the
  last flushed frame and writes only changed cells, batched into one ANSI string per
  frame.
- **`InputDecoder`** — original ANSI/CSI/SS3/SGR-mouse decoder, injected with a plain
  `read(timeoutMillis) => Int` function so it is fully unit-tested without a TTY.
- `HeadlessBackend` (in-memory, for the `Pilot` end-to-end test harness) lands with
  `tui-runtime` in step 4.

Structural additions vs. `SPEC.md` §3.2's trait sketch (recorded in `SPEC.md` §9):
`enableMouseCapture`/`disableMouseCapture` and `hideCursor`/`showCursor` — the runner
needs both to honor `RunnerConfig.mouseCapture` and to hide the cursor during redraws.

## Manual smoke test (PLAN.md §11, step 3)

Automated coverage: decoder matrix (arrows/function keys/modifiers/SGR mouse), ANSI
sequence encoding, no-JLine fake `Backend` (`FakeBackendSpec` proves the trait has no
JLine leakage), and graceful degradation without a TTY.

Attempted in the implementation environment (no interactive TTY attached):

- `./mill examples.hello-world.run` → `no usable terminal: UnsupportedTerminal(dumb
  terminal (no TTY attached))` — clean detection and exit, no crash. ✅ (observed)
- Raw-mode enter/exit, resize detection (SIGWINCH), and live key/mouse round-trips
  **need confirmation on a real terminal** — run `./mill examples.hello-world.run`
  from an interactive shell once the step-4 render loop lands; it should enter the
  alternate screen, redraw on resize, and quit cleanly on `q`. ⚠️ pending human
  confirmation on real hardware.
