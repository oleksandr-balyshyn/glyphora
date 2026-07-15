---
title: Getting started
---

# Getting started

## Add the dependency

glyphora publishes to Maven Central under `io.worxbend`. With [Mill](https://mill-build.org):

```scala
// build.mill
def mvnDeps = Seq(mvn"io.worxbend::tui-dsl:0.10.0")
```

With sbt:

```scala
libraryDependencies += "io.worxbend" %% "tui-dsl" % "0.10.0"
```

`tui-dsl` pulls in `tui-core`, `tui-widgets`, and `tui-runtime` transitively — one
dependency is enough for a normal app. See [Architecture](./architecture) if you only
need a lower tier (for example, `tui-widgets` alone with no signals/DSL layer).

## Your first app

```scala
import io.worxbend.tui.dsl.*

object Hello extends TuiApp:
  val count = Signal(0)

  override def bindings = KeyBindings(
    binding("+", "increment")(count.update(_ + 1)),
    binding("q", "quit")(quit()),
  )

  def view(using ReactiveScope): Element =
    scaffold(statusBar = Some(statusBar(bindings))) {
      centered(30, 5) {
        panel("Hello")(
          text(s"count: ${count.get}").bold.color(Color.Cyan),
          text("press + to bump it").dim,
        ).rounded
      }
    }

  def main(args: Array[String]): Unit = run().foreach(_ => ())
```

Run it — `run()` takes over the controlling terminal, sets raw mode, and blocks until
`quit()` fires or `Ctrl+C` is pressed unconsumed.

One import (`io.worxbend.tui.dsl.*`) gives you every element factory, the
styling/layout extensions, and the core vocabulary (`Signal`, `Color`, `KeyEvent`, …).

## Where to go next

- [Architecture](./architecture) — how the six modules fit together.
- [State & signals](./state-and-signals) — the reactive model in depth.
- [The app shell](./app-shell) — `scaffold`, themes, screens, the command palette.
- [Widget catalog](./widgets) — everything you can render.
- [Cookbook](./cookbook) — short recipes for common shapes.
- [Examples](./examples) — five complete runnable apps.
