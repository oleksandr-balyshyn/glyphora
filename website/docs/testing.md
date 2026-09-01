---
title: Testing
description: Test glyphora widgets, complete apps, keyboard paths, mouse interactions, resize behavior, and motion without a PTY.
---

# Test the interface, headlessly

glyphora's production renderer targets a `Backend`; a terminal is only one
implementation. `HeadlessBackend` records the same rendered buffers and accepts the
same event ADT, so tests can exercise real input and redraw cycles without opening a
PTY or comparing image pixels.

There are three useful levels: render one widget into a buffer, snapshot a whole
frame, or drive a complete `TuiApp`.

Everything on this page ships in the **`tui-test`** artifact
(`io.worxbend::tui-test`), alongside `tui-core`, `tui-terminal`, `tui-widgets`,
`tui-runtime`, `tui-macros` and `tui-dsl`. Add it as a test-only dependency; the Scala
package is `io.worxbend.tui.testsupport`.

## Test a widget buffer

`BufferAssertions` turns buffers into readable strings:

```scala
import io.worxbend.tui.core.Text
import io.worxbend.tui.testsupport.BufferAssertions
import io.worxbend.tui.widgets.Paragraph

val buffer = BufferAssertions.rendered(
  Paragraph(Text.raw("Hello")),
  width = 10,
  height = 2,
)

assert(BufferAssertions.trimmedLines(buffer) == Seq("Hello", ""))
```

- `lines` preserves trailing blanks across the full buffer width;
- `trimmedLines` strips trailing whitespace per row;
- `text` joins trimmed rows with newlines.

All three skip continuation cells occupied by wide graphemes, so assertions match
what a terminal user sees rather than the internal cell encoding.

### Assert style separately

Text snapshots show content and geometry. Inspect cells for style behavior:

```scala
import io.worxbend.tui.core.Color

val cell = buffer.get(0, 0)
assert(cell.symbol == "H")
assert(cell.style.fg.contains(Color.Cyan))
```

Keep style assertions focused on meaningful semantics; asserting every empty cell
makes harmless renderer changes noisy.

### Compare a whole buffer, style included

Two buffers are equal when they cover the same area and hold the same cell — symbol
*and* style — at every position. That makes a whole frame comparable in one assertion,
which the text snapshots above cannot do: they recover symbols only, so a widget that
lost its highlight still matches its expected text.

Write the expected frame as a list of `Line`s and let `Buffer.withLines` size and paint
it. Each span is drawn at its own style, and the buffer comes out as tall as the list
and as wide as the widest line:

```scala
import io.worxbend.tui.core.{Buffer, Line, Style}

val expected = Buffer.withLines(
  Line.raw("Total"),
  Line.styled("   42", Style.Default.bold),
)

assert(actual == expected)
```

When two frames differ, printing either one gives a dump built for exactly this moment
instead of an object hash — the rows as quoted strings, then the position of each
*change* of style (not one line per cell), then the columns hidden under the right half
of a two-column grapheme:

```text
Buffer(area=Rect(0,0,5,2), content=[
  "Total",
  "   42",
], styles=[
  x: 0, y: 0, Style.Default
  x: 3, y: 1, Style(modifiers=Bold)
], hidden=[
])
```

A `Buffer` is mutable, so this equality — and the matching `hashCode` — changes as a
frame is rendered into it. Compare buffers that are finished, and never use one as a
key in a map.

### Walk a frame

`buffer.foreach((x, y, cell) => …)` visits every cell in row-major order, and
`buffer.foreachIn(region)(…)` restricts that to a rectangle, clipped to the buffer. The
callback sees the raw grid, so the second column of a wide grapheme arrives as the
blank it holds; skip it with `buffer.isContinuation(x, y)` when rebuilding what a
terminal displays.

## Snapshot a whole frame

Asserting on individual rows stops scaling once a screen has a border, a header, and
three panes. `GoldenFrames` compares the entire rendered frame against a checked-in
text file — a *golden frame* — so a layout regression shows up as a diff of the
picture rather than as one failed row assertion.

```scala
import io.worxbend.tui.testsupport.GoldenFrames

GoldenFrames.assertMatches("dashboard-first-render", buffer)
```

The name identifies a fixture on the test classpath at
`golden/dashboard-first-render.txt` — that is, `src/test/resources/golden/` in the
module being tested. On a mismatch the failure prints the expected and the actual
frame in full, one after the other. A fixture that does not exist yet fails with an
assertion telling you to record it.

### A golden frame records glyphs, not styling

The fixture is the frame's *text*: each cell's symbol, and nothing else. Colours,
modifiers such as bold and reverse, and hyperlinks are not in it, so a change that
drops every style from a screen still matches its golden file byte for byte. Use a
golden frame for "did anything move?" and keep a handful of `cellAt` / style
assertions beside it for "is it still the right colour?".

### Regenerate a golden frame

You never write these files by hand. Run the tests with `GLYPHORA_GOLDEN_UPDATE`
pointing at the resources directory to write, and every `assertMatches` call writes
its *actual* frame instead of comparing:

```bash
GLYPHORA_GOLDEN_UPDATE=widgets/src/test/resources ./mill widgets.test
```

Nothing is compared during such a run — every `assertMatches` records and returns —
so each one also prints a line on stderr saying so, which is how a job that
accidentally inherited the variable is told apart from a real test run.

Then read the diff before committing it. A golden frame is only as good as the review
of the change to it: regenerating without looking turns a regression into a
checked-in expectation.

### Trailing blank rows do not count

Comparison normalises both sides first: trailing whitespace is stripped from each row
(that is `BufferAssertions.text`), then trailing line terminators are stripped from
the frame as a whole. A widget that leaves the bottom two rows of its area untouched
therefore matches a golden file that ends after the last row with content, and an
editor that adds or removes a final newline cannot break a test. Blank rows *between*
content are significant and are compared like any other row.

## Drive a full app with Pilot

`Pilot` starts the app on a daemon thread, posts input into a `HeadlessBackend`, and
waits until the event queue is idle:

```scala
import io.worxbend.tui.core.Size
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot

val backend = HeadlessBackend(Size(44, 8))
val app = CounterApp()
val pilot = Pilot.start(backend) {
  app.runWith(backend)
}

pilot.waitForIdle()
assert(pilot.screenText.contains("Count: 0"))

pilot.press("+", "+").waitForIdle()

assert(pilot.screenText.contains("Count: 2"))

pilot.press("q")
assert(pilot.awaitTermination())
```

`press` takes **the same key specs `binding` takes** — `"q"`, `"ctrl+s"`,
`"shift+tab"` (or its alias `"backtab"`), `"esc"`, `"f2"`, `"up"`, `"+"`. Both go
through the one parser in `tui-core`, so a test presses the string the application was written against instead
of a hand-translated `KeyEvent` that can drift away from it. Pass several specs to
post several key events in order. A spec that does not parse throws
`IllegalArgumentException` naming the spec and what is wrong with it, exactly as a
malformed `binding` does.

`pressKey(code, modifiers)` remains for the occasional test that wants to build the
`KeyCode`/`KeyModifiers` value directly — for a key with no spec spelling, say — but
`press` is the form to reach for.

`waitForIdle` waits for posted events to be consumed and for the backend to complete
an idle read. It is stronger and less flaky than sleeping for an arbitrary number of
milliseconds.

## Test focus and text entry

```scala
pilot
  .typeText("buy milk")
  .press("enter", "tab", "down")
  .waitForIdle()

assert(app.items.peek.contains("buy milk"))
assert(app.listState.selected.contains(0))
assert(pilot.screenText.contains("· buy milk"))
```

`typeText` posts one key event per Unicode *code point*, which is what the real
input decoder delivers: an emoji outside the Basic Multilingual Plane arrives as
a single `KeyCode.Char`, and a combining accent arrives as its own key event
after the letter it modifies. The number of events is therefore the number of
code points, not `text.length`.

Assert both visible behavior and important state. Visible output proves rendering;
state narrows failures when an event was not routed as expected.

## Test mouse and resize

`Pilot.click(x, y)` posts a down/up pair. `resize(width, height)` changes backend size
and produces the same resize path as a real terminal:

```scala
pilot.click(5, 2).waitForIdle()
assert(app.enabled.peek)

pilot.resize(80, 24).waitForIdle()
assert(pilot.screenLines.size == 24)
```

Scroll and drag have their own methods, so no test has to hand-build a `MouseEvent`:

```scala
pilot.scrollDown(20, 4).waitForIdle()          // one wheel notch; `times` posts several
pilot.scrollLeft(20, 4).waitForIdle()          // horizontal wheel: `scrollLeft`/`scrollRight`
pilot.drag(20, 4, 20, 9).waitForIdle()         // press, move, release
pilot.mouseDown(20, 4).mouseMove(20, 9).mouseUp(20, 9).waitForIdle()
```

`click` is exactly `mouseDown` followed by `mouseUp`. Each one takes an optional
`modifiers` argument for a Ctrl- or Shift-click, and an optional `button` argument
(`MouseButton.Left`, `Middle`, or `Right`) for anything that is not an ordinary left
click. `clickWith(x, y, MouseButton.Right)` is the down/up pair with a chosen button —
the gesture to reach for when testing a context menu, since the built-in click behavior
of a `button` or `checkbox` fires on the left button only.

When an assertion has to wait for something other than the queue going quiet, use
`waitUntil` rather than a sleep — it re-checks the condition, re-raises any failure the
app threw, and fails with the description you gave it if the timeout runs out:

```scala
pilot.waitUntil("the first sample arrives")(app.samples.peek.nonEmpty)
pilot.waitForDraws(3)   // the app repainted at least three times
```

Keep coordinates tied to a deliberate test layout and size. A test that clicks a
magic coordinate in a changing screen is hard to maintain.

## Assert style, not just glyphs

`screenLines` and `screenText` flatten a frame to its characters, which is the right
level for most assertions. When the thing under test *is* the styling — a theme, a
focus ring, a progress bar's fill colour — reach for the frame itself:

```scala
assert(pilot.cellAt(0, 0).style.fg == theme.loading.spinner.fg)
assert(pilot.cellAt(2, 0).style.modifiers.hasAny(Modifiers.Bold))
```

`pilot.lastFrame` is the whole `Buffer` for a sweep across rows; `cellAt(x, y)` is the
single cell. Both fail the test if nothing has been drawn yet, rather than returning
an empty frame that an assertion would pass against for the wrong reason.

The block hands back whatever the runner returned — `runWith` already returns
`Either[RunnerError, Unit]`, so nothing has to be written to discard it. The pilot
watches both ways a run can be wrong:

- a **throwable** escaping the block kills the app thread;
- a **`Left(RunnerError)`** is an orderly exit that still failed — the terminal could
  not be restored, an event handler threw, a queued continuation blew up.

Either one is reported on the *test* thread by the next `waitForIdle`, `screenLines`,
`lastFrame`, `isRunning` or `awaitTermination` call, as an `AssertionError` naming the
cause. Without that, a failed run read as a clean exit and an assertion about the last
frame passed against whatever happened to be on screen. A fixture with nothing to
return — one that blocks on a latch, say — ends its block with `Right(())`.

This matters more than it looks for the animated widgets. An `orbitSpinner` OR-s its
dot masks so the ring never erodes, which means its **glyphs are identical at every
moment** and the entire animation lives in the per-cell style — a glyph-only assertion
would call it static.

## Render a view without a terminal

Everything above runs an app. Sometimes there is nothing to run: you want the pixels a
view *would* produce at a given size, from a plain unit test, or from an application's
own "export what is on screen" command.

```scala
val buffer = Snapshot.render(Size(80, 24))(myView)
println(BufferAssertions.text(buffer))
```

`Snapshot.render` composes one frame into a fresh `Buffer` with no runner, no backend
and no event loop. It runs the same two passes a live frame does, in the same order —
the responsive resolve, so a `responsive` node picks the branch for that size, and the
focus decoration — so what comes back is what an app would paint, not an
approximation. A test in `dsl.test` asserts exactly that against a live `Pilot` run,
which is what stops the two from drifting apart.

Two optional arguments: a `theme` (`Theme.Dark` when you do not say), and a
`focusedKey` naming the element drawn as focused. `None` focuses the first focusable
element, which is where a freshly started app puts focus, and a key that matches
nothing falls back to the same place rather than failing.

`Snapshot.renderInto(buffer, area, theme, focusedKey)(view)` is the same thing into a
buffer you already own, for composing a view into part of a larger frame.

What it is not is interactive or animated: there is no clock and no event loop, and
reactive reads subscribe nothing, so a snapshot is a single still image. Drive
anything time- or input-dependent through `Pilot` instead.

## Capture frames outside tests

Everything above reads frames through `Pilot` and `HeadlessBackend`, which exist for
tests. A shipped application can watch its own frames too, through
`RunnerConfig.onFrame`:

```scala
override def config: RunnerConfig =
  RunnerConfig(onFrame = Some { frame =>
    if frame.count % 100 == 0 then log.debug(frame.text)
  })
```

The callback is handed a `CompletedFrame` — the cells that actually reached the
terminal, the `area` they covered, and the same `count` the `Frame` carried. Its
`buffer` is a snapshot, so keeping it is safe: the runner reuses one buffer between
frames, and handing out the live one would give you a value that changes underneath
you on the next frame. `frame.lines` and `frame.text` render it as plain text, with a
wide grapheme taking one entry rather than one per column — that is the "export what
is on screen" command, or the screen dump attached to a bug report.

Two constraints. The body runs on the render thread, inside the frame it describes,
so keep it short and do not block: no input is being read while it runs. And a body
that throws is not absorbed — the run ends as `RunnerError.Handler`, exactly as a
throwing `view` does. Leaving `onFrame` unset (the default) costs nothing at all: no
snapshot is taken.

## Assert an animated frame

Every animated widget is a pure function of elapsed time, so pin the clock and the
frame becomes reproducible without waiting for wall time to pass:

```scala
AnimationClock.freezeAt(0.millis)
val pilot = Pilot.start(backend) { app.runWith(backend) }.waitForIdle()
assert(pilot.screenLines.head.startsWith("⠋"))

AnimationClock.freezeAt(SpinnerPreset.Dots.frameDuration)
// …the next frame, deterministically
```

`freezeAt` marshals onto the render thread, so it is safe to call from a test thread
even while other suites run beside it.

It is still a **global** act, though. A running app has a clock of its own — one per
render loop — but a pin taken from a test thread lands on the clock every caller
*without* a runner shares, and it also becomes the value any app started afterwards
begins at. So a suite that pins the clock and then asserts on a particular frame can
be overruled by a sibling suite pinning it a millisecond later. That failure only shows
up under parallel execution — it passes on a developer's machine and fails in CI, which
is the worst way to find out.

So prefer the `…At(elapsed)` factories — `spinnerAt`, `orbitSpinnerAt`,
`animatedTextAt`, `indeterminateBarAt` — whenever the animation is a detail of the
element under test rather than the subject of it. Passing the moment in directly reads
no global state and cannot be raced:

```scala
spinnerAt(0.millis).preset(SpinnerPreset.Line).label("busy")   // always frame 0
```

Reserve `freezeAt` for tests whose subject *is* the ambient clock, and serialise those
suites against each other.

To assert that something animates *at all* rather than at a particular moment, watch
the draw count instead of the content:

```scala
val before = backend.drawCount
// …let a few ticks pass
assert(backend.drawCount > before)
```

That is also how to prove the opposite — that a view with no animation on it is *not*
being repainted by the ticks.

## Test async completion

Inject a fake client and wait for the app to go idle after the callback updates its
signal:

```scala
val client = new WeatherClient:
  def fetch(city: String) = Right(
    WeatherReport(city, "UA", 24.0, 50.0, 8.0, true, 0)
  )

val app = WeatherApp(client)
val pilot = start(app)

pilot.typeText("Kyiv").press("enter").waitForIdle()
assert(pilot.screenText.contains("24.0°C"))
```

Dependency injection keeps the terminal test deterministic while still exercising
the background-to-render-thread handoff.

## Test effects deterministically

Effects accept elapsed time directly. Render a known buffer, apply at meaningful
boundaries, and assert the resulting cells:

```scala
val effect = Effect.sweepIn(1.second, Easing.Linear)

effect.process(0.millis, buffer, buffer.area)
// nothing revealed

effect.process(500.millis, buffer, buffer.area)
// half the columns revealed

effect.process(1.second, buffer, buffer.area)
// complete frame revealed
```

Use fixed seeds for `coalesce` and `dissolve`.

## Run the repository checks

```bash
./mill __.compile

# tests, one module at a time — the sequence CI runs
./mill core.test
./mill terminal.test
./mill widgets.test
./mill runtime.test
./mill macros.test
./mill dsl.test
./mill test-support.test

./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources

# Manual app and render-loop check
./mill examples.showcase.run
./mill widgets.test.runMain io.worxbend.tui.widgets.RenderLoopBench
```

One command per module rather than `./mill __.test`, because a single combined step
reports only "still running": a module that hangs cannot be told apart from one that
is slow. CI runs exactly this sequence, each step with its own timeout.

GitHub Actions runs the same commands in granular jobs: discipline greps that need no
JDK, a formatting check, one compile of the library, then the library tests, one job per
example, and a GraalVM native image for every example. Everything after the compile
reuses its output, so the library is built once per run.

## What to cover before shipping

- first render at a normal and small terminal size;
- every documented keyboard command;
- focus order in both directions;
- mouse alternatives and keyboard equivalents;
- loading, empty, error, and success states;
- modal focus isolation and closing paths;
- Unicode strings relevant to your users;
- resize while scrolled or focused;
- clean quit after success and failure.

Browse real end-to-end suites under
[`examples/*/src/test`](https://github.com/oleksandr-balyshyn/glyphora/tree/main/examples).
