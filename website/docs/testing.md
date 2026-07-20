---
title: Testing
description: Test glyphora widgets, complete apps, keyboard paths, mouse interactions, resize behavior, and motion without a PTY.
---

# Test the interface, headlessly

glyphora's production renderer targets a `Backend`; a terminal is only one
implementation. `HeadlessBackend` records the same rendered buffers and accepts the
same event ADT, so tests can exercise real input and redraw cycles without opening a
PTY or comparing image pixels.

There are two useful levels: render one widget into a buffer, or drive a complete
`TuiApp`.

## Test a widget buffer

Inside this repository, `BufferAssertions` turns buffers into readable strings:

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

## Drive a full app with Pilot

`Pilot` starts the app on a daemon thread, posts input into a `HeadlessBackend`, and
waits until the event queue is idle:

```scala
import io.worxbend.tui.core.{KeyCode, Size}
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot

val backend = HeadlessBackend(Size(44, 8))
val app = CounterApp()
val pilot = Pilot.start(backend) {
  val _ = app.runWith(backend)
}

pilot.waitForIdle()
assert(pilot.screenText.contains("Count: 0"))

pilot
  .pressKey(KeyCode.Char('+'))
  .pressKey(KeyCode.Char('+'))
  .waitForIdle()

assert(pilot.screenText.contains("Count: 2"))

pilot.pressKey(KeyCode.Char('q'))
assert(pilot.awaitTermination())
```

`waitForIdle` waits for posted events to be consumed and for the backend to complete
an idle read. It is stronger and less flaky than sleeping for an arbitrary number of
milliseconds.

> **0.10.0 packaging note:** `Pilot` and `BufferAssertions` currently live in this
> repository's internal `test-support` module and are not published to Maven Central.
> They document and test the intended public test API; downstream projects can drive
> `HeadlessBackend` directly until that artifact is published.

## Test focus and text entry

```scala
pilot
  .typeText("buy milk")
  .pressKey(KeyCode.Enter)
  .pressKey(KeyCode.Tab)
  .pressKey(KeyCode.Down)
  .waitForIdle()

assert(app.items.peek.contains("buy milk"))
assert(app.listState.selected.contains(0))
assert(pilot.screenText.contains("· buy milk"))
```

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

Post specific kinds through the backend when you need scroll or drag:

```scala
import io.worxbend.tui.core.*

backend.postEvent(Event.Mouse(
  MouseEvent(20, 4, MouseEventKind.Drag, KeyModifiers.None)
))
pilot.waitForIdle()
```

Keep coordinates tied to a deliberate test layout and size. A test that clicks a
magic coordinate in a changing screen is hard to maintain.

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

pilot.typeText("Kyiv").pressKey(KeyCode.Enter).waitForIdle()
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
./mill __.test
./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources

# Manual app and render-loop check
./mill examples.showcase.run
./mill widgets.test.runMain io.worxbend.tui.widgets.RenderLoopBench
```

GitHub Actions also checks reflection discipline, Unicode width discipline, Linux
tests, best-effort Windows compatibility, and GraalVM native images for every
example.

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
