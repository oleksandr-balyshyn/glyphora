---
title: Getting started
description: Install glyphora, run a reactive counter, and understand each piece of your first terminal app.
---

# Build your first screen

In this guide you will create a small reactive counter, run it in a real terminal,
and make two changes that exercise state, layout, styling, and keyboard commands.

> **You need:** JDK 21 or newer, Scala 3, and either Mill or sbt. Run the final app
> from a terminal—not an IDE output panel—because raw input needs a controlling TTY.

## 1. Add glyphora

The normal application dependency is `tui-dsl`. It brings in the core types,
widgets, terminal backend, and runtime transitively.

### Mill

```scala title="build.mill"
package build

import mill.*, scalalib.*

object app extends ScalaModule:
  def scalaVersion = "3.7.1"
  def mvnDeps = Seq(mvn"io.worxbend::tui-dsl:0.10.0")
```

Put application sources under `app/src/`, then run them with `mill app.run` (or
`./mill app.run` when your project checks in the Mill launcher).

### sbt

```scala title="build.sbt"
scalaVersion := "3.7.1"

libraryDependencies += "io.worxbend" %% "tui-dsl" % "0.10.0"
```

Put application sources under `src/main/scala/`, then use `sbt run`.

<details>
<summary>Why depend on tui-dsl instead of every module?</summary>

`tui-dsl` is the batteries-included application layer. It exports `TuiApp`, element
factories, style and layout extensions, reactive state, widgets, terminal events,
and runtime effects. Lower-level module dependencies are useful only when you are
embedding glyphora or writing a widget library; see [Architecture](./architecture).

</details>

## 2. Create the app

```scala title="Counter.scala"
import io.worxbend.tui.dsl.*

object Counter extends TuiApp:
  private val count = Signal(0)

  override def bindings = KeyBindings(
    binding("+", "increment")(count.update(_ + 1)),
    binding("-", "decrement")(count.update(_ - 1)),
    binding("q", "quit")(quit()),
  )

  def view(using ReactiveScope): Element =
    scaffold(statusBar = Some(statusBar(bindings))) {
      centered(34, 7) {
        panel("Counter")(
          text(s"Count: ${count.get}").bold.color(Color.Cyan),
          spacer,
          text("Change state; the view follows.").dim,
        ).rounded
      }
    }

  def main(args: Array[String]): Unit =
    run().foreach(_ => ())
```

Run it in a terminal:

```bash
# Mill
./mill app.run

# sbt
sbt run
```

Press `+` and `-` to change the value; press `q` to exit. The runner enters raw mode,
uses the alternate screen, hides the cursor, and restores the terminal when it
finishes.

## 3. Understand the moving parts

| Code | Responsibility |
|---|---|
| `extends TuiApp` | owns the event/render lifecycle and terminal-safe cleanup |
| `Signal(0)` | stores mutable state and invalidates views that read it |
| `bindings` | declares global commands once for dispatch, help, palette, and status hints |
| `view(using ReactiveScope)` | tracks signal reads while returning an element tree |
| `scaffold(...)` | composes optional top bar, sidebar, content, and status bar |
| `centered(34, 7)` | gives the panel a fixed area centered in available space |
| `.rounded`, `.bold`, `.color(...)` | type-safe element decoration and style extensions |
| `run()` | opens the backend and blocks until `quit()` or an unconsumed `Ctrl+C` |

The key detail is `count.get`. That tracked read connects this view to `count`.
Calling `count.update` marks it stale; the runtime schedules a redraw and rebuilds
the view. There is no manual refresh call.

## 4. Make it yours

### Add a derived value

Use `Computed` for a value that depends on one or more signals:

```scala
private val count = Signal(0)
private val parity = Computed(if count.get % 2 == 0 then "even" else "odd")

// inside view
text(s"${count.get} is ${parity.get}")
```

### Add another region

Rows and columns divide their available area using constraints:

```scala
row(
  panel("Value")(text(count.get.toString)).percent(40),
  panel("Parity")(text(parity.get)).fill,
).length(5)
```

Here the row is five cells high. Its first child receives 40% of the width and the
second consumes the remainder.

### Handle a key locally

Global commands belong in `bindings`. Interaction that belongs to one element can
stay beside that element:

```scala
panel("Counter")(text(count.get.toString))
  .onKey(Key.char('r')) { count.set(0) }
```

Local handlers run before global bindings. A low-level `.onKeyEvent` handler returns
`true` to stop bubbling or `false` to let the parent/global binding see the event.

## 5. Know the first two traps

### “UnsupportedTerminal” at startup

The process does not have a controlling TTY. Run it from a normal terminal window;
for CI or unit tests, inject `HeadlessBackend` instead. See [Testing](./testing).

### Signal write rejected off the render thread

Key handlers, mouse handlers, and `onTick` already run on the render thread.
Callbacks from `Future`, an HTTP client, or another executor must hop back before
changing a signal:

```scala
Future(loadData()).foreach { result =>
  RenderThread.runOnRenderThread {
    data.set(result)
  }
}
```

The complete pattern is in [Async work & timers](./async-and-timers).

## Where to go next

- [Layout & style](./layout-and-style) — rows, columns, constraints, borders, and
  reusable visual language.
- [State & signals](./state-and-signals) — tracked reads, computed values, and
  render-thread rules.
- [Widget catalog](./widgets) — choose the right building blocks.
- [The app shell](./app-shell) — themes, sidebars, screens, toasts, and the palette.
- [Examples](./examples) — run seven complete applications from the repository.
