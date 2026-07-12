# glyphora

A Scala 3 library for building rich terminal user interfaces (TUI) on the JVM, with a
GraalVM native-image target, built with Mill.

## Modules

| Directory | Published as | Purpose |
|---|---|---|
| `core/` | `tui-core` | Foundational types: `Buffer`, `Cell`, `Rect`, `Style`/`Color`, `Text`/`Span`/`Line`, `Layout`/`Constraint`, `Widget`/`StatefulWidget`, `CharWidth`, input-event ADT |
| `terminal/` | `tui-terminal` | Terminal backend abstraction (`Backend`) + the JLine 3 implementation: raw mode, alternate screen, key/mouse events |
| `widgets/` | `tui-widgets` | Built-in widgets, terminal-backend-agnostic |
| `runtime/` | `tui-runtime` | Render loop, event dispatch, render-thread model, `Signal`/`Computed` reactive state |
| `dsl/` | `tui-dsl` | Declarative Scala 3 DSL: retained-mode `Element` tree, focus management, event routing |
| `macros/` | `tui-macros` | Compile-time codegen (form derivation, action binding) — no runtime reflection |
| `test-support/` | not published | Headless `Pilot` test driver + `Buffer` assertion matchers |
| `examples/` | not published | Runnable example apps; also the native-image compile targets |

## Building

```bash
./mill __.compile   # compile everything
./mill __.test      # run all tests
./mill examples.hello-world.run
```

## Planning documents

- [`RESEARCH.md`](RESEARCH.md) — analysis of ratatui, TamboUI, Terminus, cue4s, and Textual.
- [`SPEC.md`](SPEC.md) — the technical specification (authoritative over `PLAN.md` where they disagree).
- [`PLAN.md`](PLAN.md) — the phased development plan: module architecture, widget backlog,
  execution order, acceptance criteria.
- [`SCALA_CODE_STYLE.md`](SCALA_CODE_STYLE.md) — the house Scala style guide, from
  [w0rxbend/ai-oop-design-patterns](https://github.com/w0rxbend/ai-oop-design-patterns).

The five reference projects (ratatui, TamboUI, Terminus, cue4s, Textual) are **read-only
reference material** — architecture and API shape to learn from, never a source to copy
code from. See `PLAN.md` §2.1.
