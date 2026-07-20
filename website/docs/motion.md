---
title: Motion
description: Add composable post-render effects, eased value animation, springs, and skippable splash screens to glyphora apps.
---

# Motion

glyphora separates content from presentation in time. Widgets first render a normal,
deterministic frame; active `Effect`s then transform its cells using elapsed time.
That keeps motion out of widget state and makes effects composable and replayable.

Use motion to explain change, preserve context, or add a brief sense of arrival—not
to delay every interaction.

## Enable animation ticks

Effects need the runtime to produce frames while time passes:

```scala
import io.worxbend.tui.runtime.RunnerConfig
import scala.concurrent.duration.*

override def config = RunnerConfig(tickRate = Some(50.millis))
```

Twenty frames per second is usually enough for terminal animation. Faster ticks
increase CPU and terminal traffic without guaranteeing a smoother emulator.

## Run a frame effect

From a key, mouse, or other render-thread handler:

```scala
runEffect(Effect.sweepIn(350.millis, Easing.CubicOut))
```

The effect applies to the full frame after normal rendering. When a finite effect
finishes, the runtime removes it and paints one final unmodified frame.

## Pick an effect

| Effect | Visual behavior | Good for |
|---|---|---|
| `fadeIn` / `fadeOut` | interpolates foreground colors from/to black | gentle screen transitions |
| `coalesce` / `dissolve` | reveals/hides cells in deterministic random order | splash and success moments |
| `sweepIn` | reveals columns left to right | page or panel arrival |
| `slideInFromRight` | shifts content in from the right | directional navigation |
| `typewriter` | reveals cells in row-major reading order | short messages and intros |
| `pulse` | endless brightness oscillation | a single live/attention state |

Most finite effects take a duration and optional `Easing`. Scatter effects also take
a seed; equal seeds produce equal cell order, which makes testing repeatable.

```scala
Effect.coalesce(
  duration = 600.millis,
  easing = Easing.QuadOut,
  seed = 42,
)
```

## Compose timelines

```scala
val reveal = Effect.sequence(
  Effect.coalesce(450.millis),
  Effect.delay(150.millis, Effect.fadeIn(250.millis)),
)

val energetic = Effect.parallel(
  Effect.sweepIn(350.millis, Easing.CubicOut),
  Effect.fadeIn(240.millis),
)

val threePulses = Effect.repeat(Effect.fadeIn(180.millis), times = 3)
```

- `sequence` runs effects one after another;
- `parallel` runs all effects and completes with the longest;
- `delay` holds an effect at zero progress before it begins;
- `repeat` loops a finite effect a fixed number of times.

An infinite effect such as `pulse` never prunes itself. Use it sparingly, and avoid
placing one inside a sequence that needs to continue.

## Choose an easing curve

`Easing.Linear` moves at constant speed. The `Quad`, `Cubic`, `Quart`, `Quint`,
`Sine`, `Expo`, and `Circ` families provide `In`, `Out`, and `InOut` variants.
`Back`, `Elastic`, and `Bounce` intentionally overshoot or oscillate.

A practical default set:

- `QuadOut` — quick start, gentle finish; most entrances;
- `QuadIn` — accelerates away; exits;
- `CubicInOut` — symmetric state transition;
- `Linear` — progress tied directly to time;
- `BackOut` — playful overshoot for a small, isolated value.

## Animate a value with Tween

`Tween` is for numbers that your view consumes—gauge ratios, offsets, or a chart
range—rather than whole-frame cell transforms:

```scala
import scala.concurrent.duration.*

private val elapsed = Signal(0.millis)
private val progress = Tween(0.0, 1.0, 800.millis, Easing.CubicOut)

override def onTick(): Unit =
  elapsed.update(_ + 50.millis)

def view(using ReactiveScope): Element =
  gauge(progress.at(elapsed.get))
```

`Tween.at` clamps through the easing function, so values remain at their completed
endpoint after the duration.

## Use a spring for physical motion

A `Spring` has no fixed duration. Step it each tick until it settles:

```scala
import io.worxbend.tui.runtime.Spring

private val spring = Spring(frequency = 6.0, damping = 0.75, deltaTime = 0.05)
private var position = 0.0
private var velocity = 0.0
private val target = Signal(100.0)

override def onTick(): Unit =
  val next = spring.step(position, velocity, target.peek)
  position = next._1
  velocity = next._2
```

Lower damping bounces; `1.0` is approximately critically damped; higher values feel
slower. Springs suit scroll or selection motion where a destination can change
mid-flight.

## Build a skippable splash

```scala
override def splash = Some(
  SplashScreen(
    content = centered(40, 7) {
      column(
        bigText("GLYPHORA").color(Color.Cyan),
        text("terminal UI, written like Scala").dim,
      )
    },
    effect = Effect.coalesce(650.millis),
    minimumDuration = 1.second,
  )
)
```

The splash runs before the first normal view. Any key skips it, and glyphora supplies
a default tick when the app config has none.

## Motion checklist

- Keep most transitions under 500 ms.
- Never animate away an error or required action.
- Prefer one dominant effect over several competing ones.
- Offer a reduced-motion setting for persistent animation.
- Test finite effects at zero, halfway, completion, and after completion.
- Use stable scatter seeds in tests.

Effects process the same buffers used by headless tests; see [Testing](./testing) for
deterministic frame assertions.
