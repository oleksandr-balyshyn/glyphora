# A zero poll timeout makes the event loop block forever

**Labels:** `bug`, `high`, `event-loop`, `tui-runtime`

## Problem

`runtime/src/main/scala/io/worxbend/tui/runtime/TerminalRunner.scala:110-113`:

```scala
case Some(rate) =>
  val remainingNanos = rate.toNanos - (nanoTime() - lastTick)
  val clamped        = math.max(0L, math.min(remainingNanos, rate.toNanos))
  Duration.fromNanos(clamped)
```

When a redraw overruns the remaining tick budget, `remainingNanos` is negative and `clamped`
is `0`. `JLine3Backend.readEvent` passes that to `terminal.reader().read(0)`, and JLine treats
a non-positive timeout as an unbounded blocking read
(`org/jline/utils/NonBlockingReaderImpl.java:148-149`):

```java
} else if (!isPeek && timeout <= 0L && !threadIsReading) {
    ch = in.read();
```

Driven with an injected clock advancing 30 ms per read against a 16 ms tick rate, a spy backend
recorded **41 of 41** `readEvent` calls asking for `Duration.Zero`.

This is invisible to the test suite because `HeadlessBackend.readEvent` treats
`Duration.Zero` as `poll(0, MILLISECONDS)` and returns immediately
(`terminal/.../HeadlessBackend.scala:73`).

Impact: any frame slower than the tick period freezes ticks, animation, toast ageing, splash
progress and `Async` callback delivery until the user happens to press a key. A 200×50 full
repaint measures 1.98 ms warm and far more JIT-cold, and `TuiApp` forces a 50 ms tick whenever
a splash screen is set (`dsl/.../TuiApp.scala:167-168`).

## Proposal

```scala
// TerminalRunner.scala:112
val clamped = math.max(MinPollNanos, math.min(remainingNanos, rate.toNanos))

// TerminalRunner.scala, alongside DefaultPollTimeout
private val MinPollNanos: Long = 1_000_000L // never 0: JLine reads with timeout <= 0 block forever
```

To stop the two backends diverging again, make `HeadlessBackend.readEvent` reject a
non-positive finite timeout with `require(...)`, and document the contract on
`Backend.readEvent` (`Backend.scala:31-32`): *"`timeout` must be strictly positive; an infinite
`Duration` means block until an event arrives."*

## Acceptance criteria

- [ ] A runner test with an injected clock asserts no requested timeout is `Duration.Zero`
- [ ] `HeadlessBackend.readEvent(Duration.Zero)` fails loudly rather than returning immediately
- [ ] `Backend.readEvent`'s scaladoc states the positive-timeout contract
- [ ] `examples/dashboard` keeps ticking under an artificially slowed render (e.g. a 50 ms sleep in `view` with a 16 ms tick)
