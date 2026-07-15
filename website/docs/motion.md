---
title: Motion
---

# Motion

Widgets render normally, then active `Effect`s post-process the rendered frame based
on elapsed time (the [tachyonfx](https://github.com/junkdog/tachyonfx) model). Effects
are stateless in wall-clock — the runtime tracks each effect's start and passes total
`elapsed`, which keeps combinators pure and replayable.

```scala
trait Effect:
  def process(elapsed: FiniteDuration, buffer: Buffer, area: Rect): Unit
  def duration: Duration // Duration.Inf for effects that never finish
```

## Built-in effects

| Effect | What it does |
|---|---|
| `Effect.fadeIn(duration, easing)` / `fadeOut(...)` | Cell colors interpolate from/to black. |
| `Effect.coalesce(duration, easing, seed)` | Cells materialize in seeded pseudo-random order. |
| `Effect.dissolve(duration, easing, seed)` | Cells dissolve away in seeded pseudo-random order. |
| `Effect.sweepIn(duration, easing)` | Reveals column by column, left to right. |
| `Effect.slideInFromRight(duration, easing)` | Content slides in from the right edge. |
| `Effect.typewriter(duration, easing)` | Reveals in reading order (row-major), like typing. |
| `Effect.pulse(period)` | Endless brightness oscillation — `duration = Duration.Inf`. |

Every duration-based effect takes an `Easing` (`Easing.Linear`, `QuadIn`, `QuadOut`, …).

## Combinators

```scala
Effect.sequence(effectA, effectB)          // runs one after another
Effect.parallel(effectA, effectB)          // runs together; done when the longest finishes
Effect.delay(500.millis, effect)           // holds at progress zero, then plays effect
Effect.repeat(effect, times = 3)           // loops a finite effect
```

## Splash screens

```scala
override def splash: Option[SplashScreen] = Some(
  SplashScreen(
    content = bigText("glyphora").color(Color.Cyan),
    effect = Effect.sequence(Effect.coalesce(600.millis), Effect.pulse(1.second)),
    minimumDuration = 1500.millis,
  )
)
```

Shown before the first `view` render; any key skips it early. See
[The app shell](./app-shell#screens-toasts-splash).
