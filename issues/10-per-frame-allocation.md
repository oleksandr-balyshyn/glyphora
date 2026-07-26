# ~1 MiB of garbage per frame

**Labels:** `performance`, `medium`, `tui-core`

## Problem

Measured at 200×50 on JDK 25, warmed, thread-local allocation counters, steady state:

```
fill buffer (setString x50 rows)        137.2 us/op    752.1 KiB/op
diff, 0% changed (count only)           138.5 us/op    238.1 KiB/op
diff, ~1% changed (count only)          175.5 us/op    238.1 KiB/op
full-frame encode (empty -> full)      1975.4 us/op   7144.4 KiB/op
1%-change encode                        148.2 us/op    238.3 KiB/op
CharWidth.of(ascii, 176 chars)            1.2 us/op      8.3 KiB/op
```

Two sources dominate.

`Buffer.diff` (`core/src/main/scala/io/worxbend/tui/core/Buffer.scala:87-97`) allocates a
`Position` and a `Tuple2` per **cell**, not per changed cell — 238 KiB across 10 000 cells
even when the two frames are identical:

```scala
val positions =
  for
    y <- Iterator.range(next.area.y, next.area.bottom)
    x <- Iterator.range(next.area.x, next.area.right)
  yield Position(x, y)
positions
  .filter(pos => emitAll || get(pos.x, pos.y) != next.get(pos.x, pos.y))
  .filterNot(pos => next.isContinuation(pos))
  .map(pos => (pos, next.get(pos.x, pos.y)))
```

`CharWidth.graphemeClusters` (`core/.../CharWidth.scala:56-69`) allocates an anonymous
`Iterator` plus one `String` per cluster via `substring`, so plain ASCII costs one `String`
allocation per character.

Consistent with the live measurement: `examples/dashboard` at 200×50 and a 100 ms tick used
**2.85 % of one core** over 60 s and had spun up twelve G1 GC threads.

## Proposal

Two changes, both measured on this machine.

1. Replace `diff`'s `Iterator[(Position, Cell)]` with a primitive callback, and add a
   reference-equality fast path before `Cell.equals`:

```scala
def diff(next: Buffer)(emit: (Int, Int, Cell) => Unit): Unit
```

Measured: **138 µs → 49 µs and 238 KiB → 0 KiB.** Keep the current `Iterator` overload
delegating to it if anything outside the backend uses it.

2. Add an ASCII fast path to `CharWidth.of` — scan for any `char >= 0x80`; if none, return
   `length`:

Measured: **1.2 µs → ~0.0 µs and 8.3 KiB → 0 KiB.**

### Tried and rejected

Measured here; do not spend time on these:

| Optimization | Result | Why it failed |
|---|---|---|
| Reuse one `StringBuilder` across frames | 148.2 → **170.9** µs/op, allocation unchanged | The builder is not the allocation source; `diff`'s per-cell tuples are. `clear()` on a grown builder costs more than a fresh small one. |
| Memoize `AnsiSequences.sgr` in a `HashMap[Style, String]` | 141.6 → 133.8 µs/op (5 %), allocation unchanged | The emitter's `currentStyle` run-dedupe (`JLine3Backend.scala:46-48`) already elides ~99 % of `sgr` calls. |
| Diff rows in parallel (`IntStream.parallel`) | 48.8 µs/op vs 49.6 µs/op single-threaded | No gain over the plain index loop, and it puts a ForkJoin hand-off on the render thread. 10 000 cell comparisons are memory-bound. |

## Acceptance criteria

- [ ] Steady-state 200×50 frame (fill + diff + encode) allocates under 100 KiB
- [ ] `Buffer.diff` on two identical frames allocates zero bytes
- [ ] `CharWidth.of` on a pure-ASCII string allocates zero bytes and returns the same value as today
- [ ] `CharWidthSpec` and `CharWidthPropertySpec` still pass unchanged (the fast path must be observationally identical)
- [ ] `examples/dashboard` idle CPU at a 100 ms tick drops below 2 % of one core over 60 s
- [ ] A committed allocation benchmark makes regressions visible
