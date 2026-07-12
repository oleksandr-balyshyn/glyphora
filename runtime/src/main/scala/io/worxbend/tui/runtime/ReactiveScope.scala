package io.worxbend.tui.runtime

/** The capability that makes a reactive read *tracked*: `Reactive.get` requires one and reports the read to
  * it, so whoever owns the scope learns what was read and can subscribe to changes. Reads that should not
  * subscribe anything use `peek` instead — or [[ReactiveScope.untracked]] when an API demands a scope.
  */
trait ReactiveScope:
  private[runtime] def track(dependency: Subscribable): Unit

object ReactiveScope:

  /** Every reactive value read through this scope will invoke `onInvalidate` when it later changes. This is
    * the root scope an application's render loop evaluates `view` under: any `Signal.set` reachable from the
    * last evaluation schedules a redraw.
    */
  def onInvalidation(onInvalidate: () => Unit): ReactiveScope =
    val subscriber: Subscriber = () => onInvalidate()
    dependency => dependency.subscribe(subscriber)

  /** Reads through this scope subscribe nothing — equivalent to `peek`, for tests and non-reactive contexts. */
  val untracked: ReactiveScope = _ => ()
