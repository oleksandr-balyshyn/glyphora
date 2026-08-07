# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

glyphora is a Scala 3 terminal-UI toolkit published as six `io.worxbend::tui-*` artifacts. Build tool is Mill (`.mill-version` pins 1.1.7, use the checked-in `./mill` wrapper); Scala 3.7.1; JDK 21 for CI, GraalVM `graalvm-community:23.0.1` for native images.

## Commands

```bash
./mill __.compile                 # everything
./mill __.test                    # everything
./mill widgets.test               # one module's suite
./mill core.test.testOnly io.worxbend.tui.core.RectSpec            # one suite
./mill core.test.testOnly io.worxbend.tui.core.RectSpec -- -z inset # one test (ScalaTest -z)

./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources   # CI gate
./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources

./mill examples.showcase.run          # manual product tour against a real terminal
./mill examples.showcase.nativeImage  # GraalVM binary → out/examples/showcase/nativeImage.dest/
```

Docs (canonical Markdown lives in `website/docs/`, published to both GitHub Pages and the Wiki):

```bash
(cd website && npm ci && npm run build)
node scripts/export-wiki.mjs --output build/wiki
```

Golden-frame fixtures: run tests with `GLYPHORA_GOLDEN_UPDATE=<module>/src/test/resources` and each `GoldenFrames.assertMatches` writes its actual frame instead of comparing.

`core/src/main/scala/io/worxbend/tui/core/WidthTable.scala` is generated — regenerate with `python3 tools/generate-width-table.py`, never hand-edit.

## Hard constraints (CI fails on these)

- **No runtime reflection.** `java.lang.reflect` / `Class.forName` are grepped out of all `*/src/main/scala`. Any bridge to user-defined code goes through `tui-macros` compile-time derivation instead — this is what keeps native-image builds reflect-config-free.
- **No `String.substring` in main sources outside `core/CharWidth.scala`.** All display-width, truncation, and layout arithmetic goes through `CharWidth` (grapheme clusters, CJK, emoji ZWJ, combining marks).
- **Warnings are errors**: `-deprecation -feature -unchecked -Wunused:all -Werror` (set in `build.mill`).
- Scalafmt (`.scalafmt.conf`, 120 cols, Scala 3 dialect, `align.preset = more`) is checked, not applied, by CI.
- Seven examples (`hello-world`, `counter`, `todo-list`, `dashboard`, `form-demo`, `showcase`, `procmon`) must build with `--no-fallback` and exit cleanly with no TTY. The three that use `java.net.http` — `weather`, `airsensor`, `loadtest` — are deliberately outside that job.

## Architecture

Six published modules plus a test-only one. Dependency edges are real Mill `moduleDeps` and only point downward — **nothing above `tui-core` may be referenced by a lower tier**:

| Module | `moduleDeps` | Owns |
|---|---|---|
| `core` | — | `Buffer`/`Cell`, `Rect`/`Size`/`Position`, `Style`/`Color`/`Modifiers`, `Text`/`Line`/`Span`, `Layout` + `Constraint` solver, `Widget`/`StatefulWidget`, the `Event`/`KeyEvent`/`MouseEvent` ADT, `CharWidth` |
| `terminal` | core | `Backend` trait, `JLine3Backend` (pinned `org.jline:jline:3.30.x`), `InputDecoder`, `HeadlessBackend` |
| `widgets` | core | all 40+ built-in widgets, backend-agnostic |
| `runtime` | core, terminal | `Signal`/`Computed`/`ReactiveScope`, `RenderThread`, `Runner`/`TerminalRunner`/`Frame`, `Async`, `Timers`, `Effect`/`Easing` |
| `macros` | core | `deriveForm` / `bindAction`, inline + `Mirror` only |
| `dsl` | core, widgets, runtime, macros | `Element` tree, `TuiApp`, `EventRouter`, `Focus`, `Chrome`, `Theme`, `Screen`, `Form` |
| `test-support` | core, terminal, runtime | `Pilot`, `BufferAssertions`, `GoldenFrames` — **not published**, test-only |

The design invariants worth knowing before editing:

- **The event ADT lives in `core`, not `terminal`**, so widgets stay backend-agnostic. Everything above `terminal` talks to the `Backend` trait, which imports no JLine types; fallible backend ops return `Either[BackendError, A]`.
- **Widgets render into a `Buffer` and nothing else.** `Widget` is a SAM (`(Rect, Buffer) => Unit`); interactive/scrollable renderers use `StatefulWidget[S]` where `S` is owned by the *caller*, keeping the widget value immutable and reusable.
- **`Element` is a declarative layer over `tui-widgets`, never a parallel render path** — every node exposes a `widget`. The tree is plain sealed data, so styling extensions rebuild nodes instead of mutating, and construction tests can pattern-match it.
- **Event ordering in the DSL**: the user's `onKeyEvent`/`onMouseEvent` handler runs first; only if it returns `false` does the element's `builtinKeyHandler`/`builtinMouseHandler` fire; unconsumed keys bubble to ancestors, then to `TuiApp.bindings`. Return `false` to let an event keep bubbling.
- **Single render thread.** `Signal.set` calls `RenderThread.checkRenderThread()`, which throws off-thread but is a deliberate no-op when no runner is registered — so plain unit tests need no runtime. Each `Runner` owns its own work queue (multiple runners can coexist in one JVM); background work must call `RenderThread.capture()` *before* going async so its continuation returns to the right loop.
- **Signals track dependencies per evaluation.** `get` (needs a `ReactiveScope`) subscribes; `peek` doesn't. Edges are rebuilt on every recompute, so conditional reads subscribe only the branch that ran. Setting an `==` value notifies nobody.
- **Rendering is diff-based**: the full frame is composed into a `Buffer` with no terminal involved; `JLine3Backend` compares against the last flushed frame and emits only changed cells as one batched ANSI string. That is why `HeadlessBackend` + `Pilot` tests are exact rather than approximate.

Every module is `<module>/src/{main,test}/scala/io/worxbend/tui/<module>/`. Examples live in `examples/<name>/` with `Main` at `io.worxbend.tui.examples.<name-without-dashes>.Main`.

## Build-file conventions

`build.mill` holds the two shared traits: `TuiModule` (Scala version, strict flags, `TuiTests` ScalaTest wiring) and `TuiPublishModule` (POM metadata + the single synchronized `publishVersion`, currently `0.10.0` — bump it in one place). Each module has a small `package.mill`. Test-only dependencies go through `def extraTestDeps`, not by overriding `mvnDeps`, because Mill cannot resolve a second `super.mvnDeps` chain in nested test objects.

## Adding a widget

1. Implement in `widgets/` depending only on `tui-core`; render inside the given `Rect`, clip safely, all width math via `CharWidth`. Stateless → `Widget`; interactive → `StatefulWidget[S]` with caller-owned state.
2. Test the buffer with `BufferAssertions`: empty/tiny areas, truncation, Unicode, focus/selection styling, state boundaries.
3. Add the DSL node and its built-in key/mouse behavior in `dsl/Element.scala`, a factory in `object Element`, and an export in `dsl.scala`.
4. Drive interaction through `Pilot` (focus, keys, mouse, resize, redraw).
5. Document it in `website/docs/widgets.md`; new pages must also be registered in `website/docs-navigation.mjs`, which drives both the Docusaurus sidebar and the generated Wiki.

## Style docs

`SCALA_CODE_STYLE.md` is the general Scala convention doc (explicit result types, sealed ADTs over booleans, `Either` for recoverable failures, no `return`, Scaladoc that states ownership and thread constraints). Its sections on Ox, Scalafix, HTTP/JSON, and DI describe conventions that have no counterpart in this repo — there is no Scalafix config and no Ox dependency here. `docs/STYLE_GUIDE.md` covers visual/editorial rules for the README, site, and `docs/assets/`.
