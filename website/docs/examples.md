---
title: Examples
description: Run seven complete glyphora applications and learn which source to read for state, focus, dashboards, forms, async work, and app chrome.
---

# Learn from complete apps

The repository includes seven runnable applications. Each is intentionally small,
uses the same public APIs described in this guide, and has a headless end-to-end
test. Start with the behavior closest to your app and read its source beside the
running terminal.

```bash
git clone git@github.com:oleksandr-balyshyn/glyphora.git
cd glyphora
./mill examples.showcase.run
```

## Pick an example

| Example | Run | Best place to learn |
|---|---|---|
| `hello-world` | `./mill examples.hello-world.run` | `TuiApp`, panels, text, local key handling |
| `counter` | `./mill examples.counter.run` | signal update → tracked redraw, concise `onKey` |
| `todo-list` | `./mill examples.todo-list.run` | text entry, list state, focus switching, delete action |
| `dashboard` | `./mill examples.dashboard.run` | tick-driven gauges, sparklines, chart layout |
| `form-demo` | `./mill examples.form-demo.run` | compile-time form derivation and validation |
| `weather` | `./mill examples.weather.run` | real HTTP, loading/error states, render-thread handoff |
| `showcase` | `./mill examples.showcase.run` | scaffold, themes, palette, screens, toasts, splash |

Every app exits with `q` or `Esc`; the source comment above each app lists its full
keyboard vocabulary.

## hello-world: the smallest useful tree

```scala
object HelloWorld extends TuiApp:
  def view(using ReactiveScope): Element =
    panel("Hello")(
      text("Welcome to glyphora!").bold.color(Color.Cyan),
      spacer,
      text("Press 'q' to quit").dim,
    ).rounded.onKeyEvent {
      case KeyEvent(KeyCode.Char('q'), _) =>
        quit()
        true
      case _ => false
    }
```

This is the render model in miniature: describe an element tree, decorate it, and
handle an event near the UI it controls.

[Read hello-world source](https://github.com/oleksandr-balyshyn/glyphora/blob/main/examples/hello-world/src/main/scala/io/worxbend/tui/examples/helloworld/Main.scala)

## counter: tracked reactive redraws

```scala
final class CounterApp extends TuiApp:
  val count: Signal[Int] = Signal(0)

  def view(using ReactiveScope): Element =
    panel("Counter")(
      text(s"Count: ${count.get}").bold.color(Color.Green),
      spacer,
      text("'+' increment · '-' decrement · 'q' quit").dim,
    ).rounded
      .onKey(Key.char('+')) { count.update(_ + 1) }
      .onKey(Key.char('-')) { count.update(_ - 1) }
      .onKey(Key.char('q')) { quit() }
```

Notice that multiple `.onKey` calls compose. Only a matching handler consumes the
event, and reading `count.get` makes redraw automatic.

[Read counter source](https://github.com/oleksandr-balyshyn/glyphora/blob/main/examples/counter/src/main/scala/io/worxbend/tui/examples/counter/Main.scala)

## todo-list: state belongs to the app

```scala
val items = Signal(Vector.empty[String])
val inputState = TextInputState()
val listState = ListState()

def view(using ReactiveScope): Element =
  panel("Todo")(
    input(inputState, placeholder = "what needs doing?").onKeyEvent {
      case KeyEvent(KeyCode.Enter, _) => addItem(); true
      case _                          => false
    },
    spacer(1),
    list(items.get.map(item => s"· $item"), listState).onKey(Key.char('d')) {
      deleteSelected()
    },
    text("Enter add · Tab switch · ↑/↓ select · d delete").dim,
  ).rounded
```

`TextInputState` and `ListState` are created once outside `view`. The input and list
keep their own editing/selection mechanics while the app owns the todo collection.

[Read todo-list source](https://github.com/oleksandr-balyshyn/glyphora/blob/main/examples/todo-list/src/main/scala/io/worxbend/tui/examples/todolist/Main.scala)

## dashboard: time becomes state

```scala
override def config = RunnerConfig(tickRate = Some(100.millis))
val tick = Signal(0)

override def onTick(): Unit = tick.update(_ + 1)

def view(using ReactiveScope): Element =
  val t = tick.get
  val load = (math.sin(t * 0.1) + 1) / 2
  val samples = Vector.tabulate(60)(i =>
    (math.sin((t + i) * 0.25) * 40 + 50).toLong
  )
  row(
    panel("Load")(gauge(load)).percent(50),
    panel("Throughput")(sparkline(samples)).percent(50),
  )
```

Ticks update a signal on the render thread; charts remain ordinary pure renderers.

[Read dashboard source](https://github.com/oleksandr-balyshyn/glyphora/blob/main/examples/dashboard/src/main/scala/io/worxbend/tui/examples/dashboard/Main.scala)

## form-demo: reflection-free derivation

```scala
final case class Signup(username: String, age: Int, subscribe: Boolean)

val formState = FormState.of(
  deriveForm[Signup],
  Field.text("username").mapValidated { name =>
    if name.trim.nonEmpty then Right(name.trim) else Left("required")
  },
  Field.int("age").mapValidated { age =>
    if age >= 18 then Right(age) else Left("must be 18 or older")
  },
)
```

The macro generates metadata and the final constructor call at compile time. The UI
shows parser/validator errors inline and publishes `Some(Signup(...))` only after a
valid submit.

[Read form-demo source](https://github.com/oleksandr-balyshyn/glyphora/blob/main/examples/form-demo/src/main/scala/io/worxbend/tui/examples/formdemo/Main.scala)

## weather: real asynchronous I/O

```scala
private def search(): Unit =
  val city = cityInput.value.trim
  if city.nonEmpty then
    status.set(Status.Loading(city))
    Future(client.fetch(city)).foreach { result =>
      RenderThread.runOnRenderThread {
        status.set(result.fold(
          error => Status.Failed(city, WeatherError.describe(error)),
          Status.Loaded.apply,
        ))
      }
    }
```

The example performs two live Open-Meteo requests on a worker thread, then returns
to the render thread before changing `Signal` state. Its client is injected, so the
headless test uses a deterministic fake.

[Read weather source](https://github.com/oleksandr-balyshyn/glyphora/blob/main/examples/weather/src/main/scala/io/worxbend/tui/examples/weather/Main.scala)

## showcase: the integrated product surface

The showcase combines:

- launch splash plus frame effects;
- live theme switching;
- top bar, sidebar, tabbed content, and status hints;
- command palette generated from `KeyBindings`;
- modal screen and focus isolation;
- tick-aged toasts;
- input, gauge, sparkline, log, and Markdown widgets;
- clipboard output through the active terminal backend.

Use it as the manual PTY test bed and the fastest tour of app-level features.

[Read showcase source](https://github.com/oleksandr-balyshyn/glyphora/blob/main/examples/showcase/src/main/scala/io/worxbend/tui/examples/showcase/Main.scala)

## Run tests and native builds

```bash
# One example's end-to-end tests
./mill examples.todo-list.test

# Every example test
./mill examples.__.test

# A self-contained executable (GraalVM required)
./mill show examples.showcase.nativeImage
```

Native images use `--no-fallback` and no reflection configuration. Continue with
[Testing](./testing) or [Native binaries](./native-image).
