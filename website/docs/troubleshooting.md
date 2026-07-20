---
title: Troubleshooting
description: Diagnose terminal startup, stale views, focus, input, animation, Unicode, native-image, and cleanup problems.
---

# Troubleshooting

Start here when an app does not render, input is missing, or native behavior differs
from the JVM. Work from the first relevant symptom; most problems come from TTY
availability, state lifetime, tracked reads, or event consumption.

## UnsupportedTerminal at startup

glyphora needs a controlling TTY. An IDE output panel, redirected pipe, or headless
CI process intentionally returns `UnsupportedTerminal` instead of entering raw mode.
Run the app in a terminal:

```bash
./mill examples.showcase.run
```

For CI, inject `HeadlessBackend`; see [Testing](./testing).

## The view does not update

Check these in order:

1. The view must read reactive state with `.get`, not `.peek`.
2. A signal must live outside `view`; recreating it resets the value every pass.
3. A signal only notifies when the new value is not equal (`!=`) to the old one.
4. Replace immutable collections instead of mutating one in place.
5. Third-party callbacks must write on the render thread.

```scala
private val rows = Signal(Vector.empty[Row])

def view(using ReactiveScope) = renderRows(rows.get)
def append(row: Row) = rows.update(_ :+ row)
```

## Input edits, then resets

`TextInputState`, `TextAreaState`, `ListState`, and other interactive state objects
must be created once on the app or owning screen:

```scala
private val inputState = TextInputState()

def view(using ReactiveScope) = input(inputState)
```

Creating `TextInputState()` in `view` replaces it with an empty editor on the next
redraw.

## Input or mouse events do not arrive

- Confirm the element is focusable and press `Tab` to move focus.
- Return `false` from a custom handler when the event should bubble.
- A user handler that always returns `true` can block built-in input/list behavior.
- Keep global behavior in `KeyBindings`; focused handlers run before them.
- Do not parse escape sequences in the app; `JLine3Backend` owns decoding.
- `TuiApp` enables backend interaction modes. A custom runner owns that lifecycle.

See [Mouse & focus](./mouse) for the exact routing order.

## Focus jumps after conditional rendering

Focus is positional unless an element has a stable key. Add `.key("unique-name")` to
interactive controls that can move when branches appear or disappear. Keys must be
unique among focusable elements in the current tree.

## Toasts never disappear or effects do not animate

Both advance on ticks. Configure a cadence:

```scala
import io.worxbend.tui.runtime.RunnerConfig
import scala.concurrent.duration.*

override def config = RunnerConfig(tickRate = Some(100.millis))
```

A splash supplies ticks automatically when the app has none; normal toasts and
`runEffect` calls do not.

## Text alignment breaks with emoji or CJK

Use `CharWidth` for custom width calculations. Java/Scala string length counts
UTF-16 code units, not terminal cells. Built-in widgets already use `CharWidth` for
clipping, wrapping, and cursor placement.

If built-in widgets agree but the emulator still looks wrong, check the emulator's
ambiguous/emoji width policy and font fallback. An application cannot force an
emulator to assign a particular width to a new emoji.

## A Signal update throws off the render thread

Key, mouse, binding, and tick handlers are already safe. Futures, HTTP clients, and
other callbacks must hop back before writing.

Prefer the structured helper:

```scala
Async.runCatching(fetch()) {
  case Right(value) => data.set(value)
  case Left(error)  => failure.set(Some(error.getMessage))
}
```

Its completion already runs on the render thread. For an externally owned callback,
use `RenderThread.runOnRenderThread`.

## A panel or child looks empty

Borders consume space. A panel needs at least three rows to leave one inner row. In
a `row`, child constraints allocate width; in a `column`, they allocate height.

Temporarily replace nested content with labeled `text` elements and inspect each
container's `.length`, `.percent`, and `.fill` constraints from the outside in.

## DataTable opens the wrong row

`DataTableState.selected` indexes the filtered and sorted view, not the original row
sequence. Resolve the selection with:

```scala
state.selected.flatMap(table.visibleRows(state).lift)
```

## Native-image compilation fails

First confirm the JVM build and tests:

```bash
./mill app.compile
./mill app.test
./mill app.nativeImage
```

Keep `--no-fallback` enabled. Inspect your own dependencies for runtime reflection,
dynamic class loading, resources, JNI, and proxies. glyphora's derivation APIs use
compile-time Scala 3 macros and require no reflection config. See
[Native binaries](./native-image).

## The screen is corrupted after a crash

The normal runner restores cooked mode, cursor visibility, mouse capture, and the
alternate screen in a `finally` path. If the process is killed ungracefully, run
`reset` in the shell.

When launching an external interactive program, use `TuiApp.suspend { ... }` so the
terminal is deliberately handed over and restored.

## Debug output destroys the live UI

Ordinary `println` writes into the terminal glyphora is repainting. Use a `Log`
widget, write to a file, or call `printAbove(...)` from a handler to add durable lines
to scrollback without corrupting the frame.

## Still stuck?

Search [existing issues](https://github.com/oleksandr-balyshyn/glyphora/issues), then
open a minimal reproduction with:

- glyphora, Scala, JVM, and OS versions;
- terminal emulator and shell;
- smallest terminal size that reproduces it;
- whether `HeadlessBackend` reproduces it;
- relevant stack trace and key/mouse sequence;
- JVM/native-image difference, if any.
