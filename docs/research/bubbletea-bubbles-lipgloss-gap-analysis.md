# Bubbletea / Bubbles / Lipgloss vs Glyphora — Feature Inventory & Gap Analysis

Reference sources are the **v2** lines of the Charm libraries (bubbletea imports `ultraviolet`/`colorprofile`; lipgloss uses `image/color`; bubbles is `charm.land/bubbles/v2`). Glyphora is the Scala 3 library under `io.worxbend.tui.*`. All Go citations are file-relative to each ref repo; Scala citations are file-relative to the worktree.

---

## 1. Runtime / Architecture

### 1.1 Bubbletea's model (Elm)

- **Model interface** (`tea.go`): `Init() Cmd`, `Update(Msg) (Model, Cmd)`, `View() View`. State is threaded explicitly through immutable `Model` values; every message runs `Update`, producing a new model + an optional command, then `View()` re-renders the whole frame.
- **Cmd** (`tea.go`): `type Cmd func() Msg` — an async IO thunk. **Msg** (`tea.go`): `type Msg = any`. There is no `Sub` type; "subscriptions" are self-re-issuing `Tick`/`Every` commands (`commands.go`).
- **Command scheduling** (`tea.go` event loop + `handleCommands`): commands are pushed onto a `cmds` channel; each runs in its **own leaked goroutine** and its result is `Send`-ed back as a Msg. `Batch(cmds...)` (`commands.go`) runs concurrently (WaitGroup); `Sequence(cmds...)` (`commands.go`) runs serially/ordered. Both `BatchMsg`/`sequenceMsg` are intercepted before `Update`. `Tick(d, fn)` fires once after `d`; `Every(d, fn)` aligns to the system clock so components stay in phase.
- **Other Cmds**: `Quit`, `Interrupt`, `Suspend`, `Println/Printf` (unmanaged lines above the app, `renderer.go`), `ClearScreen` (`screen.go`), `Raw` (`raw.go`), clipboard read/write (`clipboard.go`), background/foreground/cursor-position/terminal-version queries (`color.go`, `cursor.go`, `xterm.go`), `RequestWindowSize` (`commands.go`).
- **`tea.Exec` / `ExecProcess`** (`exec.go`): suspend the program to run a foreground subprocess (`$EDITOR`, shell). `p.exec` (`exec.go`) calls `releaseTerminal` (restore state, stop renderer, cancel input) → `c.Run()` → `RestoreTerminal` (`tea.go`), then delivers the `ExecCallback(error) Msg`. Runs **inline/blocking** in the loop.
- **Program options** (`options.go`): `WithContext`, `WithInput/Output/Environment`, `WithoutSignalHandler`, `WithoutCatchPanics`, `WithoutSignals`, `WithoutRenderer`, `WithFilter(func(Model,Msg)Msg)` (drop/rewrite every msg before Update), `WithFPS` (renderer ticker, default 60/cap 120), `WithColorProfile`, `WithWindowSize`. (In v2 the old `WithAltScreen`/mouse/paste/focus toggles migrated to declarative `View` fields: `AltScreen`, `MouseMode`, `ReportFocus`, `DisableBracketedPasteMode`, `WindowTitle`, `Cursor`.)
- **Renderer** is FPS-decoupled: `Update`→`render` only *stages* a frame; a `time.Ticker` at `WithFPS` paints it (`tea.go startRenderer`). Signals become msgs: SIGINT→`InterruptMsg`, SIGTERM→`QuitMsg` (`tea.go handleSignals`), SIGWINCH→`WindowSizeMsg` (`signals_unix.go`). Input: `KeyMsg`/`KeyPressMsg` (`key.go`), `MouseMsg` variants + modes (`mouse.go`), `PasteMsg`/`PasteStart/EndMsg` (`paste.go`), `FocusMsg`/`BlurMsg` (`focus.go`).

### 1.2 Glyphora's model (fine-grained signals + retained tree + render loop)

Glyphora is **not** Elm. Its architecture:

- **Reactivity** (`runtime/Reactive.scala`): `Signal[A]` (mutable, `set`/`update`, notifies subscribers) and `Computed[A]` (lazy, auto-tracked dependencies via `ReactiveScope`). Reads inside a tracking scope subscribe automatically — no dependency arrays, no manual `Msg` plumbing, no threaded `State`. `Signal.set` from a non-render thread is rejected by `RenderThread.checkRenderThread()`.
- **Render loop** (`runtime/TerminalRunner.scala`): a single blocking loop on the calling thread (= the render thread). It sets up raw mode + alt screen + hide cursor (+ optional mouse capture), then: drain queued render-thread work → redraw if invalidated → `backend.readEvent(pollTimeout)` → dispatch → redraw on demand → emit `Event.Tick` when `config.tickRate` elapses. Redraws are **diff-driven** (`backend.draw(buffer)`) and only happen when a subscribed signal changed (`redrawRequested = () => invalidated`, `dsl/TuiApp.scala`). A JVM shutdown hook restores the terminal on signal termination (`TerminalRunner.scala`).
- **Retained Element tree** (`dsl/Element.scala`): a sealed, pattern-matchable data hierarchy; each node renders through a core `Widget`. The `view` (`dsl/TuiApp.scala`) is re-evaluated under the tracking scope each generation; a focus pass (`dsl/Focus.scala`) marks the focused node and wraps focusables in `TrackedElement`s for click hit-testing.
- **Events** (`dsl/TuiApp.scala`, `dsl/EventRouter.scala`): `Event.Key` bubbles focused-leaf→root (user `onKey` then framework `builtinKeyHandler`); `Event.Mouse` hit-tests, focuses on `Down`, routes to the tracked element; `Event.Paste` → `builtinPasteHandler` on the focused element; `Event.FocusGained/Lost` → `onTerminalFocus`; `Event.Resize`; `Event.Tick` → `onTick` + toast aging + splash/effect progress. So **bracketed paste and focus reporting are supported as events**, just not as toggleable options.
- **Config** (`runtime/Runner.scala`): `RunnerConfig(tickRate: Option[Duration], mouseCapture: Boolean)` — that is the entire option surface.
- **Async escape hatch**: `RunnerHandle.runOnRenderThread` + `RenderThread.drainPending` let a user-spawned thread do IO and marshal a `Signal` update back onto the render thread. This is the *only* async mechanism — unstructured, uncancellable, no library-owned scheduling.
- **Animation / effects** (`runtime/Effect.scala`): a post-render frame-transform system (tachyonfx model): `fadeIn/fadeOut`, `coalesce/dissolve`, `sweepIn`, `slideInFromRight`, `typewriter`, `pulse`, plus combinators `sequence/parallel/delay/repeat` with `Easing`. Driven by `tickRate` + `runEffect`.

### 1.3 What glyphora's model lacks vs bubbletea

| Bubbletea capability | Glyphora equivalent | Gap |
|---|---|---|
| `Cmd`/`Msg` async command system | raw threads + `runOnRenderThread` | **No structured command abstraction.** No return-a-command-from-a-handler, no library-scheduled async, no result-as-event plumbing. |
| `tea.Batch` (concurrent) / `tea.Sequence` (ordered) | none | No composition/ordering of async work. |
| `Tick(d,fn)` / `Every(d,fn)` | one global `config.tickRate` + `onTick()` | No per-source timers, no clock-aligned ticks, no one-shot scheduled messages. |
| `tea.Exec` / `ExecProcess` (suspend to run subprocess) | none | **Cannot suspend the TUI to run `$EDITOR`/shell and resume.** `enterAlternateScreen`/raw mode teardown are not exposed for release/restore. |
| Request/response Cmds (window size, clipboard, bg color, cursor pos, terminal version) | resize event only | No clipboard read/write, no terminal capability queries, no OSC background-color query. |
| `WithFilter` global msg interceptor | none | No single choke point to log/drop/rewrite all events. |
| `WithFPS` decoupled renderer ticker | synchronous redraw + fixed 100 ms poll / tick-driven | No frame-rate cap independent of update; redraw is inline. |
| `Println/Printf` above the program, `ClearScreen`, `SetWindowTitle`, `Raw` passthrough | none | No unmanaged scrollback output, no title, no raw escape injection. |
| `Suspend`/`Interrupt`/ctrl-Z job control | ctrl+C quit only | No SIGTSTP suspend/resume. |
| Runtime alt-screen / mouse-mode / paste / focus **toggling** | fixed at startup (`mouseCapture` bool) | Modes can't change after launch; only mouse on/off (no cell-motion vs all-motion). |

**Glyphora's model advantages** are real and belong in the ledger (see §5): only-what-changed redraws via fine-grained tracking (bubbletea re-runs `Update`+`View` for every msg), a pattern-matchable retained tree, and a far richer built-in animation/effect system.

---

## 2. Components (bubbles) → glyphora mapping

Legend: **Full** = comparable fidelity · **Partial** = core present, knobs missing · **Missing** = no equivalent.

| bubbles component (Go file) | Key behaviors / knobs | Glyphora equivalent | Status | Gap |
|---|---|---|---|---|
| **textinput** (`textinput/textinput.go`) | suggestions/autocomplete, `Validate`, word nav (`alt+←/→`,`ctrl+f/b`), delete-word (`ctrl+w`,`alt+backspace`,`alt+d`), emacs (`ctrl+a/e/k/u/d/h`), echo/password mode, `CharLimit`, width scrolling, paste, cursor modes | `TextInput`/`TextInputState`, `InputElement` (`widgets/TextInput.scala`, `dsl/Element.scala`) | **Partial** | Has insert/backspace/delete/←→/home/end, grapheme clusters, horizontal scroll, placeholder, paste (newlines folded). **Missing: validation, word-wise nav & delete, emacs bindings (ctrl+a/e/k/u/w), echo/password mode, char limit.** Autocomplete is a *separate* `AutocompleteElement` (subsequence, not prefix); number/mask are separate `NumberInputElement`/`MaskedInputElement`. |
| **textarea** (`textarea/textarea.go`) | multi-line, soft-wrap (memoized), line numbers, viewport scroll, `MaxHeight/Width`, `CharLimit`, dynamic height, prompt func, word nav, transforms | `TextArea`/`TextAreaState`, `TextAreaElement` (`widgets/TextArea.scala`) | **Partial** | Has multi-line `(line,col)` grapheme editing, vertical+horizontal scroll following cursor, **undo/redo** (bounded 100-deep, a bubbles gap!), arrows/home/end/newline. **Missing: soft-wrapping (only horizontal scroll), line numbers, char limit, word nav, dynamic height, custom prompt, transforms.** |
| **list** (`list/list.go`) | fuzzy filter, pagination, status bar, title, item delegate (two-line/description), help integration, spinner, status message, show/hide toggles, infinite scroll | `ListView`/`ListState`, `ListElement`; `SelectionListElement` for multi-select (`widgets/ListView.scala`, `dsl/Element.scala`) | **Partial** | Has selection, offset-follows-selection scroll, up/down, wheel, highlight symbol, multi-select variant. **Missing (flagship gaps): filtering, built-in pagination, status bar, title, item delegate / two-line items, keybinding help, loading spinner, status message, show/hide toggles.** |
| **table** (`table/table.go`) | width-driven columns, row nav, focus, page/half-page/goto keys, styles | `DataTable`/`DataTableState`, `DataTableElement`; simpler `TableElement` (`widgets/DataTable.scala`, `dsl/Element.scala`) | **Full+** | `DataTable` **exceeds bubbles table**: numeric-aware **sort** (with ▲/▼), substring **filter**, **paging**, selection, scroll, header style. Up/down + PageUp/Down nav. Minor gaps: no half-page nav, no `g`/`G` goto keys, no explicit blur handling (element-level). |
| **viewport** (`viewport/viewport.go`) | line/page/half-page/goto scroll, horizontal scroll, mouse wheel + delta, gutter/line-number func, search highlights (`HighlightNext`, `EnsureVisible`), soft-wrap toggle, scroll percent | `ScrollView`/`ScrollViewState`, `ScrollViewElement`; `LogElement` for follow-tail (`dsl/Element.scala`) | **Partial** | Has vertical scroll, PageUp/Down (10 lines), wheel, content auto-measure, log tail-follow. **Missing: horizontal scroll, half-page, goto top/bottom keys, search/highlight, gutter/line numbers, configurable wheel delta, scroll-percent readout.** |
| **spinner** (`spinner/spinner.go`) | 12 predefined sets (Line/Dot/MiniDot/Jump/Pulse/Points/Globe/Moon/Monkey/Meter/Hamburger/Ellipsis), per-spinner FPS, self-ticking `Tick` cmd | `Spinner` (stateless frame counter) (`widgets/Spinner.scala`); plus `WaveText`, `Marquee`, `Skeleton`, `IndeterminateBar`, `Loading` | **Partial** | Only **2 frame sets** (Braille, Line); stateless-by-design (app advances `frame` via `onTick`, no self-ticking command). Extra animated widgets partly compensate. |
| **progress** (`progress/progress.go`) | solid/gradient/dynamic fill, spring animation (harmonica), percent format, `ViewAs`, width | `Gauge`, `LineGauge`, `IndeterminateBar` (`widgets/Gauge.scala`, etc.) | **Partial** | `Gauge` has ratio + label + filled style. **Missing: gradient fill, spring/animated percent, percent format.** (Glyphora offsets via the `Effect`/`Tween`/`Easing` system for animation generally.) |
| **paginator** (`paginator/paginator.go`) | Dots vs Arabic, `GetSliceBounds`, per-page, keymap | `Paginator`/`PaginatorElement` (`dsl/Element.scala`) | **Full** | Current/total + left/right. Minor: slice-bounds helper / arabic-format knobs less exposed. |
| **filepicker** (`filepicker/filepicker.go`) | dir nav, selection, `AllowedTypes`, `ShowHidden`, permissions/size columns, `DirAllowed`, page keys, height | `FilePicker`/`FilePickerElement` + `DirectoryTree` (`dsl/Element.scala`) | **Partial** | Arrows + enter (open dir / choose file), chosen readout. **Missing: allowed-type filtering, show-hidden toggle, permission/size columns, dir-selection mode, page-nav keys.** |
| **help** (`help/help.go`) | short/full toggle, multi-column keymap render, width truncation + ellipsis, disabled-binding filtering | `KeyBindings` + `statusBar` + `helpOverlay` + command palette (`dsl/KeyBindings.scala`, `dsl/Chrome.scala`, `dsl/TuiApp.scala`) | **Partial** | Global declared bindings drive dispatch + status hints + a help dialog + a **fuzzy command palette** (a bubbles-absent plus). **Missing: per-component composable keymaps, short↔full toggle with column layout, width-aware ellipsis truncation, enabled/disabled filtering.** Model differs: app-global bindings vs bubbles' reusable per-component `key.Binding`s. |
| **key** (`key/key.go`) | `Binding` with multiple keys, `WithKeys/WithHelp/WithDisabled`, `Matches` helper | `KeyBinding` + `KeyBindings.parseKey` (`dsl/KeyBindings.scala`) | **Partial** | Spec-string parse (`"ctrl+s"`), single trigger + label + description + `showInHints`, equality match. **Missing: multiple keys per binding, disabled state, reusable `Matches` for per-component use** (glyphora widgets hardcode their keys in `builtinKeyHandler`). |
| **timer** (`timer/timer.go`) | countdown, interval, start/stop/toggle, `TickMsg`/`TimeoutMsg` | none (app-owned counter via `onTick`) | **Missing** | No structured countdown timer with timeout event. |
| **stopwatch** (`stopwatch/stopwatch.go`) | count-up, start/stop/reset, elapsed | none (app-owned counter via `onTick`) | **Missing** | No structured stopwatch. |
| **cursor** (`cursor/cursor.go`) | blink modes (Blink/Static/Hide), `BlinkSpeed`, blink command | cursor = reverse-styled cell under position, `showCursor` bool per widget (`widgets/TextInput.scala`) | **Partial** | No blink modes, no blinking (hardware cursor hidden at startup). Static block cursor only. |

**Bubbles components with no bubbles analog that glyphora *adds*:** charts (`BarChart`, `StackedBarChart`, `PieChart`, `Heatmap`, `Chart`, `Sparkline`, `DualSparkline`, `Canvas`), `BigText`, `WaveText`, `Marquee`, `Markdown`, `Image`, `Calendar`, `Dialog`, `Tree`/`DirectoryTree`, `SplitPane`, `TabbedContent`, `Collapsible`, `Slider`, `RadioGroup`, `Toggle`, `Checkbox`, `Button`, `Rule`.

---

## 3. Styling / Layout (lipgloss)

### 3.1 Lipgloss model

- **One fluent immutable `Style`** (`style.go`, `set.go`) carrying *everything*: text attrs (Bold/Italic/Underline+variants/Strikethrough/Reverse/Blink/Faint), Foreground/Background/UnderlineColor, **Width/Height/MaxWidth/MaxHeight**, **Padding** (per-side + CSS shorthand + `PaddingChar`), **Margin** (per-side + shorthand + `MarginBackground`/`MarginChar`), **Border** (style + per-side enable + per-side fg/bg + `BorderForegroundBlend` perimeter gradient), **Align** (H + V), `Inline`, `Transform`, `ColorWhitespace`, `UnderlineSpaces`, `StrikethroughSpaces`, `TabWidth`, `Hyperlink`, `Inherit`. Full `Get*`/`Unset*` mirrors.
- **Borders** (`borders.go`): a 13-glyph `Border` struct (edges + corners + junctions), presets Normal/Rounded/Thick/Double/Block/InnerHalfBlock/OuterHalfBlock/Hidden/Markdown/ASCII, and **custom borders**; per-side size helpers.
- **Color** (`color.go`): hex/ANSI/256/RGB, `NoColor`, `LightDark(isDark)` adaptive factory, `Complete(profile)` profile factory, plus color **math** `Alpha/Lighten/Darken/Complementary`. Output **downsampling** truecolor→256→ANSI→mono via `colorprofile` writer (`writer.go`). Terminal **background detection** `HasDarkBackground` (`query.go`).
- **Layout**: `JoinHorizontal(pos,…)` / `JoinVertical(pos,…)` with cross-axis `Position` alignment (`join.go`); `Place`/`PlaceHorizontal`/`PlaceVertical` with `Position` floats + whitespace options (`position.go`); `AlignHorizontal/Vertical` of multi-line blocks (`align.go`); `Size`/`Width`/`Height` measuring (`size.go`); ANSI-preserving `Wrap` (`wrap.go`).
- **Advanced**: **gradients** `Blend1D`/`Blend2D` (Lab space, `blending.go`); **compositing** `Canvas`/`Layer`/`Compositor` with x/y/**z-order** + hit-testing (`canvas.go`, `layer.go`); **styled ranges** `StyleRanges`/`StyleRunes` (`ranges.go`, `runes.go`); whitespace fill with custom chars/style (`whitespace.go`).

### 3.2 Glyphora model

Glyphora **separates paint from layout from box** rather than unifying them:

- **`Style`** (`core/Style.scala`) = paint only: `fg`, `bg`, `modifiers` (Bold/Dim/Italic/Underline/Blink/Reverse/Hidden/CrossedOut), `link` (OSC 8). `patch` layers styles. **No** padding/margin/border/width/height/align/maxwidth/inline/transform on `Style`.
- **Layout / sizing** = `Constraint` (`Length/Percentage/Ratio/Min/Max/Fill`) solved by `Layout.split` (`core/Constraint.scala`, `core/Layout.scala`), consumed via `Row`/`Column`/`length/percent/fill/minSize/maxSize` DSL extensions (`dsl/dsl.scala`). Two-pass solver with deterministic largest-remainder distribution.
- **Box** = `Block` widget (`widgets/Block.scala`): 4 border types (Plain/Rounded/Double/Thick), **per-side** borders (`Borders` bitset), title + `Alignment` (Left/Center/Right), **uniform** `padding: Int`.
- **Color** (`core/Color.scala`) = `Reset` + 8 named + `Rgb` + `Indexed(256)`; `approximateRgb` for effect math / downsampling.
- **Compositing** = `LayersElement` (full-area z-stack) + `Canvas` widget (shape/braille drawing). Hit-testing exists but only for focus (`dsl/Focus.scala`).
- **Themes** (`dsl/Theme.scala`) = ambient `given Theme` with Dark/Light/HighContrast presets (semantic styles). Chrome presets `topBar/statusBar/sidebar/scaffold/centered/helpOverlay` (`dsl/Chrome.scala`).
- **Styled ranges**: the `Line`/`Span` model (a `Line` is `Seq[Span]`, each `Span` carries its own `Style`) provides per-range styling — **rough parity with `StyleRanges`**.

### 3.3 Lipgloss gaps in glyphora

| Lipgloss feature | Glyphora | Gap severity |
|---|---|---|
| Unified fluent `Style` (padding/margin/border/align/width all on one type) | split across Style + Constraint + Block | design difference; less convenient, arguably cleaner |
| Per-side **padding** (CSS shorthand) | `Block.padding` = one uniform Int | Medium |
| **Margin** (+ MarginBackground/Char) | none (spacers / layout only) | Medium |
| **MaxWidth/MaxHeight** + truncation-with-ellipsis at style level | none (silent area clipping; some widgets truncate individually) | Medium |
| **Align vertical** / align multi-line text within width | horizontal title `Alignment` only; block centering via `centered`/spacers | Medium |
| **`Place`/PlaceHorizontal/PlaceVertical** with arbitrary `Position` | `centered(w,h)` preset only | Medium |
| **`JoinHorizontal`/`JoinVertical` cross-axis alignment** | Row/Column pin to top/left; no `Position` alignment of unequal blocks | Medium |
| **Adaptive `LightDark` colors** + terminal **background detection** | manual Theme selection; no OSC-11 bg query | High |
| **Color-profile downsampling on output** | `approximateRgb` exists but not wired to a capability-aware writer | Medium |
| **Gradients** `Blend1D/Blend2D` | none (Effect fades scale brightness only) | Medium |
| **Color math** `Alpha/Lighten/Darken/Complementary` | none | Medium |
| **Custom border glyphs**, half-block/ASCII/markdown presets, **per-side border color**, **border gradient** | 4 fixed presets, single `borderStyle` | Low |
| Positioned **z-layers with x/y/z + hit-testing** (`Compositor`) | full-area layers only | Low |
| Whitespace **fill chars/pattern** | `FilledElement` fills with a style (space only) | Low |
| ANSI-preserving `Wrap`, `Inline`, `Transform`, per-attr `UnderlineSpaces`/`TabWidth` | Paragraph wraps; no Style-level transform/inline/tab-width | Low |

---

## 4. Ranked gap list

### High
1. **No structured async effect/command system.** No `Cmd`/`Msg` equivalent — async work is raw threads + `runOnRenderThread`, with no `Batch`/`Sequence`, no scheduled `Tick`/`Every`, no cancellation, no result-as-event. This is the single biggest architectural gap for real apps that load data, debounce, or fan out IO. (bubbletea `commands.go`, `tea.go`)
2. **No subprocess exec (`tea.Exec`).** Cannot suspend the TUI to run `$EDITOR`/`git`/a shell and resume; terminal release/restore isn't exposed. (bubbletea `exec.go`)
3. **`textinput` fidelity.** Add validation, word-wise navigation & deletion (`ctrl+w`, `alt+←/→`, `alt+d`), emacs bindings (`ctrl+a/e/k/u`), echo/password mode, char limit. (bubbles `textinput/textinput.go`)
4. **`list` fidelity.** Add filtering, pagination, status bar, title, two-line item delegate, and keybinding help — the flagship bubbles list is far ahead. (bubbles `list/list.go`)
5. **No adaptive colors / terminal background detection.** Add a `LightDark`-style adaptive color and OSC-11 background query so themes auto-fit the terminal; wire color-profile downsampling on output. (lipgloss `color.go`, `query.go`, `writer.go`)

### Medium
6. **`textarea`**: soft-wrapping, line numbers, char limit, word navigation. (`textarea/textarea.go`)
7. **`viewport`**: horizontal scroll, half-page, goto top/bottom keys, search/highlight, gutter/line numbers. (`viewport/viewport.go`)
8. **Styling**: per-side padding, margin, `MaxWidth`+ellipsis, vertical/block alignment, `Place`, `JoinHorizontal/Vertical` cross-axis alignment. (lipgloss `set.go`, `join.go`, `position.go`)
9. **Color math + gradients**: `Blend1D/2D`, `Lighten/Darken/Alpha/Complementary`. (lipgloss `blending.go`, `color.go`)
10. **`timer`/`stopwatch` components** (`timer/timer.go`, `stopwatch/stopwatch.go`) — trivial given `onTick`, currently absent.
11. **`filepicker`**: allowed-types, show-hidden, permission/size columns, dir-selection mode. (`filepicker/filepicker.go`)
12. **Global `WithFilter`-style event interceptor** and a **configurable FPS** cap decoupled from update. (bubbletea `options.go`, `tea.go`)
13. **`spinner`/`progress`**: more frame sets; gradient/spring-animated progress. (`spinner/spinner.go`, `progress/progress.go`)
14. **`help`**: per-component composable keymaps, short↔full toggle with columns, width-aware truncation, disabled-binding filtering. (`help/help.go`, `key/key.go`)

### Low
15. Custom border glyphs, half-block/ASCII/markdown border presets, per-side border color, border gradient. (lipgloss `borders.go`)
16. Cursor blink modes. (`cursor/cursor.go`)
17. Mouse-mode granularity (cell-motion vs all-motion), runtime mode toggling, `SetWindowTitle`, `Println/Printf` scrollback, clipboard read/write, raw escape passthrough, SIGTSTP suspend. (bubbletea `mouse.go`, `screen.go`, `clipboard.go`, `exec.go`)
18. Positioned z-layer compositor with hit-testing; custom whitespace fill chars. (lipgloss `layer.go`, `whitespace.go`)

---

## 5. What glyphora does better

1. **Fine-grained reactivity.** Signals/Computed with automatic dependency tracking (`runtime/Reactive.scala`) redraw **only when observed state changes** (`invalidated`/`redrawRequested`). Bubbletea re-runs `Update` + full `View()` for *every* message. No `Msg` boilerplate, no explicit `State` threading.
2. **Retained, pattern-matchable Element tree** (`dsl/Element.scala`) — construction is unit-testable data; styling rebuilds nodes immutably.
3. **Rich post-render Effect system** (`runtime/Effect.scala`): fade/coalesce/dissolve/sweep/slide/typewriter/pulse + sequence/parallel/delay/repeat with easing — far beyond bubbles' spinner + progress spring.
4. **Batteries-included app chrome** the Charm stack leaves to the user: built-in focus/tab order + click-to-focus + stable focus keys (`dsl/Focus.scala`), a **fuzzy command palette**, **toasts**, **splash screen**, a **screen/modal stack**, and `scaffold/topBar/statusBar/sidebar/masterDetail/centered/helpOverlay` presets (`dsl/Chrome.scala`, `dsl/TuiApp.scala`).
5. **`DataTable` beats bubbles `table`**: built-in numeric-aware sort, substring filter, and pagination (`widgets/DataTable.scala`).
6. **`textarea` undo/redo** (bounded stack) — bubbles' textarea has none (`widgets/TextArea.scala`).
7. **Much larger widget catalog** out of the box (charts, BigText/WaveText/Marquee, Markdown, Image, Calendar, Tree/DirectoryTree, SplitPane, TabbedContent, Collapsible, Slider, RadioGroup, etc.).
8. **Consistent grapheme-cluster-correct editing** across inputs (cursor never splits emoji/combining marks).
9. **Clean separation of concerns**: `Style` = paint, `Constraint`/`Layout` = space, `Block` = box — easier to reason about than one mega-Style, and a deterministic constraint solver (`core/Layout.scala`).
10. **Safe terminal restoration** via a JVM shutdown hook even on SIGTERM/SIGHUP (`runtime/TerminalRunner.scala`).
