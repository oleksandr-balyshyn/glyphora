---
title: Async work & timers
description: Safely connect HTTP, disk work, polling, timers, and animations to glyphora's render thread.
---

# Async work & timers

Terminal apps still fetch data, read files, poll services, and animate. glyphora
keeps those operations predictable with one rule: **do slow work off-thread; mutate
UI signals on the render thread**.

The `Async` helpers enforce that handoff for you.

## Model loading explicitly

A small state enum produces a clearer UI than parallel `loading`, `error`, and
`value` flags:

```scala
private enum LoadState[+A]:
  case Idle
  case Loading
  case Ready(value: A)
  case Failed(message: String)

private val users = Signal[LoadState[Vector[User]]](LoadState.Idle)
```

Render each state in one place:

```scala
def usersView(using ReactiveScope): Element = users.get match
  case LoadState.Idle            => text("Press r to load users.").dim
  case LoadState.Loading         => row(spinner(0), text(" loading…"))
  case LoadState.Ready(values)   => list(values.map(_.name), userList)
  case LoadState.Failed(message) => text(s"Load failed: $message").color(Color.Red)
```

## Run blocking work safely

`Async.runCatching` executes work on a daemon worker and always delivers its result
back on the render thread:

```scala
private def reload(): Unit =
  users.set(LoadState.Loading)
  Async.runCatching(api.fetchUsers()) {
    case Right(value) => users.set(LoadState.Ready(value))
    case Left(error)  => users.set(LoadState.Failed(error.getMessage))
  }
```

Because the callback is already on the render thread, setting `users` is safe and
automatically schedules the next render.

Use `Async.run` when your API already models errors in its result:

```scala
Async.run(api.fetchReport()) { result =>
  report.set(result)
}
```

## Schedule one-shot and repeating work

```scala
import scala.concurrent.duration.*

val dismiss: Cancelable = Async.after(2.seconds) {
  notice.set(None)
}

val poller: Cancelable = Async.every(10.seconds) {
  reload()
}

// Stop work when the owning screen closes.
poller.cancel()
```

Both callbacks run on the render thread. Returned `Cancelable` handles make
lifecycle ownership explicit and prevent a screen that no longer exists from
continuing to update app state.

## Use the app tick for frame-oriented work

Set `RunnerConfig.tickRate` when the whole application benefits from a regular
heartbeat:

```scala
import io.worxbend.tui.runtime.RunnerConfig
import scala.concurrent.duration.*

override def config = RunnerConfig(tickRate = Some(100.millis))

private val frame = Signal(0)

override def onTick(): Unit =
  frame.update(_ + 1)
```

Key, mouse, and tick handlers already execute on the render thread. A tick that
changes no signal causes no dependent computation to change; keep expensive work
out of `onTick` itself.

## Stopwatches and countdowns

`Stopwatch` and `Timer` are caller-owned utilities advanced by the tick loop:

```scala
import io.worxbend.tui.runtime.{RunnerConfig, Timer}
import scala.concurrent.duration.*

private val timer = Timer(30.seconds)

override def config = RunnerConfig(tickRate = Some(100.millis))

override def onTick(): Unit =
  timer.tick(100.millis)
  if timer.justExpired() then notify("Time is up", ToastLevel.Warning)

def timerView: Element = text(timer.formatted)
```

`justExpired()` returns `true` once, on the transition to zero, so side effects do
not repeat on every later tick.

## Integrate an existing callback API

If a library owns its worker thread, marshal the callback manually:

```scala
import io.worxbend.tui.runtime.RenderThread

client.onMessage { message =>
  RenderThread.runOnRenderThread {
    messages.update(_ :+ message)
  }
}
```

`runOnRenderThread` runs immediately when already on the UI thread and queues
otherwise. `runLater` always queues for the next loop iteration.

## Failure and lifecycle checklist

- Model loading and failure in state; do not let worker exceptions vanish.
- Cancel scheduled work when its screen or app feature is no longer active.
- Never block in `view`, a key handler, `onTick`, or an `Async` completion callback.
- Capture plain data in worker closures; terminal widgets and buffers belong to the
  render path.
- Keep daemon work idempotent when retry is possible.

The complete HTTP example is
[`examples/weather`](https://github.com/oleksandr-balyshyn/glyphora/tree/main/examples/weather).
Run it with `./mill examples.weather.run`.
