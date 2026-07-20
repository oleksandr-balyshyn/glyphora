# glyphora — competitive gap analysis

A principal-engineer review of glyphora against the JVM/Scala and reference (Rust/Go)
TUI landscape, with a prioritized roadmap. Comparators studied: **terminus**
(Creative Scala), **tamboui** (Java), **tui-scala** (ratatui port), **Lanterna** &
**JLine 3** (Java incumbents), and the reference designs **ratatui** (Rust) and
**Bubble Tea / Lipgloss / Bubbles** (Go).

## TL;DR

glyphora is, today, **the most capable TUI toolkit on the JVM** and the most
feature-complete in the Scala ecosystem. It already ships things *no other JVM/Scala
library has*: the kitty keyboard protocol, bracketed paste, DEC-2026 synchronized
output, OSC 8 hyperlinks, and — crucially — real **grapheme-cluster shaping** (ZWJ
emoji families, flags, combining marks), which terminus explicitly does *not* do.
It pairs that with a documented signals runtime, a 40+ widget catalog, a constraint
layout solver, mouse support (terminus has none), an animation/effects engine,
native-image support with zero reflection, and a headless test harness.

The gaps are therefore not "catch up to the field" — they are a **short, specific
list of capabilities the reference libraries have and glyphora doesn't yet.** This
document enumerates them and proposes an order of attack.

## Where glyphora already leads

| Capability | glyphora | terminus | tamboui | tui-scala | Lanterna |
|---|:---:|:---:|:---:|:---:|:---:|
| Kitty keyboard protocol | ✅ | ❌ | ❌ | ❌ | ❌ |
| Bracketed paste | ✅ | ❌ | ❌ | ❌ | ❌ |
| Synchronized output (mode 2026) | ✅ | ❌ | ❌ | ❌ | ❌ |
| Focus reporting (mode 1004) | ✅ | ❌ | ❌ | ❌ | ❌ |
| OSC 8 hyperlinks | ✅ | ❌ | ❌ | ❌ | ❌ |
| Grapheme-cluster shaping | ✅ | ❌ (per-codepoint) | ? | ? | ❌ |
| Mouse (click/drag/wheel) | ✅ | ❌ | ✅ | ❌ (none) | ✅ |
| Signals/fine-grained reactivity | ✅ | ✅ (undocumented) | ❌ | ❌ | ❌ |
| Widget count | 40+ | 7 | ~standard set | ~13 | rich |
| Native-image, zero reflection | ✅ | (JS/Native cross) | ✅ | ✅ (JNI) | ❌ |
| Headless test harness + golden frames | ✅ | StringBuilder term | ❌ | ❌ | ❌ |

## The gaps (prioritized)

### ✅ Shipped in this pass (were gaps, now closed)

- **`NO_COLOR` / `CLICOLOR_FORCE` support.** glyphora previously ignored the
  [no-color.org](https://no-color.org) standard that Lipgloss and most modern CLIs
  honor. `ColorDepth` now has a `NoColor` case; `detect()` resolves `NO_COLOR`
  (non-empty ⇒ off) with `CLICOLOR_FORCE` (non-zero ⇒ force on) precedence; the SGR
  encoder drops color codes but keeps text attributes. `JLine3Backend.create` now
  takes an optional `colorDepth` override.
- **OSC 52 clipboard write.** *No JVM/Scala TUI library had this.* Added
  `AnsiSequences.clipboardCopy`, `Backend.copyToClipboard` (default no-op, real in
  JLine3, recorded in `HeadlessBackend`), `RunnerHandle.copyToClipboard`, and a
  `TuiApp.copyToClipboard` helper. Demonstrated in `examples/showcase` (`ctrl+y`).

### P1 — highest leverage, still open

1. **Terminal cursor placement (`Frame.setCursor`).** Both ratatui (`frame.set_cursor`)
   and terminus (`Cursor` effect) let a focused text input position the *real*
   terminal cursor; glyphora hides the cursor and paints a software block only. The
   real cursor matters for IME composition, screen readers, and native feel. Touches
   `Backend` (add `setCursor(Option[Position])` + show/hide coordination), the runner
   draw path, `Frame`, and the DSL text-input/textarea render so focus drives it.
   Medium size, high value; the single most-cited missing primitive.

2. **Inline / viewport rendering.** glyphora always takes the alternate screen.
   ratatui's `Viewport::Inline(n)` + `insert_before` renders a fixed-height live UI
   inside normal scrollback — the right model for CLI-embedded progress UIs, wizards,
   and REPL widgets. Needs a non-alt-screen runner mode and scrollback-preserving
   draw. Distinct, additive runner path.

3. **First-class async / command effects.** Today background work is hand-rolled
   (`examples/weather` uses `Future` + `RenderThread.runOnRenderThread`), and a
   background signal update waits up to the poll timeout (≤100 ms, or one tick)
   because nothing interrupts the blocking `readEvent`. Bubble Tea's `Cmd` and
   terminus's Cats Effect runner both solve this. Proposal: (a) a `Backend` wakeup
   (self-pipe / interruptible read) so `runOnRenderThread` repaints immediately;
   (b) a small `TuiApp.spawn[A](work: => A)(onResult: A => Unit)` that runs off-thread
   and marshals the result back. The style guide already mandates Ox — an
   Ox-scoped runner is the idiomatic target.

### P2 — completeness

4. **Flex leftover-distribution** in the layout solver. glyphora has
   `Length/Percentage/Ratio/Min/Max/Fill(weight)` but not ratatui's `Flex`
   (`SpaceBetween/SpaceAround/SpaceEvenly/Center/Start/End`) for positioning slack
   across the whole axis. Additive to `Layout`.

5. **Adaptive color** (light/dark background detection, à la Lipgloss `AdaptiveColor`).
   Requires an OSC 11 background-color query + response parse, then a
   `Color.adaptive(light, dark)` resolved at render time.

6. **More OSC/terminal niceties:** OSC 2 window title, OSC 9/desktop notifications,
   cursor-style control (bar/underline/block), OSC 4/10/11 color queries. Each is a
   small `AnsiSequences` + `Backend` addition.

### P3 — strategic / large

7. **Image protocols (sixel / kitty graphics / iTerm2).** Currently explicitly out of
   scope (half-block `Image` only); ratatui pushes these to `ratatui-image`. A
   `tui-image` companion module keeps native-image and the core clean.

8. **Accessibility / screen-reader mode.** The research flagged this as the *weakest
   area across every TUI library* and therefore the clearest differentiation
   opportunity: a semantic, non-visual output mode (announce focus/labels/values as
   plain lines) driven off the same element tree. `NO_COLOR` (shipped) is the first
   step; real cursor placement (P1.1) is the second.

## Design notes worth borrowing (from the study)

- **terminus's capability phase-split enforced by the type system** — `React`
  (create state) is only in scope during setup; `Observe` (reactive read) only during
  render. The compiler stops you creating a signal in a render pass, with a bespoke
  `@implicitNotFound`. glyphora's `ReactiveScope` already encodes tracking as a
  capability; a similar "you may not create a long-lived `Computed` inside `view`"
  guard would harden a known footgun (documented in `Reactive.scala`).
- **terminus's `Buffer.view(rect)` clipping sub-views** — children render at their own
  origin and a view translates+clips writes. glyphora clips in `Buffer` directly; a
  `view` primitive would simplify offscreen widgets (ScrollView, overlays).
- **Bubble Tea's `Cmd`/`Msg`** — the cleanest published model for async effects; worth
  studying for the P1.3 API even though glyphora is signals- not TEA-based.

## Positioning

glyphora's moat is the **modern terminal-protocol suite + Scala 3 ergonomics + a real
widget catalog + testability**, none of which the JVM competition combines. The
roadmap above defends that moat: cursor placement and inline viewport remove the last
"ratatui has it, we don't" primitives; async ergonomics removes the sharpest usability
edge; and accessibility is open territory no competitor occupies.
