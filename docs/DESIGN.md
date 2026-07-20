# glyphora — Design, Positioning & Roadmap

*A synthesis of a deep comparative study of **ratatui** (Rust), **bubbletea / bubbles / lipgloss**
(Go), and **creativescala/terminus** (Scala), turned into a concrete plan for glyphora's DSL and
feature set.*

> Full reference inventories that back every claim here live alongside this study; each cites the
> upstream source file it draws from. This document distills them into (1) where glyphora sits in the
> design space, (2) the target "best-of-all" DSL, and (3) a prioritized, honest roadmap with a status
> column that tracks what has actually shipped.

---

## 1. Where glyphora sits

The four libraries occupy three different points in the design space:

| Library | Rendering | State / architecture | User-facing DSL |
|---|---|---|---|
| **ratatui** | immediate-mode `Buffer`/`Widget`, Cassowary layout | caller-owned, `&mut state` each frame | builder structs (`Block::default().borders(…)`) |
| **bubbletea + bubbles** | immediate `View() string` + lipgloss | **Elm** — `Model`/`Update`/`View`, `Cmd`/`Msg` | Elm update loop + component structs |
| **terminus (ui)** | retained component tree | **fine-grained signals** + capabilities | `Column(size, style){ body }`, `Signal`, `?=>` caps |
| **glyphora** | retained `Element` tree → `Widget` → `Buffer` | **fine-grained signals** (`Signal`/`Computed`) | `panel(...)`, `text(...).bold.color(…)`, `view(using ReactiveScope)` |

**Conclusion from the study:** glyphora already made the right core bet. It shares terminus-ui's
architecture (retained tree + automatic-dependency signals + one-frame-per-event batching), but ships a
**far larger widget catalog than ratatui and bubbles combined**, plus app-chrome none of the three
references have (focus/tab order, click-to-focus, command palette, toasts, screen stack, splash, themed
scaffold, a post-render effects engine). It re-renders **only what changed** — bubbletea re-runs
`Update`+`View` for every message; glyphora repaints only when an observed signal changes.

So this is not a rewrite. The work is **closing concrete feature gaps** and **sharpening the surface
grammar** so the DSL reads as "the best of terminus's ergonomics + React/Compose's declarative tree +
ratatui/bubbles's feature depth."

### On "copy-paste their code as is"

The reference libraries are Rust and Go; their code cannot be pasted into a Scala 3, reflection-free,
GraalVM-native library. Every feature below is a **faithful idiomatic port**, not a transliteration —
which is also what the rest of the request (a terminus-like / JSX-like / Compose-like Scala DSL)
requires. Where a behavior is subtle (constraint arbitration, grapheme width, word-wrap trimming) the
port matches the upstream *semantics*, verified by tests, rather than the upstream syntax.

---

## 2. The target DSL — "best of all"

The unifying idea: **a declarative, signal-driven element tree (React/Compose) whose leaf and styling
grammar reads like terminus, backed by ratatui-grade rendering primitives and bubbles-grade interactive
components.**

Four grammar principles, drawn one each from the references:

1. **Declarative tree, reactive leaves (React/Compose + terminus-ui).** `view` returns an `Element`
   tree; state lives in `Signal`s; reading a signal in the view re-renders exactly what depends on it.
   *Already true in glyphora.* Sharpened by naming the shape: `type View = ReactiveScope ?=> Element`.

2. **Verbs read like prose, styling nests (terminus core).** `text("hi").bold.color(Color.Cyan)` for a
   leaf; `withStyle(_.dim)(column(...))` to push a default style onto a subtree (terminus's
   auto-restoring `foreground.green { … }`, expressed as a retained node). Named keys replace
   escape-code matching: `panel(...).onKey(Key.Up){ … }` instead of
   `case KeyEvent(KeyCode.Up, _) => …; true`.

3. **Layout is a first-class, ratatui-faithful solver.** Constraints (`Length/Percentage/Ratio/Min/
   Max/Fill`) **plus Flex modes** (`Start/End/Center/SpaceBetween/SpaceAround/SpaceEvenly`) and
   per-axis margins — so `row(...).center` and `row(...).spaceBetween` just work.

4. **Effects & async are structured (bubbletea `Cmd`).** Background IO that resolves back into signals
   without hand-rolled threads: `Async.run(fetch())( result => data.set(result) )`, plus `every` / `after`
   timers — the piece glyphora most lacked for real apps.

The result, in one screen:

```scala
object Dashboard extends TuiApp:
  val rows   = Signal(Vector.empty[Row])
  val filter = TextInputState()

  override def bindings = KeyBindings(
    binding("r", "refresh")(load()),
    binding("q", "quit")(quit()),
  )

  def load(): Unit =
    Async.run(api.fetchRows()) { fetched => rows.set(fetched) }   // off-thread, marshaled back

  def view(using ReactiveScope): Element =
    scaffold(statusBar = Some(statusBar(bindings))) {
      column(
        input(filter, "filter…").length(1),
        panel("Services")(
          table(rows.get.map(_.cells), Constraint.Fill(1), Constraint.Length(8)),
        ).fill,
      ).spaceBetween                                             // flex layout
        .onKey(Key.CtrlR) { load() }                             // named keys, no ceremony
    }

  // the `View` alias names the shape for composed sub-views:
  //   def header(using ReactiveScope): Element  ==  val header: View
```

> The app's `view` keeps its `def view(using ReactiveScope): Element` signature (unchanged public API);
> the `View` alias (`ReactiveScope ?=> Element`) is for naming composed sub-view helpers and teaching the
> one shape every view has.

---

## 3. Prioritized roadmap

Ranked by value across the three studies. **Status** tracks this workstream:
✅ shipped · 🟡 partial · ⬜ planned.

### Tier 1 — foundational, cross-cutting

| # | Item | Sources | Status |
|---|---|---|---|
| 1 | **Layout Flex modes** (Center/SpaceBetween/SpaceAround/SpaceEvenly/End) + per-axis margins | ratatui `flex.rs` | ✅ |
| 2 | **16 ANSI colors** (bright variants) + `Color.hex` parsing + color math (`lighten`/`darken`/`blend`/`mix`) | ratatui `color.rs`, lipgloss `color.go`/`blending.go` | ✅ |
| 3 | **Removable modifiers** (`sub_modifier`): `Style.without*` + `Modifiers` remove ops so a patch can un-bold | ratatui `style.rs` | ✅ |
| 4 | **DSL surface grammar**: `View` alias, named `Key` constants, `onKey(Key.X){…}` sugar, subtree `styled{}` scope | terminus | ✅ |
| 5 | **Structured async** (`Cmd` analog): `Async.run`/`runCatching` + `every`/`after` timers marshaling onto the render thread | bubbletea `commands.go` | ✅ |
| 6 | **Rect ops**: per-axis `inner`, `centered`, `offset`, `clamp`, `union`, `intersects` | ratatui `rect/ops.rs` | ✅ |

### Tier 2 — widget & text depth

| # | Item | Sources | Status |
|---|---|---|---|
| 7 | **Paragraph word-wrap + scroll offset + trim** (`WordWrapper` semantics) | ratatui `reflow.rs` | ⬜ |
| 8 | **TextInput fidelity**: word nav (`Alt+←/→`), delete-word (`Ctrl+W`), emacs (`Ctrl+A/E/K/U`), password echo, char limit, validation | bubbles `textinput` | ⬜ |
| 9 | **List fidelity**: fuzzy filter, pagination, title/status bar, two-line item delegate | bubbles `list` | ⬜ |
| 10 | **Table selection/highlight + footer** on the plain `Table` | ratatui `table.rs` | ⬜ |
| 11 | **Chart legend + axis titles/labels + Bar/Area graph types** | ratatui `chart.rs` | ⬜ |
| 12 | **Shared `symbols` module** (bar/block/line/border/braille/scrollbar/marker) → custom & dashed borders, scrollbar arrows, marker choice | ratatui `symbols/*` | ⬜ |
| 13 | **timer / stopwatch** components (trivial on `every`) | bubbles `timer`/`stopwatch` | 🟡 (via `every`/`after`) |

### Tier 3 — styling & polish

| # | Item | Sources | Status |
|---|---|---|---|
| 14 | **Adaptive light/dark colors** + OSC-11 terminal background query | lipgloss `color.go`/`query.go` | ⬜ |
| 15 | **Per-side padding + margins** on Block; multiple/bottom titles; 12 border types | ratatui/lipgloss | ⬜ |
| 16 | **Gauge unicode sub-cell fill**, **Sparkline direction/absent-values**, **Scrollbar arrows**, **BarChart grouping/horizontal** | ratatui | ⬜ |
| 17 | **`Line`/`Text` alignment & style fields** (alignment travels with the data) | ratatui `text/line.rs` | ⬜ |
| 18 | **tea.Exec** — suspend TUI to run `$EDITOR`/shell and resume | bubbletea `exec.go` | ⬜ |
| 19 | Bundled palettes (Tailwind/Material), border merging, block shadow, cursor blink | ratatui/lipgloss/bubbles | ⬜ |

---

## 4. What glyphora already does better (keep these)

From all three studies combined — do not regress:

- **Fine-grained reactivity** with automatic dependency tracking; repaint only what changed.
- **Retained, pattern-matchable `Element` tree** — construction is unit-testable data.
- **Batteries-included app chrome**: focus/tab order, click-to-focus, stable focus keys, fuzzy command
  palette, toasts, splash, screen/modal stack, themed `scaffold`.
- **`DataTable`** with built-in sort/filter/pagination — beats bubbles `table`.
- **`TextArea` undo/redo** — bubbles has none.
- **Largest widget catalog** of the four (charts, Markdown, Image, Calendar, Tree/DirectoryTree,
  SplitPane, BigText, …).
- **Dependency-free, grapheme-correct `CharWidth`** (CJK, ZWJ emoji, flags, combining marks).
- **First-class OSC-8 hyperlinks** as a `Style` field.
- **One-import ergonomics** (`import io.worxbend.tui.dsl.*`) — friendlier than terminus's `Terminal.` prefix.
- **Safe terminal restore** via a JVM shutdown hook even on SIGTERM/SIGHUP.
- **Clean separation**: `Style` = paint, `Constraint`/`Layout` = space, `Block` = box.

---

## 5. Non-negotiable house rules (all new code obeys)

- No `java.lang.reflect` / `Class.forName` (native-image, zero reflect-config).
- All width/truncation math through `CharWidth`, never `String.length`/`substring`.
- Warnings are errors; scalafmt owns formatting.
- Additive changes to `tui-core` (the stability anchor).
- Every widget: render-to-`Buffer` tests; every interactive element: an end-to-end `Pilot` test.
