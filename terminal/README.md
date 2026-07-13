# tui-terminal

The terminal backend layer: the `Backend` trait plus the JLine 3 implementation.
Everything above (`tui-runtime`, widgets, DSL) talks to `Backend` only.

- **`Backend`** — raw mode, alternate screen, cursor visibility, mouse capture,
  diff-based `draw(buffer)`, `readEvent(timeout)`. All fallible operations return
  `Either[BackendError, A]`.
- **`JLine3Backend`** — the production implementation over `org.jline:jline` 3.30.x
  (pinned; JLine 4.x is too new to be a safe default). Keeps a snapshot of the last
  flushed frame and writes only changed cells, batched into one ANSI string per
  frame, with OSC 8 hyperlink transitions.
- **`InputDecoder`** — ANSI/CSI/SS3/SGR-mouse decoder, injected with a plain
  `read(timeoutMillis) => Int` function so it is fully unit-tested without a TTY.
- **`HeadlessBackend`** — in-memory backend for the `Pilot` end-to-end test harness:
  renders to a retained snapshot, reads synthetic events, counts draws/idle reads.

The trait is deliberately JLine-free: `FakeBackendSpec` implements a complete
backend without importing a single JLine type, and every example runs against
`HeadlessBackend` in tests and against `JLine3Backend` (JVM or native binary) live.
