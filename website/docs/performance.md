---
title: Performance
description: The benchmarks that measure glyphora's render path, how to run them, and why none of them is a CI gate.
---

# Measure the render path

Every frame a glyphora application draws goes through three steps, and each has a
benchmark of its own:

1. **Composition** — the widgets draw into a `Buffer`. No terminal is involved and no
   escape sequence exists yet. Measured by `RenderLoopBench`.
2. **Diff** — the new buffer is compared against the last flushed one, cell by cell, and
   only the differences survive. Measured by `FramePipelineBench`.
3. **Encode** — the surviving cells are turned into one batched string of ANSI escape
   sequences, which is what is finally written to the terminal. Also measured by
   `FramePipelineBench`.

Both benchmarks share the `Bench` harness published as part of `tui-test`, so their
output has the same shape and two runs diff cleanly in a terminal.

## Run them

```bash
./mill widgets.test.runMain io.worxbend.tui.widgets.RenderLoopBench
./mill terminal.test.runMain io.worxbend.tui.terminal.FramePipelineBench
```

Neither prints anything but a table. Neither asserts anything, and neither is run by any
suite — see [why they are not gates](#why-none-of-this-is-a-ci-gate) below.

## What each row means

`RenderLoopBench` composes one dashboard-like frame — a header, two one-row meters, a
sparkline and a body of prose that must be re-wrapped every frame — at four buffer sizes,
twice each:

- **fresh buffer** allocates a `Buffer` per frame, which is what a naive render loop costs.
- **reused buffer** clears one buffer and draws into it again, which is what `RenderThread`
  actually does.

The gap between those two rows is the allocation, so a change claiming to reduce
allocation has somewhere to show it.

`FramePipelineBench` builds two frames that differ in one of three ways and measures the
diff and the encode over each:

- **idle** — nothing moved, the shape of a tick where the application had no news.
- **onecell** — one cell moved, the shape of a cursor blink or a clock updating.
- **full** — everything moved, the shape of the first frame after a resize.

The encode rows are measured at two color depths, because `ColorDepth.TrueColor` and
`ColorDepth.Ansi16` emit different sequences for the same style, and the per-run style
handling is exactly where that shows.

## Reference numbers

Taken on one developer machine so that the *shape* of the table is on record — a 16-core
x86-64 Linux box, JDK 23, glyphora 0.13.0, nothing else running. Your absolute numbers
will differ; what should hold is the relationships (idle and one-cell frames cost about the
same, a full frame costs several times more, encode dominates diff on a full frame).

| Benchmark | ns/op |
| --- | --- |
| compose 80x24, reused buffer | ~117 000 |
| compose 200x50, reused buffer | ~131 000 |
| diff 200x50, idle | ~52 000 |
| diff 200x50, full | ~65 000 |
| encode 200x50, idle, truecolor | ~52 000 |
| encode 200x50, full, truecolor | ~179 000 |

## Why none of this is a CI gate {#why-none-of-this-is-a-ci-gate}

A wall-clock threshold asserted in CI fails for reasons that have nothing to do with the
commit under test: a noisy neighbour on a shared runner, a different machine class, a
garbage collection that happened to land inside the timed window. The repository has been
through enough flaky-suite work already to not want a timing assertion added to it.

What *can* be asserted is work done rather than seconds spent, and that is asserted:
`ViewportCostSpec` in the widget tests pins that a scrolled viewport does an amount of
work proportional to the visible rows rather than to the document. When a performance
property must hold, write it that way — count the operations, not the nanoseconds.

The benchmarks here are for a person comparing two commits on one machine in one sitting,
which is the only setting in which their numbers mean anything.

## Related

- [Testing](./testing) — the `Pilot` harness, buffer assertions and golden frames.
- [Architecture](./architecture) — why rendering is diff-based in the first place.
