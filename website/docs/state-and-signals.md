---
title: State & signals
---

# State & signals

State lives in `Signal`s; any signal a view *reads* re-renders that view when it's
set. No dispatch loops, no reducers, no dependency arrays to keep in sync by hand.

```scala
val count = Signal(0)
def view(using ReactiveScope) = text(s"count: ${count.get}")
// in a binding/handler (render thread): count.update(_ + 1)
```

## The primitives

- **`Signal[A]`** — mutable reactive cell. `.get` (tracked read, subscribes the
  enclosing computation), `.peek` (untracked read), `.set` / `.update` (write; lazily
  marks dependents stale).
- **`Computed[A]`** — a derived value recomputed from signals it reads.
- **`ReactiveScope`** — the tracking capability threaded implicitly through
  `view(using ReactiveScope)`. Dependency edges are re-established on *every*
  recomputation, so conditional reads subscribe exactly the branch that ran — no
  stale subscriptions from a branch that stopped being taken.

## The render thread

Glyphora runs a single render thread (TamboUI-style). `Signal.set` asserts it's being
called on that thread:

- `checkRenderThread()` is a no-op when no runtime is running, so plain unit tests
  that construct signals and read them need no special setup.
- `runOnRenderThread` / `runLater` hop onto it explicitly when a handler runs off it
  (e.g. a background fetch callback).

In practice: mutate `Signal`s from key/mouse handlers and `onTick()` — both already
run on the render thread — and you'll never think about this rule.

## Runtime switching example

Because chrome presets like [`scaffold`](./app-shell) just read the ambient
`given Theme`, re-rendering after a `Signal[Theme]` changes is enough to re-theme the
whole app live — no explicit "theme changed" event needed.
