# tui-test

The headless test harness, published as `io.worxbend::tui-test` for use as a **test-only**
dependency. The repository directory is `test-support/`; the Scala package is
`io.worxbend.tui.testsupport`.

- **`Pilot`** — the headless end-to-end driver: starts an app over a `HeadlessBackend`
  on a background thread, then
  `press` / `pressKey` / `typeText` / `click` / `mouseDown` / `mouseUp` / `mouseMove` / `drag` /
  `scrollUp` / `scrollDown` / `resize` / `waitForIdle` / `waitUntil` / `waitForDraws` /
  `screenLines` / `awaitTermination` from the test.
- **`BufferAssertions`** — render-to-`Buffer` helpers: `rendered(widget, w, h)`,
  `rendered(statefulWidget, state, w, h)`, `renderedInto(widget, area, w, h)`,
  `lines` / `trimmedLines` / `text` / `line` (wide-grapheme continuation cells skipped, so
  expected strings read like the terminal).
- **`GoldenFrames`** — whole-frame snapshots: `assertMatches(name, buffer)` compares against
  `golden/<name>.txt` on the test classpath, and `GLYPHORA_GOLDEN_UPDATE=<dir>` records
  instead of comparing.

```scala
val backend = HeadlessBackend(Size(40, 10))
val pilot = Pilot.start(backend) { app.runWith(backend) }
pilot.typeText("hi").press("enter").waitForIdle()
assert(pilot.screenText.contains("hi"))
```
