package io.worxbend.tui.dsl

import scala.concurrent.duration.FiniteDuration

/** Serialises the tests that call `AnimationClock.freezeAt`.
  *
  * A running app's clock is its own — one signal per render loop — so this is no longer about apps racing each other.
  * What is still shared is what `freezeAt` writes: the clock every caller with no runner reads, and the value any app
  * started afterwards begins at. Two tests pinning that at once decide each other's frames. [[frozenAt]] holds a lock
  * across both the pin and the render that asserts on it, which is the only window where the interleaving matters.
  *
  * A test that merely wants a deterministic frame should not come here at all: the `…At(elapsed)` factories —
  * `spinnerAt`, `orbitSpinnerAt`, `indeterminateBarAt` — read nothing ambient, and are what the rest of the tests use.
  */
private[dsl] object AnimationClockLock:

  /** Pins the clock for the duration of `body` and lets it go again afterwards — including when `body` throws, so one
    * failing assertion cannot leave every later test in the JVM running against a stopped clock.
    */
  def frozenAt[A](elapsed: FiniteDuration)(body: => A): A =
    synchronized:
      AnimationClock.freezeAt(elapsed)
      try body
      finally AnimationClock.resume()
