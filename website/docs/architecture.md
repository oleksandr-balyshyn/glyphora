---
title: Architecture
---

# Architecture

```text
 ┌────────────┐   ┌────────────┐   ┌────────────┐
 │  tui-dsl   │──▶│tui-widgets │──▶│  tui-core  │   Element tree → Widgets → Buffer
 └─────┬──────┘   └────────────┘   └─────▲──────┘
       │          ┌────────────┐   ┌─────┴──────┐
       └─────────▶│tui-runtime │──▶│tui-terminal│   signals/loop → diff → ANSI
                  └────────────┘   └────────────┘
```

Each arrow is a real Mill module dependency — nothing above `tui-core` reaches back
down into a layer above it, so you can also depend on any single tier directly (for
example, `tui-widgets` with a backend of your own, skipping the DSL entirely).

| Module | What it owns | API reference |
|---|---|---|
| `tui-core` | `Buffer`/`Cell`, `Style`, `Layout` solver, `Widget` traits, event ADT, `CharWidth` (UCD-generated width table) | [tui-core](pathname:///api/core/) |
| `tui-terminal` | `Backend` trait, JLine 3 impl (diff flush, input decoding), `HeadlessBackend` | [tui-terminal](pathname:///api/terminal/) |
| `tui-widgets` | every built-in widget — backend-agnostic, render-to-`Buffer` tested | [tui-widgets](pathname:///api/widgets/) |
| `tui-runtime` | `Signal`/`Computed`, render thread, runner loop, `Effect` engine | [tui-runtime](pathname:///api/runtime/) |
| `tui-dsl` | `TuiApp`, `Element` tree, focus/mouse routing, chrome presets, screens/toasts/palette | [tui-dsl](pathname:///api/dsl/) |
| `tui-macros` | `deriveForm`/`bindAction` — compile-time only, keeps native-image reflect-config-free | [tui-macros](pathname:///api/macros/) |
| `test-support` | `Pilot` driver + buffer assertions (not published; test-only) | — |

## tui-core

Foundational types, no dependencies, no terminal I/O, no reflection — the
maximum-stability tier everything else builds on:

- **Geometry**: `Rect`, `Position`, `Size`.
- **Frame buffer**: `Buffer` (mutable cell grid, absolute coordinates, silent
  clipping), `Cell` (a `String` symbol, because one cell can hold a multi-codepoint
  grapheme cluster).
- **Styling**: `Style`, `Color`, `Modifiers` (allocation-free bitset).
- **Text**: `Text` / `Line` / `Span`.
- **`CharWidth`**: terminal display-width arithmetic (CJK, combining marks, emoji ZWJ
  sequences, flags, variation selectors) — generated from the Unicode Character
  Database by `tools/generate-width-table.py`.
- **Layout**: `Constraint` (`Length`/`Percentage`/`Ratio`/`Min`/`Max`/`Fill`) and the
  `Layout.split` solver.
- **Widget traits**: `Widget`, `StatefulWidget[S]` — SAM-convertible.
- **Input events**: `Event` / `KeyEvent` / `MouseEvent` ADT, defined here (not in
  `tui-terminal`) so widgets stay backend-agnostic.

```scala
import io.worxbend.tui.core.*

val buffer = Buffer(Rect(0, 0, 20, 3))
val areas = Layout.vertical(1, Constraint.fill).split(buffer.area)
buffer.setString(areas(0).x, areas(0).y, "Title", Style.Default.bold.withFg(Color.Cyan))
```

## tui-terminal

The terminal backend layer. Everything above (`tui-runtime`, widgets, DSL) talks to
`Backend` only:

- **`Backend`** — raw mode, alternate screen, cursor visibility, mouse capture,
  diff-based `draw(buffer)`, `readEvent(timeout)`. All fallible operations return
  `Either[BackendError, A]`.
- **`JLine3Backend`** — the production implementation over `org.jline:jline` 3.30.x,
  pinned. Keeps a snapshot of the last flushed frame and writes only changed cells,
  batched into one ANSI string per frame, with OSC 8 hyperlink transitions.
- **`InputDecoder`** — ANSI/CSI/SS3/SGR-mouse decoder, injected with a plain
  `read(timeoutMillis) => Int` function so it is fully unit-tested without a TTY.
- **`HeadlessBackend`** — in-memory backend for the `Pilot` end-to-end test harness.

The trait is deliberately JLine-free: a fake backend can implement it without
importing a single JLine type, and every example runs against `HeadlessBackend` in
tests and `JLine3Backend` live (JVM or native binary).

## tui-widgets

Every built-in widget. Depends only on `tui-core` — widgets are
backend-agnostic and render into a `Buffer`, nothing else. See the full
[Widget catalog](./widgets).

## tui-runtime

The mid-level framework tier:

- **`Signal[A]` / `Computed[A]` / `ReactiveScope`** — fine-grained signals. See
  [State & signals](./state-and-signals).
- **`RenderThread`** — single-render-thread contract: `checkRenderThread()` is a
  no-op when no runtime is running (so plain unit tests need no setup),
  `runOnRenderThread`, `runLater`. `Signal.set` asserts it.
- **`Runner` / `TerminalRunner` / `Frame` / `RunnerConfig`** — the event/render loop:
  terminal setup/teardown, diff-driven redraws, tick emission, resize handling.
- **`Effect`** — the post-render motion engine. See [Motion](./motion).

## tui-dsl

The high-level declarative API — what applications use day-to-day: `Element`,
`TuiApp`, the chrome presets (`scaffold`, `topBar`, `statusBar`, `sidebar`), themes,
screens, toasts, the command palette, and focus/mouse routing. See
[The app shell](./app-shell) and [Mouse & focus](./mouse).

## tui-macros

Compile-time codegen: everywhere the framework bridges *user-defined* code, the
bridge is generated at compile time — never runtime reflection. This is the
constraint that keeps GraalVM native-image builds free of reflect-config JSON.

- **`deriveForm[A]`** derives a `FormSpec[A]` from a case class via
  `Mirror.ProductOf` (`inline`, stdlib-only): field names become `FieldSpec`s, field
  types choose the input kind (`String`/`Int`/`Boolean`); anything else is a compile
  error.
- **`bindAction[A](handler)`** binds an action handler as a direct call.
- **`Field[A]`** is cue4s-style lazily-composed parsing/validation:
  `Field.int("age").mapValidated(a => if a >= 18 then Right(a) else Left("must be 18+"))`.

CI enforces the zero-reflection rule with a grep over all main sources.

## House rules (CI-enforced)

- No `java.lang.reflect`/`Class.forName` anywhere outside `tui-macros`' compile-time
  codegen.
- No `String.length`/`substring` for layout math outside `CharWidth` — grapheme
  clusters and wide codepoints must always go through the Unicode-aware table.
- Warnings are errors (`-Wunused:all -Werror`).
- Scalafmt owns formatting; CI checks formatting, doesn't just apply it.
