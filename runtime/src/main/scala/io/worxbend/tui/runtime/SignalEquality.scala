package io.worxbend.tui.runtime

/** The change-detection strategy a [[Signal]] consults on every `set`/`update`: when `unchanged(next, current)` is true
  * the write is dropped — the signal keeps `current` and notifies nobody.
  *
  * The strategy is selected statically, by the type the signal is created with, not by the runtime class of the values
  * it happens to hold: `Signal(0.0)` gets [[SignalEquality.doubleTotalOrder]] because the expression is typed `Double`.
  * Instances are expected to be stateless and pure — the check runs on the render thread, inline with `set`, on every
  * write, so an implementation that allocates, blocks, or observes mutable state outside its two arguments breaks the
  * signal's contract, not just its performance.
  *
  * Provide a local `given SignalEquality[A]` to override change detection for a type (for example, to compare case
  * classes by a subset of their fields). A `given` in lexical or imported scope outranks the defaults in the companion,
  * which sit in implicit scope.
  */
trait SignalEquality[A]:

  /** `true` when writing `next` over `current` is not a change: the signal keeps `current` and notifies nobody. */
  def unchanged(next: A, current: A): Boolean

object SignalEquality:

  /** The default strategy: plain `==`, which is reference equality for types that do not override `equals`.
    *
    * A value mutated *in place* is equal to itself, so `set(sameInstance)` never notifies — hold immutable values in a
    * signal, or set a new instance.
    */
  given standard[A]: SignalEquality[A] with
    def unchanged(next: A, current: A): Boolean = next == current

  /** `Double` compares by IEEE-754 total order rather than `==`.
    *
    * `==` gets both floating-point edges wrong for a change flag: `-0.0 == 0.0` is true, so a sign flip that carries
    * direction (a scroll or velocity delta) would be silently dropped, and `NaN == NaN` is false, so re-setting `NaN`
    * would notify on every write and repaint forever. `compare` distinguishes the zeros and treats `NaN` as itself.
    */
  given doubleTotalOrder: SignalEquality[Double] with
    def unchanged(next: Double, current: Double): Boolean = java.lang.Double.compare(next, current) == 0

  /** `Float` compares by IEEE-754 total order, for the same reason as [[doubleTotalOrder]]. */
  given floatTotalOrder: SignalEquality[Float] with
    def unchanged(next: Float, current: Float): Boolean = java.lang.Float.compare(next, current) == 0
