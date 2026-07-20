<div align="center">

<img src="docs/assets/banner.svg" alt="glyphora — terminal UI, written like Scala" width="100%"/>

### Terminal UI, written like Scala.

Reactive state · 40+ widgets · keyboard & mouse · composable motion · headless tests · GraalVM native-image

[![CI](https://github.com/oleksandr-balyshyn/glyphora/actions/workflows/ci.yml/badge.svg)](https://github.com/oleksandr-balyshyn/glyphora/actions/workflows/ci.yml)
[![Docs](https://github.com/oleksandr-balyshyn/glyphora/actions/workflows/docs.yml/badge.svg)](https://oleksandr-balyshyn.github.io/glyphora/)
[![Release](https://img.shields.io/github/v/tag/oleksandr-balyshyn/glyphora?label=release&color=ef3340)](https://github.com/oleksandr-balyshyn/glyphora/tags)
[![Scala](https://img.shields.io/badge/Scala-3.7-DC322F?logo=scala&logoColor=white)](https://scala-lang.org)
[![GraalVM](https://img.shields.io/badge/native--image-ready-34d399?logo=oracle&logoColor=white)](https://www.graalvm.org/latest/reference-manual/native-image/)
[![License](https://img.shields.io/github/license/oleksandr-balyshyn/glyphora?color=fbbf24)](LICENSE)

**[Read the guide](https://oleksandr-balyshyn.github.io/glyphora/)** ·
**[Open the cookbook](website/docs/cookbook.md)** ·
**[Browse Scaladoc](https://oleksandr-balyshyn.github.io/glyphora/api/)** ·
**[Run the examples](examples/README.md)**

</div>

---

## ✦ Why glyphora

<p align="center"><img src="docs/assets/capabilities.svg" alt="Reactive signals, rich widgets, motion, mouse input, and native binaries" width="100%"/></p>

- ⚡ **Signals, not message plumbing** — read `Signal` and `Computed` values in a
  typed Scala view; glyphora tracks dependencies and redraws when they change.
- 🧩 **A serious widget vocabulary** — inputs, tables, trees, Markdown, forms,
  loading states, menus, dialogs, charts, and app chrome ship together.
- ⌨️ **Terminal interaction is first-class** — focus order, bubbling keys, bracketed
  paste, mouse hit-testing, resize events, and Unicode-aware editing are built in.
- 🎬 **Motion stays composable** — effects transform the completed frame, keeping
  widget renderers deterministic and easy to test.
- 🧪 **Production and tests share one pipeline** — render to a real terminal or the
  in-memory `HeadlessBackend`; drive full apps without a PTY.
- 📦 **Native-image by design** — Scala 3 compile-time derivation replaces runtime
  reflection, so six example apps build with `--no-fallback` and no reflect config.

## 🚀 Your first app

Add the batteries-included DSL module:

```scala
// build.mill
def mvnDeps = Seq(mvn"io.worxbend::tui-dsl:0.10.0")
```

```scala
// build.sbt
libraryDependencies += "io.worxbend" %% "tui-dsl" % "0.10.0"
```

Then return an ordinary Scala `Element` tree:

```scala
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

Three ideas carry through the entire toolkit:

1. **Model** changing values with `Signal`; derive cached values with `Computed`.
2. **Compose** the screen from elements, constraints, semantic styles, and retained
   widget state.
3. **Ship** on the JVM, test through `HeadlessBackend`, or compile a native binary.

The guided walkthrough explains every line: **[Getting started →](website/docs/getting-started.md)**

## 🧭 One render pipeline

<p align="center"><img src="docs/assets/architecture.svg" alt="glyphora typed render pipeline and module architecture" width="100%"/></p>

| Module | Owns |
|---|---|
| [`tui-core`](core/README.md) | cells, buffer, geometry, style, layout, events, Unicode display width |
| [`tui-terminal`](terminal/README.md) | backend contract, JLine 3, ANSI diffing, input decoder, headless backend |
| [`tui-widgets`](widgets/README.md) | backend-independent content, controls, data, visualization, and feedback widgets |
| [`tui-runtime`](runtime/README.md) | signals, render thread, loop, async work, timers, easing, effects |
| [`tui-dsl`](dsl/README.md) | element tree, `TuiApp`, focus/mouse routing, themes, shell, screens, toasts, palette |
| [`tui-macros`](macros/README.md) | reflection-free form and action derivation at compile time |

Use the complete `tui-dsl` stack for applications or stop at a lower layer for a
custom backend, renderer, or widget library. No widget depends on a terminal and no
terminal backend knows about signals.

**[Architecture guide →](website/docs/architecture.md)**

## 🧩 Widget atlas

| Family | Highlights |
|---|---|
| 🧱 **Layout & chrome** | panel, row/column, spacer, rule, scroll view, tabs, collapsible, split pane, layers, scaffold, sidebar |
| 📄 **Content** | text, list, table, `DataTable`, tree, directory tree, log, Markdown, OSC 8 links, half-block image |
| ⌨️ **Input** | text input/area, checkbox, toggle, select, radio group, slider, masked/number input, autocomplete, file picker, button, derived form |
| 📊 **Data viz** | gauge, sparkline, bar/stacked/pie chart, line/scatter chart, heatmap, canvas shapes, calendar |
| ✨ **Feedback** | spinner, skeleton, indeterminate bar, marquee, wave text, dialog, tooltip, toasts, splash, effects |

Every interactive state object is caller-owned. Every widget renders into a `Buffer`.
Every width calculation goes through grapheme-aware `CharWidth`.

**[Browse the complete catalog →](website/docs/widgets.md)**

## 🧪 Test the terminal without a terminal

```scala
val backend = HeadlessBackend(Size(50, 10))
val app = TodoApp()
val pilot = Pilot.start(backend) {
  val _ = app.runWith(backend)
}

pilot
  .waitForIdle()
  .typeText("ship docs")
  .pressKey(KeyCode.Enter)
  .waitForIdle()

assert(pilot.screenText.contains("· ship docs"))
```

`Pilot` posts the same event ADT used in production and exposes the last rendered
screen as text. Buffer helpers skip wide-character continuation cells, so assertions
match what users see.

> `Pilot` and `BufferAssertions` currently live in the repository's internal
> `test-support` module; the public `HeadlessBackend` can be driven directly by
> downstream projects.

**[Testing guide →](website/docs/testing.md)**

## 📦 Native binaries, zero reflection config

```bash
./mill examples.showcase.nativeImage
```

CI compiles `hello-world`, `counter`, `todo-list`, `dashboard`, `form-demo`, and
`showcase` with GraalVM `--no-fallback`, then launches each without a TTY to verify a
safe exit. Reflection and dynamic class loading are rejected in main Scala sources.

**[Native-image guide →](website/docs/native-image.md)**

## 🛠️ Work on glyphora

```bash
./mill __.compile
./mill __.test
./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources

# manual product tour
./mill examples.showcase.run

# docs + shared Wiki export
(cd website && npm ci && npm run build)
node scripts/export-wiki.mjs --output build/wiki
```

Read **[Contributing](website/docs/contributing.md)** for the widget checklist,
quality gates, docs workflow, and pull-request expectations. The shared visual and
editorial rules live in [`docs/STYLE_GUIDE.md`](docs/STYLE_GUIDE.md).

## 📚 Documentation map

- 🟢 **Start** — [Introduction](website/docs/intro.md) · [Getting started](website/docs/getting-started.md)
- 🧠 **Understand** — [State](website/docs/state-and-signals.md) · [Layout](website/docs/layout-and-style.md) · [Architecture](website/docs/architecture.md)
- 🏗️ **Build** — [App shell](website/docs/app-shell.md) · [Widgets](website/docs/widgets.md) · [Forms](website/docs/forms-and-validation.md)
- ⚙️ **Integrate** — [Async & timers](website/docs/async-and-timers.md) · [Mouse & focus](website/docs/mouse.md) · [Motion](website/docs/motion.md)
- ✅ **Ship** — [Testing](website/docs/testing.md) · [Native binaries](website/docs/native-image.md) · [Troubleshooting](website/docs/troubleshooting.md)

The Markdown above is canonical: the same pages publish to the styled GitHub Pages
site and the generated GitHub Wiki.

## 📜 License

[MIT](LICENSE) — go build something glyphorious. ✦
