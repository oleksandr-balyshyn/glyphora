---
title: State & signals
description: Learn glyphora's tracked reactive state, computed values, conditional dependencies, and render-thread rules.
---

# State & signals

glyphora state is intentionally small: `Signal[A]` stores a mutable value,
`Computed[A]` derives one, and `ReactiveScope` records what a view reads. That is
enough to update a terminal UI without reducers, message plumbing, or manual redraw
calls.

## Read, write, redraw

```scala
private val count = Signal(0)

def view(using ReactiveScope): Element =
  text(s"Count: ${count.get}")

// key/mouse/tick handler
count.update(_ + 1)
```

While `view` runs, `count.get` subscribes its root reactive scope. `update` changes
the value, marks that scope stale, and wakes the runtime. On the next pass the view
reads the new value and widgets render a new buffer.

Setting a signal to an equal value (using `==`) does not notify dependents.

## Signal operations

| Operation | Tracks? | Purpose |
|---|---:|---|
| `signal.get` | yes | read inside `view` or a `Computed` and subscribe |
| `signal.peek` | no | inspect current state in a handler or service without adding a dependency |
| `signal.set(value)` | — | replace the value and invalidate when it changed |
| `signal.update(f)` | — | replace the value using its current value |

Use immutable values inside signals so updates are obvious:

```scala
private val jobs = Signal(Vector.empty[Job])

def add(job: Job): Unit =
  jobs.update(_ :+ job)

def remove(id: JobId): Unit =
  jobs.update(_.filterNot(_.id == id))
```

Mutating a collection in place and setting the same reference can bypass equality
change detection. Prefer a new `Vector`, `Map`, case class, or other immutable value.

## Derive state with Computed

`Computed` values are lazy and cached. They recompute on the next read after a
dependency changes:

```scala
private val query = Signal("")
private val jobs = Signal(Vector.empty[Job])

private val visibleJobs = Computed {
  val needle = query.get.trim.toLowerCase
  if needle.isEmpty then jobs.get
  else jobs.get.filter(_.name.toLowerCase.contains(needle))
}

def view(using ReactiveScope): Element =
  column(
    text(s"Filter: ${query.get}").dim,
    text(s"${visibleJobs.get.size} matching jobs"),
    jobTable(visibleJobs.get),
  )
```

Long-lived computed values belong beside your signals, not inside `view`. If you
create a short-lived `Computed`, call `.dispose()` when its owner goes away so its
internal subscriptions are detached.

## Conditional dependencies stay accurate

Dependencies are rebuilt every time a computation runs:

```scala
private val useRemote = Signal(false)
private val localRows = Signal(Vector.empty[Row])
private val remoteRows = Signal(Vector.empty[Row])

private val activeRows = Computed {
  if useRemote.get then remoteRows.get else localRows.get
}
```

When `useRemote` is false, changes to `remoteRows` do not invalidate `activeRows`.
After the branch switches, the old subscription is removed and the remote one is
added. There is no dependency list to maintain.

## Keep view pure and cheap

`view` may run many times. It should describe current UI, not perform work:

```scala
// Good: read state and compose elements.
def view(using ReactiveScope): Element =
  report.get match
    case Some(value) => reportPanel(value)
    case None        => text("No report loaded").dim

// Avoid inside view: HTTP calls, file writes, sleeps, starting Futures,
// mutating signals, or constructing long-lived resources.
```

Start side effects from a key/mouse handler, `onTick`, an app service, or an
`Async` callback. See [Async work & timers](./async-and-timers).

## The render-thread rule

Once a runner is active, `Signal.set` and `Signal.update` must run on its render
thread. This guarantees deterministic ordering between event handling, state
changes, focus, effects, and redraws.

Already safe:

- `.onKey` / `.onKeyEvent` handlers;
- `.onMouseEvent` handlers;
- `KeyBindings` actions;
- `onTick()`;
- completion handlers passed to `Async.run` and `Async.runCatching`.

Callbacks owned by another thread must hop back:

```scala
import io.worxbend.tui.runtime.RenderThread

socket.onMessage { payload =>
  RenderThread.runOnRenderThread {
    messages.update(_ :+ payload)
  }
}
```

The guard is a no-op when no runner is registered. Plain unit tests can construct,
read, and update signals without bootstrapping a runtime.

## Runtime theme switching

Themes demonstrate the whole model: one signal chooses a theme, `view` tracks it,
and a key action changes it.

```scala
private val themes = Vector(Theme.Dark, Theme.Light, Theme.HighContrast)
private val themeIndex = Signal(0)

override def theme: Theme = themes(themeIndex.peek)

override def bindings = KeyBindings(
  binding("ctrl+t", "switch theme") {
    themeIndex.update(i => (i + 1) % themes.size)
  }
)

def view(using ReactiveScope): Element =
  given Theme = theme
  val _ = themeIndex.get // tracked read requests a new themed tree
  scaffold(statusBar = Some(statusBar(bindings)))(content)
```

`theme` itself uses `.peek` because the explicit tracked read in `view` owns
invalidation. The complete implementation is in the `showcase` example.

## A practical state checklist

- Put application facts in `Signal`; keep derived facts in `Computed`.
- Read with `.get` in view code and `.peek` in handlers when you do not need to
  create a dependency.
- Keep state values immutable and updates small.
- Create long-lived `Computed` values outside `view`.
- Model async loading and errors as a single enum.
- Marshal third-party callbacks to the render thread before writing.

Next: use state in [Forms & validation](./forms-and-validation), animate it with
[Motion](./motion), or test it through full input cycles in [Testing](./testing).
