package io.worxbend.tui.dsl

import scala.concurrent.duration.FiniteDuration

/** Serialises the suites whose subject is [[AnimationClock]] itself.
  *
  * The clock is one process-global signal and ScalaTest runs suites in parallel, so two suites pinning it at once
  * decide each other's frames. [[frozenAt]] holds a lock across both the pin and the render that asserts on it, which
  * is the only window where the interleaving matters.
  *
  * A suite that merely wants a deterministic frame should not come here at all: the `…At(elapsed)` factories —
  * `spinnerAt`, `orbitSpinnerAt`, `indeterminateBarAt` — read no global state, and are what the rest of the tests use.
  */
private[dsl] object AnimationClockLock:

  def frozenAt[A](elapsed: FiniteDuration)(body: => A): A =
    synchronized:
      AnimationClock.freezeAt(elapsed)
      body
