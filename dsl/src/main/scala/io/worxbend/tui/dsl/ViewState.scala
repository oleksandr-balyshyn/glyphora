package io.worxbend.tui.dsl

import io.worxbend.tui.runtime.Signal

import scala.collection.mutable

/** State that belongs to a *place in the view tree* rather than to the application object.
  *
  * Why this exists. Everything reactive in glyphora is either a `Signal` the app declares as one of its own fields or a
  * caller-owned widget state (`ListState`, `TextInputState`). Both have to be declared next to the app and threaded by
  * hand into the factory call that uses them, which means a reusable piece of view — a collapsible section, a counter,
  * a search box — cannot own anything privately. Its caller has to declare the state and pass it in, and a caller that
  * builds five of them has to declare five.
  *
  * `useSignal` and `useState` remove that. They hand back a value that is created the first frame the call is reached
  * and then given back, the same instance, on every later frame that reaches it again — so the state belongs to the
  * call site rather than to whoever wrote the app.
  *
  * The identity of a call site is the chain of enclosing [[keyed]] scopes plus the position of the call within its
  * scope. That is what makes the one rule worth memorising: **a view must reach the same hook calls in the same order
  * every frame**. A hook behind an `if` that flips changes the positions of everything after it, and the state shifts
  * between call sites. Building elements in a loop needs [[keyed]] around the loop body, keyed by the item's own
  * identity (its id, not its index), so that adding or removing an item does not renumber its neighbours.
  *
  * Lifetime: a slot is created on the first frame that reaches it and dropped at the end of the first frame that does
  * not. Hiding a subtree and showing it again therefore starts it fresh, which is the behaviour a collapsed panel
  * wants; state that must outlive its own disappearance belongs in a `Signal` field on the app.
  *
  * Ownership and threads: one store belongs to one `TuiApp` run and is touched only while that run's render thread is
  * evaluating the view. Nothing here is synchronised, and nothing needs to be.
  */
final class ViewState private[dsl] ():

  private val slots: mutable.LinkedHashMap[String, ViewState.Slot] = mutable.LinkedHashMap.empty

  /** The chain of enclosing [[keyed]] scopes, innermost first. */
  private var path: List[String] = Nil

  /** How many hook calls have already been made inside the innermost scope. */
  private var ordinal: Int = 0

  /** Which frame is being evaluated. A slot reached during this frame is stamped with it; anything left holding an
    * older stamp when the frame ends was not reached and is swept.
    */
  private var generation: Long = 0L

  /** Starts a new frame: nothing has been reached yet, and the scope path is back at the root. */
  private[dsl] def beginGeneration(): Unit =
    generation += 1
    path = Nil
    ordinal = 0

  /** Drops every slot the generation that just ended did not reach. */
  private[dsl] def sweep(): Unit =
    val stale = slots.collect { case (id, slot) if slot.generation != generation => id }.toList
    stale.foreach(id => slots.subtractOne(id))

  /** How many slots are alive — for tests that want to prove a hidden subtree's state was actually released. */
  private[dsl] def slotCount: Int = slots.size

  /** Runs `body` with `key` pushed onto the scope path, so hooks inside it belong to this key rather than to the
    * position the body happens to occupy among its siblings.
    */
  private[dsl] def scoped[A](key: String)(body: => A): A =
    val savedPath    = path
    val savedOrdinal = ordinal
    path = key :: savedPath
    ordinal = 0
    try body
    finally
      path = savedPath
      // a whole keyed scope counts as one step of its parent's numbering, so siblings after it stay put
      ordinal = savedOrdinal + 1

  /** The value this call site owns, creating it the first time the site is reached.
    *
    * The cast is safe because the identifier is derived from the call site, and one call site always asks for the same
    * type: the only way to reach an existing slot with a different `A` is to break the same-order rule the class
    * documentation states, which changes the shape of the view rather than only its types.
    */
  private[dsl] def slot[A](create: () => A): A =
    val id = s"${path.reverse.mkString("/")}#$ordinal"
    ordinal += 1
    slots.get(id) match
      case Some(existing) =>
        existing.generation = generation
        existing.value.asInstanceOf[A] // scalafix:ok DisableSyntax.asInstanceOf; erased, see the Scaladoc above
      case None =>
        val created = create()
        val _       = slots.put(id, ViewState.Slot(created, generation))
        created

object ViewState:

  /** One call site's value, plus the last frame that reached it. */
  private final class Slot(val value: Any, var generation: Long)

  private object Slot:
    def apply(value: Any, generation: Long): Slot = new Slot(value, generation)

  /** The store the view currently being evaluated belongs to.
    *
    * Per thread, because each `Runner` owns its own render thread and several runners can share a JVM. There is never
    * more than one writer per entry: a view is evaluated on the render thread and nowhere else.
    */
  private val active: ThreadLocal[Option[ViewState]] = ThreadLocal.withInitial(() => Option.empty[ViewState])

  /** Evaluates `body` with `store` installed as the one hooks attach to. */
  private[dsl] def during[A](store: ViewState)(body: => A): A =
    active.set(Some(store))
    try body
    finally active.remove()

  /** The installed store, or a failure explaining what to do instead.
    *
    * Calling a hook from an event handler, a timer or a plain unit test is not a mistake worth guessing around: there
    * is no place in the tree for the state to belong to, so the message names the alternative rather than inventing an
    * anonymous slot that nothing would ever reach again.
    */
  private[dsl] def current: ViewState =
    active.get() match
      case Some(store) => store
      case None        =>
        throw new IllegalStateException(
          "useSignal, useState and keyed may only be called while a TuiApp view is being evaluated on the render " +
            "thread. An event handler, a timer body or a test helper has no place in the view tree to attach state " +
            "to; declare a Signal field on the app and read that instead."
        )

/** A `Signal` owned by this call site: created on the first frame that reaches this call, handed back unchanged on
  * every later frame that reaches it, and dropped one frame after the call stops being reached.
  *
  * It is an ordinary `Signal` — writing it invalidates the view that read it and schedules a redraw, exactly as a
  * `Signal` declared as a field on the app does. The only thing that changes is who owns it:
  *
  * {{{
  * def counter(label: String)(using ReactiveScope, Theme): Element =
  *   val count = useSignal(0)
  *   row(text(s"$label: ${count.get}"), button("+")(count.update(_ + 1)))
  * }}}
  *
  * `initial` is by-name and is evaluated only on the frame that creates the slot, so an expensive starting value costs
  * nothing on later frames.
  *
  * Read the ordering rule on [[ViewState]] before using this inside an `if` or a loop.
  */
def useSignal[A](initial: => A): Signal[A] =
  ViewState.current.slot(() => Signal(initial))

/** A plain mutable value owned by this call site — the spelling for the caller-owned widget states (`ListState`,
  * `TextInputState`, `TreeState`) that a reusable piece of view wants to keep private:
  *
  * {{{
  * def searchBox(using ReactiveScope, Theme): Element =
  *   input(useState(TextInputState()))
  * }}}
  *
  * Unlike [[useSignal]] this is not reactive: writing the object does not invalidate anything, so a mutation made
  * outside the event path has to ask for its frame with `requestRedraw()`. That is the same contract these states
  * already have when an app declares them as fields.
  *
  * `create` is by-name and runs only on the frame that creates the slot.
  */
def useState[S](create: => S): S =
  ViewState.current.slot(() => create)

/** Gives everything built inside `body` an identity of its own, so sibling instances of the same call site keep their
  * own state.
  *
  * Without it, three rows built by the same `map` all share one call site and therefore one set of slots. Key them by
  * the item's own identity — never by its index, which renumbers the survivors when an item is removed and hands each
  * of them their neighbour's state:
  *
  * {{{
  * column(rows.map(row => keyed(row.id)(rowView(row)))*)
  * }}}
  *
  * Keys have to be unique among siblings only, not across the whole view: a key is a step in a path, so the same key
  * under two different parents is two different places.
  */
def keyed[A](key: String)(body: => A): A =
  ViewState.current.scoped(key)(body)
