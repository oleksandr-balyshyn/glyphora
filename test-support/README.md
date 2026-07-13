# test-support

Shared test infrastructure (not published; consumed by other modules' `test` submodules only).

- **`Pilot`** — the headless end-to-end driver: starts an app over a `HeadlessBackend`
  on a background thread, then
  `pressKey` / `typeText` / `click` / `resize` / `waitForIdle` / `screenLines` /
  `awaitTermination` from the test.
- **`BufferAssertions`** — render-to-`Buffer` helpers: `rendered(widget, w, h)`,
  `lines` / `trimmedLines` / `text` (wide-grapheme continuation cells skipped, so
  expected strings read like the terminal).

```scala
val backend = HeadlessBackend(Size(40, 10))
val pilot = Pilot.start(backend) { val _ = app.runWith(backend) }
pilot.typeText("hi").pressKey(KeyCode.Enter).waitForIdle()
assert(pilot.screenText.contains("hi"))
```
