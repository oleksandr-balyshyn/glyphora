# Roadmap: beyond v0.2.0

Researched against the mature TUI ecosystems — charmbracelet **bubbles** (Go),
**Textual**'s widget gallery and app chrome (Python), the **awesome-ratatui**
third-party widget ecosystem (Rust — a signal of what users need badly enough to
build themselves), and **tachyonfx** (ratatui's effects engine, whose post-render
buffer-transform model maps directly onto our `Buffer`). Each milestone below is
independently shippable; items marked ★ are the enablers other items depend on.

## Guiding observations from the research

- Every ecosystem converges on the same app chrome: **header/top bar, footer with
  key-binding hints, status line, screens/navigation, toasts, command palette**
  (Textual ships all of these as first-class; bubbles ships `help` + `key` to
  auto-generate footer hints from declared bindings).
- The most-built third-party ratatui widgets are: **scroll view, overlay/modal
  primitives, toasts, big text, image, editor, tabs-with-content** — i.e. exactly
  the gaps between a widget kit and an application framework.
- tachyonfx's animation model: effects are **stateful post-render transforms** —
  widgets render normally, then `effect.process(elapsed, buffer, area)` mutates
  cells; combinators (`sequence`, `parallel`, `repeat`, `ping_pong`, easing timers)
  compose them. This drops into our architecture with zero changes to `Widget`.

---

## 0.3.0 — App chrome & theming ("make real apps look real")

**Low-level enablers**
- ★ `Borders` bitset on `Block` (per-side borders), title alignment (left/center/
  right), and `padding` — needed by every chrome preset below.
- ★ `Theme`: semantic styles (`primary`, `accent`, `muted`, `error`, `surface`,
  `focusStyle`…) provided ambiently via `given Theme`; widgets/DSL default their
  styles from it. Ships with 2–3 presets (dark/light/high-contrast) + runtime
  switching (a `Signal[Theme]`).
- `Rule` (horizontal/vertical separator with optional label), `Button` (we have no
  plain button!), `BigText`/`Digits` (block-glyph large text — also the splash
  building block), `Log` (append-only scrolling text with follow-tail).

**High-level presets (`tui-dsl`)**
- ★ **`Scaffold`** — the app-view preset the DSL composes around:
  ```scala
  scaffold(
    topBar = topBar(title = "glyphora", tabs = Seq("Files", "Logs")),
    sidebar = sidebar(directoryTree(state), width = 30, collapsible = true),
    statusBar = statusBar.fromBindings, // auto key hints, mode indicator, clock
  )(content)
  ```
  Variants: `sidebarLayout(left, main)`, `masterDetail`, `threeColumn`,
  `centered(w, h)`, `grid(cols)(cells*)`.
- ★ **`KeyBindings` registry** (bubbles' `key`+`help` pattern): declare
  `binding("q", "quit")(quit())` once → routed dispatch, auto-generated status-bar
  hints, and a `?` help overlay. Replaces ad-hoc `onKeyEvent` matches in apps.

## 0.4.0 — Overlays & navigation ("multiple screens, things on top")

- ★ `Buffer.blit` + **layer compositing**: `layers(base, overlays*)` with z-order —
  the enabler for everything floating.
- ★ **`ScrollView`**: render a child at its natural size into an offscreen buffer,
  blit the visible window, wire the scrollbar (the single most-requested
  third-party ratatui widget).
- **Screen stack**: `pushScreen`/`popScreen`/modal screens with per-screen focus
  state (was a deliberate v1 non-goal — SPEC §7 — now the natural step; record the
  decision when implemented).
- **Toasts/notifications**: `notify("Saved", Level.Info)` → queued, TTL-driven
  (ticks), corner placement, entry/exit animation once 0.5.0 lands.
- **Command palette** (Ctrl+P): fuzzy-filtered command list over the `KeyBindings`
  registry.
- `TabbedContent` (tabs + content switcher — we only have the tab *row*),
  `SplitPane` (draggable divider; mouse support exists), `Collapsible`, `Tooltip`.

## 0.5.0 — Motion ("splash screens and animations")

- ★ **Effects engine** (tachyonfx-shaped, original implementation):
  ```scala
  trait Effect:
    def process(elapsed: FiniteDuration, buffer: Buffer, area: Rect): Unit
    def isDone: Boolean
  ```
  Applied after the view renders, driven by the existing tick loop; a running
  effect keeps invalidating until done. Combinators: `sequence`, `parallel`,
  `repeat`, `pingPong`, `delay`, `Easing` (linear/quad/cubic/sine in-out).
  Core effects: `fadeIn`/`fadeOut` (RGB interpolation toward the background),
  `slideIn`/`slideOut`, `dissolve`/`coalesce` (seeded random cell reveal),
  `sweep`, `typewriter`, `pulse`/`shimmer`, `marquee`.
- **`Tween[A]`**: animate values (gauge ratios, offsets, colors) over a duration
  with easing — app-side animation without touching effects.
- **Splash screen preset** (composes the above):
  ```scala
  override def splash = SplashScreen(
    bigText("glyphora").color(Color.Cyan),
    effect = Effect.coalesce(1.second, Easing.QuadOut),
    minimumDuration = 1500.millis, // then auto-transition to view
  )
  ```
- `Spinner` gains indeterminate `ProgressBar` mode; `Skeleton` placeholders
  (pulse/sweep while loading).

## 0.6.0 — Input & data completeness

- `RadioSet`/`RadioButton`, `Slider`, `NumberInput`, `MaskedInput` (template-based),
  `SelectionList` (multi-select), `Paginator`, `Timer`/`Stopwatch` components.
- **Autocomplete / fuzzy-finder input** (fzf-style; also powers the palette).
- **`FilePicker`** (composes `DirectoryTree` + input + bindings).
- Viz extras: `PieChart`, `StackedBarChart`, `Heatmap`; braille + half-block
  `Canvas` markers (2×/2×4 sub-cell resolution for `Chart`).
- `Link` (OSC 8 hyperlinks — needs a `Cell` attribute + backend support).

## Later / explicitly hard

- **Image widget** (Kitty/iTerm2/Sixel/half-block) — highest platform risk; the
  half-block fallback is cheap and could ship first.
- **PTY/terminal widget** (run a shell inside a pane) — large, JVM PTY handling.
- **Syntax-highlighted code editor** — `TextArea` + an incremental highlighter;
  regex-based grammar first, tree-sitter-class engines out of scope.
- MiMa binary-compatibility checks once 1.0 API freezes; Scala Native cross-build.

## Suggested order of attack

1. 0.3.0's enablers (`Borders`/padding, `Theme`) are small and unblock everything
   visual; `Scaffold` + `KeyBindings` deliver the "app view / top bar / status
   line" experience immediately.
2. 0.4.0's `blit`+layers is the single highest-leverage engine change (unlocks
   scroll view, toasts, palette, splash overlay) — do it before any of them.
3. The effects engine is self-contained and can proceed in parallel with 0.4.0.
