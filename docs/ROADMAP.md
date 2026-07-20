# Roadmap & competitive gap analysis

This note captures a survey of the leading TUI toolkits — Rust **ratatui** (+ crossterm and the
awesome-ratatui crates), Python **Textual/Rich**, Go **Charm** (Bubble Tea / Bubbles / Lip Gloss /
Huh / Harmonica), JS **Ink** and **blessed-contrib**, and the JVM/Scala peers **Terminus**,
**Tamboui**, **Lanterna**, and **jatatui** (the ex-`tui-scala`, now Java) — and what it implies for
glyphora.

## Where glyphora sits

glyphora is currently the only full-featured, **signals-based TUI toolkit for Scala 3**. Its closest
philosophical peer, Creative Scala's **Terminus**, is also signals-based but ships ~7 widgets; the
most active JVM competitors (**Tamboui**, **jatatui**) are Java and immediate-mode. glyphora's
differentiators — 40+ widgets, built-in app chrome (`scaffold`/palette/themes), a motion engine,
mouse support, zero-reflection GraalVM native images, and headless `Pilot` testing — are unmatched in
this field.

## Recently closed gaps

Some of the survey's gaps were closed in parallel on `main` (PR #3): ratatui-style **`Flex`**
distribution (`Start`/`End`/`Center`/`SpaceBetween`/`SpaceAround`/`SpaceEvenly`) with the `.flex`/
`.center`/`.spaceBetween`/… DSL, `Rect.inner`/`offset`/`centered`, **`Color` math** (`mix`/`blend`/
`lighten`/`darken`/`rgb`/`hex` + the 8 `Bright*` colors), `Style.without`/`not*`, a **structured async**
primitive (`Async.run`/`runCatching`/`after`/`every` + `Cancelable`), and a terminus-style `View`/
grammar.

The remaining gaps were closed in the `feat/gap-analysis-widgets` line of work, layered on top of
main's primitives:

- **Style** — separate underline color (SGR 58) and styled underlines (SGR `4:n`: curly / dotted /
  dashed / double), correctly suppressed under `NoColor`.
- **Layout** — a Lip-Gloss-style `place`/`Align` primitive with styled-whitespace fill (`centered`
  now delegates to it).
- **Widgets** — `Menu` (dropdown / context menu, with a `PositionedElement` anchor), `Tooltip`, and a
  dependency-free `SyntaxHighlighter` (Scala / JSON / Bash / generic) wired into Markdown code fences.
- **Motion** — the full Penner easing set plus a Harmonica-style damped `Spring`.
- **Runtime/UX** — `Stopwatch`/`Timer`, `Backend.suspend` (`$EDITOR`/subprocess handoff) and
  `printAbove` (durable scrollback above the UI), `Color.gradient` + `AdaptiveColor` (building on
  main's `mix`), and a screen-reader-friendly `Form.accessible` rendering.

## Deferred — larger architectural bets

These are the two axes where Terminus genuinely out-reaches glyphora today. They are **consciously
deferred**, not overlooked; each is a module-sized effort with API-surface implications.

1. **Effect-system interop (cats-effect / ZIO).** main's `Async` gives structured, thread-based
   background work, but Terminus ships a cats-effect event-loop layer (`ui-ce`). A first-class
   `tui-cats` (and/or `tui-zio`) module — an effectful `Runner`/`Program` bridge plus `Signal` ↔
   `Ref`/`SignallingRef` adapters — would neutralize this and appeal to the FP-heavy Scala audience.
   glyphora's core stays effect-agnostic; this is additive.
2. **Cross-platform targets (Scala.js / Scala Native).** Terminus runs on Scala.js (xterm.js) and
   Scala Native (termios), not just the JVM + native-image. Reaching them needs a pluggable `Backend`
   seam and cross-building `core`/`widgets`/`runtime` (which are already backend-agnostic) — `terminal`
   and `dsl`'s JLine dependency are the coupling points. A browser-embeddable backend is the
   reactive-era analogue of Lanterna's Swing terminal.

Decision needed before starting either: whether Scala.js/Native are on the roadmap or a deliberate
non-goal — Terminus will use this in comparisons.

## Candidate future widgets (not yet scheduled)

Primarily the dataviz family that only blessed-contrib really delivers, plus a few content widgets:

- **Dataviz** — donut chart, seven-segment / LCD digits, gradient text, world map (braille + lat/lon
  markers), stacked gauge / gauge list.
- **Content** — a `RichLog`/object-inspector (pretty-print), and multi-series legends + axis ticks for
  `Chart` (Line/Scatter exist; Bar graph-type does not).
- **Input** — mouse `ScrollLeft`/`ScrollRight`, and `KeyModifiers` `Super`/`Hyper`/`Meta` +
  `KeyEventKind` (Press/Repeat/Release) for fuller kitty-protocol fidelity.
