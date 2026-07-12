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

> **0.3.0 shipped** — Borders/title-alignment/padding on `Block`; `Button`, `Rule`,
> `BigText` (3×5 font), `Log` (+follow-tail state); ambient `Theme` (dark/light/
> high-contrast); `KeyBindings` registry with key-spec parser and app-level dispatch;
> `scaffold`/`topBar`/`statusBar`/`sidebar`/`helpOverlay` + layout presets.
>
> **Post-0.3.0 brainstorm (carried into 0.4.0):**
> - *Intrinsic-size protocol*: `defaultConstraint` is orientation-blind — a text
>   element defaulting to `Length(1)` means one **column** inside a `row`. Replace
>   with `preferredSize(direction)` while building the offscreen-render machinery
>   (ScrollView needs natural sizes anyway).
> - *Showcase example*: one `examples/showcase` app exercising scaffold + theme
>   switching + bindings + (later) toasts/palette/effects — doubles as living docs
>   and the manual PTY test bed.
> - *Tab-switching helpers* for `topBar` once screens land (tab index ↔ screen).

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

> **0.4.0 shipped** — `Buffer.blit` + `layers` z-order compositing; direction-aware
> `preferredSize` (fixed the orientation-blind defaults); `ScrollView`,
> `TabbedContent`, `Collapsible`, `SplitPane`; screen stack (modal with focus trap /
> full replacement); tick-aged toasts (`notify`); fuzzy command palette (`Ctrl+P`
> over `KeyBindings`); `examples/showcase` as the living chrome demo.
>
> **Post-0.4.0 brainstorm (carried into 0.5.0):**
> - The palette selection could reuse `ListView` once list items accept styled
>   lines with per-item actions — consider a shared `Menu` primitive (also usable
>   for context menus and the sidebar).
> - `ScrollView` needs explicit `contentHeight`; an intrinsic *measure* pass
>   (preferredSize exists, but only per-axis constraint, not content-driven pixels)
>   would remove that wart — revisit alongside effects, which also need per-element
>   areas.
> - Toast entry/exit should animate once effects land (slide-in from the right).
> - `SplitPane` divider should be mouse-draggable (mouse drag events already decode).

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

> **0.5.0 shipped** — effects engine in `tui-runtime` (`Easing`, `Tween`, fade/
> coalesce/dissolve/sweep/slide/typewriter/pulse + sequence/parallel/delay/repeat);
> `Frame.applyEffect`; `TuiApp.runEffect` (tick-driven, auto-pruned) and the
> skippable `SplashScreen` preset (showcase now opens with a coalescing GLYPHORA
> logo); `Skeleton`/`IndeterminateBar`/`Marquee` loading widgets.
>
> **Post-0.5.0 brainstorm (carried into 0.6.0):**
> - Effects currently target the whole frame; per-element effects need the element's
>   rendered `Rect` — the FocusTracker area-recording mechanism generalizes to an
>   area registry (also what toast slide-in needs).
> - Lesson learned twice: state flipped during *render* can't schedule the next
>   redraw — all time-based state transitions belong in the Tick handler. Candidate
>   for a small `Timeline` abstraction owning (start, duration, onDone) triples.
> - `Tween` is unused by any widget yet — a `gauge(tween.at(elapsed))` example
>   belongs in the showcase once per-element areas land.

## 0.6.0 — Input & data completeness

- `RadioSet`/`RadioButton`, `Slider`, `NumberInput`, `MaskedInput` (template-based),
  `SelectionList` (multi-select), `Paginator`, `Timer`/`Stopwatch` components.
- **Autocomplete / fuzzy-finder input** (fzf-style; also powers the palette).
- **`FilePicker`** (composes `DirectoryTree` + input + bindings).
- Viz extras: `PieChart`, `StackedBarChart`, `Heatmap`; braille + half-block
  `Canvas` markers (2×/2×4 sub-cell resolution for `Chart`).
- `Link` (OSC 8 hyperlinks — needs a `Cell` attribute + backend support).

> **0.6.0 shipped** — form controls (`RadioGroup`, `Slider`, `SelectionList`,
> `NumberInput`, `MaskedInput`, `Paginator`); `Autocomplete` + `FilePicker`; canvas
> braille/half-block sub-pixel resolutions; `PieChart`, `StackedBarChart`, `Heatmap`.
> Timer/Stopwatch components were deliberately skipped — ticks + `Tween` already
> cover that logic without dedicated widgets.
>
> **Post-0.6.0 brainstorm (the 0.7–0.9 arc):**
> - **0.7.0 "polish & interaction"**: mouse upgrades (click buttons/checkboxes via
>   hit-testing, scroll wheel in lists/scrollviews, SplitPane drag); OSC 8 `Link`
>   (needs a `Cell` attribute + backend support — the first core change since 0.2);
>   per-element effect targeting via a rendered-area registry; `Chart` with braille
>   default + axis labels.
> - **0.8.0 "images & docs"**: half-block image widget (RGB cell pairs; Kitty/Sixel
>   protocols stay out); Scaladoc sweep over `tui-core`/`tui-dsl` public APIs; a
>   `docs/` cookbook generated from the examples.
> - **0.9.0 "hardening"**: publishLocal + consumption test from a scratch project;
>   MiMa baseline; render-loop benchmark rerun + budget assertions; PTY matrix
>   re-run; API deprecation pass (rename inconsistencies before a 1.0 freeze).

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
