package io.worxbend.tui.runtime

import scala.collection.mutable
import scala.compiletime.uninitialized

/** A readable reactive value: either a mutable [[Signal]] or a derived [[Computed]].
  *
  * Reads come in two flavors: `get` requires a [[ReactiveScope]] capability and subscribes the enclosing computation to
  * future changes (automatic dependency tracking — no manual dependency arrays); `peek` reads untracked. Dependency
  * edges are re-established on every recomputation, so conditional reads (`if cond.get then a.get else b.get`)
  * subscribe exactly the branch that actually ran.
  */
sealed trait Reactive[A]:

  /** Read without subscribing anything. */
  def peek: A

  /** Read and subscribe the computation this scope tracks for. */
  def get(using scope: ReactiveScope): A

  /** A derived value that recomputes (lazily) whenever this one changes. */
  def map[B](f: A => B): Reactive[B] = Computed(f(get))

/** A mutable reactive variable.
  *
  * `set`/`update` mark dependents stale and (via the root scope) schedule a redraw; nothing recomputes eagerly. Setting
  * an equal value (by `==`) notifies nobody. Must only be called from the render thread once one is registered —
  * enforced by `RenderThread.checkRenderThread()`, which is a no-op in tests with no running runtime (SPEC.md §4.1).
  */
final class Signal[A] private (initial: A) extends Reactive[A], Subscribable:

  private var currentValue: A = initial
  private val subscribers     = mutable.LinkedHashSet[Subscriber]()

  def peek: A = currentValue

  def get(using scope: ReactiveScope): A =
    scope.track(this)
    currentValue

  def set(value: A): Unit =
    RenderThread.checkRenderThread()
    if value != currentValue then
      currentValue = value
      subscribers.toSeq.foreach(_.markStale())

  def update(f: A => A): Unit = set(f(currentValue))

  private[runtime] def subscribe(subscriber: Subscriber): Unit =
    subscribers += subscriber

  private[runtime] def unsubscribe(subscriber: Subscriber): Unit =
    subscribers -= subscriber

object Signal:
  def apply[A](initial: A): Signal[A] = new Signal(initial)

/** A value derived from other reactive values.
  *
  * Lazily cached: `set` on a dependency only marks this stale (cascading to dependents); the thunk re-runs on the next
  * read. Each recomputation first unsubscribes from the previous dependency set, then re-subscribes to exactly what the
  * thunk reads this time — the mechanism that makes conditional dependencies correct.
  */
final class Computed[A] private (thunk: ReactiveScope ?=> A) extends Reactive[A], Subscriber, Subscribable:

  private var cachedValue: A = uninitialized
  private var stale          = true
  private val subscribers    = mutable.LinkedHashSet[Subscriber]()
  private val dependencies   = mutable.LinkedHashSet[Subscribable]()

  def peek: A =
    if stale then recompute()
    cachedValue

  def get(using scope: ReactiveScope): A =
    scope.track(this)
    peek

  def markStale(): Unit =
    if !stale then
      stale = true
      subscribers.toSeq.foreach(_.markStale())

  private def recompute(): Unit =
    dependencies.toSeq.foreach(_.unsubscribe(this))
    dependencies.clear()
    val recomputeScope: ReactiveScope = dependency =>
      dependency.subscribe(this)
      val _ = dependencies.add(dependency)
    cachedValue = thunk(using recomputeScope)
    stale = false

  private[runtime] def subscribe(subscriber: Subscriber): Unit =
    subscribers += subscriber

  private[runtime] def unsubscribe(subscriber: Subscriber): Unit =
    subscribers -= subscriber

object Computed:
  def apply[A](thunk: ReactiveScope ?=> A): Computed[A] = new Computed(thunk)
