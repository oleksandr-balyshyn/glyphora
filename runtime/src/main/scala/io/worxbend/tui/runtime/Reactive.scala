package io.worxbend.tui.runtime

import scala.collection.mutable
import scala.compiletime.uninitialized

/** A readable reactive value: a mutable [[Signal]], a cached [[Computed]], or a transparent [[Derived]] view.
  *
  * Reads come in two flavors: `get` requires a [[ReactiveScope]] capability and subscribes the enclosing computation to
  * future changes (automatic dependency tracking — no manual dependency arrays); `peek` reads untracked. Dependency
  * edges are re-established on every recomputation, so conditional reads (`if cond.get then a.get else b.get`)
  * subscribe exactly the branch that actually ran.
  */
sealed trait Reactive[A]:

  /** Read without subscribing the caller.
    *
    * On a [[Computed]] this still re-establishes the computed's *own* dependency edges when the cached value is stale —
    * `peek` promises only that the reader is not subscribed, not that nothing at all subscribes.
    */
  def peek: A

  /** Read and subscribe the computation this scope tracks for. */
  def get(using scope: ReactiveScope): A

  /** A derived view of this value: `f` is applied on every read, and the result subscribes to nothing of its own.
    *
    * Safe to create inside a repeatedly evaluated `view` body — reading the result through a scope subscribes that
    * scope to *this* value, not to a per-generation intermediate, so nothing accumulates. `f` therefore re-runs per
    * read: when the derivation is expensive enough to be worth caching, write `Computed { f(source.get) }` instead and
    * dispose it when its owner goes away.
    */
  def map[B](f: A => B): Derived[B] = Derived(f(get))

/** A mutable reactive variable.
  *
  * `set`/`update` mark dependents stale and (via the root scope) schedule a redraw; nothing recomputes eagerly. Setting
  * an equal value notifies nobody — equality is `==`, except that floating-point values compare by IEEE-754 total order
  * (see `unchanged`). A value mutated *in place* is equal to itself, so `set(sameInstance)` never notifies: hold
  * immutable values in a signal, or set a new instance. Must only be called from the render thread once one is
  * registered — enforced by `RenderThread.checkRenderThread()`, which is a no-op in tests with no running runtime.
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
    if !unchanged(value, currentValue) then
      currentValue = value
      subscribers.toSeq.foreach(_.markStale())

  def update(f: A => A): Unit = set(f(currentValue))

  /** Change detection: `==`, except that `Double`/`Float` compare by total order.
    *
    * `==` gets both floating-point edges wrong for a change flag: `-0.0 == 0.0` is true, so a sign flip that carries
    * direction (a scroll or velocity delta) would be silently dropped, and `NaN == NaN` is false, so re-setting `NaN`
    * would notify on every write and repaint forever. `compare` distinguishes the zeros and treats `NaN` as itself.
    */
  private def unchanged(value: A, current: A): Boolean =
    (value, current) match
      case (next: Double, previous: Double) => java.lang.Double.compare(next, previous) == 0
      case (next: Float, previous: Float)   => java.lang.Float.compare(next, previous) == 0
      case _                                => value == current

  private[runtime] def subscribe(subscriber: Subscriber): Unit =
    subscribers += subscriber

  private[runtime] def unsubscribe(subscriber: Subscriber): Unit =
    subscribers -= subscriber

  /** Live subscriber count. Package-private: regression tests assert that repeated derivation does not grow it. */
  private[runtime] def subscriberCount: Int = subscribers.size

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
  // counts invalidations rather than just flagging them, so a recomputation can tell whether it was invalidated again
  // while its own thunk was running
  private var dirtyEpoch     = 0L
  private var recomputing    = false
  private val subscribers    = mutable.LinkedHashSet[Subscriber]()
  private val dependencies   = mutable.LinkedHashSet[Subscribable]()

  def peek: A =
    if stale then recompute()
    cachedValue

  def get(using scope: ReactiveScope): A =
    scope.track(this)
    peek

  /** Flags this value dirty and cascades to dependents.
    *
    * Dependents are notified when this node *becomes* stale — an already-stale node has told them once and does not
    * repeat itself. The exception is an invalidation raised while the thunk is running (a dependency written from
    * inside it): that one arrived after the notification that started this recomputation, refers to the value being
    * produced right now, and would otherwise never reach anyone.
    */
  def markStale(): Unit =
    dirtyEpoch += 1
    val wasFresh = !stale
    stale = true
    if wasFresh || recomputing then subscribers.toSeq.foreach(_.markStale())

  /** Detaches this computed from its dependencies and dependents.
    *
    * A `Computed` created inside a repeatedly-evaluated context (a `view` body) re-subscribes on every evaluation and
    * is otherwise never released — long-lived derived values belong outside `view`, and short-lived ones should be
    * disposed. The app root scope prunes its own stale subscriptions automatically; this handles the computed's
    * internal edges.
    *
    * Dependents are invalidated on the way out: they still hold an edge *to* this computed, so they must re-derive
    * rather than keep a cache this computed no longer maintains. Reading a disposed computed re-attaches it — dispose
    * detaches, it does not close.
    */
  def dispose(): Unit =
    dependencies.toSeq.foreach(_.unsubscribe(this))
    dependencies.clear()
    stale = true
    dirtyEpoch += 1
    subscribers.toSeq.foreach(_.markStale())
    subscribers.clear()

  private def recompute(): Unit =
    dependencies.toSeq.foreach(_.unsubscribe(this))
    dependencies.clear()
    val recomputeScope: ReactiveScope = dependency =>
      dependency.subscribe(this)
      val _ = dependencies.add(dependency)
    val epochBefore                   = dirtyEpoch
    recomputing = true
    cachedValue =
      try thunk(using recomputeScope)
      finally recomputing = false
    // a thunk that wrote to one of its own dependencies invalidated the value it just produced: stay stale so the next
    // read re-runs, rather than caching a value that is already out of date
    stale = dirtyEpoch != epochBefore

  private[runtime] def subscribe(subscriber: Subscriber): Unit =
    subscribers += subscriber

  private[runtime] def unsubscribe(subscriber: Subscriber): Unit =
    subscribers -= subscriber

object Computed:
  def apply[A](thunk: ReactiveScope ?=> A): Computed[A] = new Computed(thunk)

/** A transparent view of another reactive value: `read` re-runs on every access and nothing is cached.
  *
  * Unlike [[Computed]] this holds no subscription of its own — a tracked read passes the caller's scope straight
  * through to the underlying values, so the caller subscribes to the source rather than to an intermediate. There is
  * consequently no lifecycle and nothing to dispose: an instance created inside a `view` body and abandoned after one
  * frame leaves no edge behind. The trade is that `read` is evaluated per access; use [[Computed]] when the derivation
  * is expensive enough to be worth caching, and give that computed an owner that disposes it.
  *
  * Holds no mutable state, so an instance may be read from any thread its source allows.
  */
final class Derived[A] private (read: ReactiveScope ?=> A) extends Reactive[A]:

  def peek: A                            = read(using ReactiveScope.untracked)
  def get(using scope: ReactiveScope): A = read(using scope)

object Derived:
  def apply[A](read: ReactiveScope ?=> A): Derived[A] = new Derived(read)
