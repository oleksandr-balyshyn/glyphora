---
title: Unicode & accessibility
description: Build terminal interfaces that measure Unicode correctly and communicate focus, status, and errors without relying on color.
---

# Unicode & accessibility

A terminal cell is not a Java `Char`, a Unicode code point, or always one visible
character. glyphora treats display width and grapheme clusters as infrastructure so
layout, wrapping, editing, and cursor movement agree.

Accessibility also starts in the model: predictable focus, explicit labels, and
status text that does not depend on color alone.

## Measure what the terminal displays

```scala
import io.worxbend.tui.core.CharWidth

CharWidth.of("hello")       // 5
CharWidth.of("界")          // usually 2 terminal cells
CharWidth.of("e\u0301")    // 1: e + combining acute accent
CharWidth.of("👨‍👩‍👧‍👦")   // one grapheme cluster, terminal-dependent width
```

Use `CharWidth` anywhere custom code clips, aligns, wraps, pads, or positions a
cursor. `String.length` counts UTF-16 code units and will eventually misalign a UI.

Built-in widgets already route their width calculations through generated Unicode
Character Database tables. `TextInput` and `TextArea` edit by grapheme cluster, so
Backspace does not split combining sequences or emoji families.

## Test the hard strings

Include a small width corpus in custom widget tests:

```scala
val labels = Seq(
  "Kyiv",
  "東京",
  "naïve",
  "e\u0301",
  "🇺🇦",
  "👩🏽‍💻",
)

labels.foreach { label =>
  assert(CharWidth.of(label) >= 1)
}
```

For exact rendering expectations, use `BufferAssertions`; its `lines`,
`trimmedLines`, and `text` helpers skip wide-character continuation cells.

## Make focus predictable

Interactive elements enter a tree-derived tab order. `Tab` moves forward and
`Shift+Tab` moves backward. Mouse clicks focus the hit element before built-in
interaction runs.

When the tree can change shape, give important controls stable identity:

```scala
column(
  input(query, placeholder = "search").key("search"),
  if advanced.get then input(pattern).key("pattern") else spacer(1),
  button("Apply", applyFilter).key("apply"),
)
```

Without keys, focus is positional and can jump when an element appears above the
current control.

## Never use color alone

Pair tone with a symbol and clear language:

```scala
def statusLine(state: State): Element = state match
  case State.Ready  => text("✓ Ready").color(Color.Green)
  case State.Syncing => text("… Synchronizing").color(Color.Yellow)
  case State.Failed(message) => text(s"Error: $message").color(Color.Red)
```

`notice` and `badge` do this for you: `NoticeLevel` pairs each severity with a glyph
(`✔ • ▲ ✖`) and a word (`OK INFO WARN FAIL`), so a message survives a monochrome
terminal. The glyphs are geometric rather than emoji deliberately — emoji are two
columns wide and inconsistently rendered, which makes a stacked column of notices
ragged.

This remains understandable in monochrome terminals and high-contrast modes.
`Theme.HighContrast` is available when the application needs stronger separation.

## Offer a way to turn motion down

Persistent animation is the accessibility hazard most terminal toolkits ignore. When
an app animates while it waits, give the user a setting, and route it to the quieter
member of each family rather than to nothing at all:

| Instead of | Reach for | Why |
|---|---|---|
| `indeterminateBar()` | `.motion(IndeterminateMotion.Pulse)` | brightens in place; no travel to track across the row |
| `spinnerGrid()` | `.uniform` | every slot in lockstep, so the block pulses rather than waves |
| `spinner()` | `.preset(SpinnerPreset.Ellipsis)` | four slow frames of `...`, closer to punctuation than motion |
| any animation | a static caption | when there is nothing useful to say about progress, say it once |

Two more properties worth knowing before choosing an animation:

- An `orbitSpinner` animates entirely through per-cell **style**; its glyphs are the
  same at every moment. On a terminal that renders neither colour nor dim it is a
  still ring, so prefer `spinnerGrid` or `spinner` there.
- `SpinnerPreset.AsciiPresets` and `ProgressStyle.Ascii`/`Arrow` need nothing beyond
  ASCII, and `orbitSpinner().markers("*")` draws the same figure with one glyph per
  cell. Those are the floors for an unknown terminal, a CI log, or a screen reader.

## Prefer explicit forms and commands

- Use `Form.accessible(state)` when field position, checkbox state, and errors should
  be spelled out; see [Forms & validation](./forms-and-validation).
- Give every global key binding a short, action-oriented description. The same label
  appears in the status bar, help overlay, and command palette.
- Do not require mouse interaction. Every built-in control has a keyboard path.
- Keep status messages visible long enough to read, and allow important output to be
  revisited outside a short-lived toast.
- Use familiar keys (`Tab`, arrows, `Enter`, `Esc`) before inventing chord-heavy
  navigation.

## Terminal capability reality

Terminals vary in color depth, glyph fallback, hyperlink support, and the width they
assign to newer emoji. glyphora can make its calculations internally consistent,
but it cannot install fonts or change an emulator's width policy.

When a glyph is decorative, include a text fallback. When exact alignment is
mission-critical, prefer stable box-drawing and text symbols over very new emoji.
Test at least one 16-color environment and one true-color terminal before release.

Continue with [Mouse & focus](./mouse) for event routing or [Testing](./testing) for
headless interaction checks.
