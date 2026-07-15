---
title: Testing
---

# Testing

Every widget, screen, and app in this repo is tested headlessly — no PTY, fully
CI-friendly. All 1,500+ tests in the repository run this way.

## Render-to-Buffer tests

Widgets render into a `Buffer`; `BufferAssertions` (from `test-support`) turns that
into readable assertions:

```scala
import io.worxbend.tui.test.BufferAssertions.*

val buffer = rendered(Paragraph(Text.raw("Hello")), width = 10, height = 1)
assert(buffer.trimmedLines == Seq("Hello"))
```

`lines` / `trimmedLines` / `text` skip wide-grapheme continuation cells, so expected
strings read exactly like what a terminal would show.

## End-to-end with Pilot

`Pilot` drives a full event/render cycle against a `HeadlessBackend` — starting the
app on a background thread and feeding it synthetic input:

```scala
val backend = HeadlessBackend(Size(60, 16))
val pilot   = Pilot.start(backend) { app.runWith(backend) }

pilot.typeText("deploy").pressKey(KeyCode.Enter).waitForIdle()
assert(pilot.screenText.contains("deployed ✓"))
```

Available on `Pilot`: `pressKey`, `typeText`, `click`, `resize`, `waitForIdle`,
`screenLines` / `screenText`, `awaitTermination`.

## Running the suite

```bash
./mill __.compile                                   # build everything
./mill __.test                                      # ~1.5k tests, headless
./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources
./mill widgets.test.runMain io.worxbend.tui.widgets.RenderLoopBench      # fps check
./mill examples.showcase.test.runMain \
      io.worxbend.tui.examples.showcase.ScreenshotMain 70 17             # README shot
./mill examples.showcase.run                        # drive it for real
```

## Property-based tests

`tui-core` also ships ScalaCheck property tests (via `scalacheck-1-18`) over
`CharWidth` and layout math — the kind of grapheme-cluster/combining-mark edge cases
that are easy to miss with example-based tests alone.

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs, on every push and PR:

- a reflection-discipline grep (no `java.lang.reflect`/`Class.forName` in main sources)
- a `CharWidth`-discipline grep (no `.substring` for layout math outside `CharWidth`)
- `scalafmt` format check
- full compile + test on Linux, best-effort on Windows
- native-image builds for every example, run headlessly to confirm a clean
  "no TTY" exit
