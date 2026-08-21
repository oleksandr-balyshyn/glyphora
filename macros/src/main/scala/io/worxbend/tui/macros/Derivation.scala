package io.worxbend.tui.macros

import scala.compiletime.{constValue, constValueTuple, erasedValue, error}
import scala.deriving.Mirror

/** A named, reusable handler for an application's action type `A` — typically an `enum` of the things the user can ask
  * the application to do. Wrapping the function in a type gives the handler somewhere to live (a field, a constructor
  * parameter, a value passed between components) instead of an anonymous lambda threaded through call sites.
  *
  * Build one with [[bindAction]].
  *
  * Threading: `handle` runs on whatever thread calls it, with no queueing or hand-off of its own. When an application
  * dispatches actions from the DSL that thread is the render thread, so an implementation must not block it — no
  * network calls, no waiting on a lock, no sleeping. Hand slow work to `Async` and let its continuation come back to
  * the render loop.
  */
trait ActionHandler[A]:
  def handle(action: A): Unit

object ActionHandler:
  /** Non-inline factory so the wrapper class exists once instead of being duplicated at every inline site. */
  def of[A](handler: A => Unit): ActionHandler[A] =
    new ActionHandler[A]:
      def handle(action: A): Unit = handler(action)

/** Derives a [[FormSpec]] for a case class at compile time via `Mirror.ProductOf` — field names become field specs,
  * field types choose the input kind (`String` → text, `Int` → int, `Boolean` → bool). Unsupported field types are a
  * compile error, not a runtime surprise. No runtime reflection anywhere.
  *
  * The returned spec's `assemble` takes one value per derived field, in declaration order, each already of that field's
  * declared type; see [[FormSpec]] for the full contract. A call with the wrong number of values throws an
  * `IllegalArgumentException` naming the fields it wanted, the same programmer-error convention `FormState.of` uses for
  * a validator that names a field the form does not declare.
  */
inline def deriveForm[A](using m: Mirror.ProductOf[A]): FormSpec[A] =
  val names  = constValueTuple[m.MirroredElemLabels].toList.map(_.toString)
  val inputs = fieldInputs[m.MirroredElemLabels, m.MirroredElemTypes]
  FormSpec.ofProduct(names.zip(inputs).map(FieldSpec(_, _)), m.fromProduct)

/** Wraps a function as a named [[ActionHandler]] for the action type `A`.
  *
  * Intended use is one handler per application, holding the `match` over the action enum, so that the code that raises
  * an action (a key binding, a button) and the code that carries it out stay separate:
  *
  * {{{
  * enum CounterAction:
  *   case Increment, Reset
  *
  * val actions = bindAction[CounterAction] {
  *   case CounterAction.Increment => count.set(count.peek + 1)
  *   case CounterAction.Reset     => count.set(0)
  * }
  *
  * actions.handle(CounterAction.Increment)
  * }}}
  *
  * The result is an ordinary Scala function value behind a small wrapper class, so no reflect-config entry is ever
  * generated for it and native-image builds stay configuration-free. `handle` inherits the caller's thread and the
  * no-blocking rule that comes with it — see [[ActionHandler]].
  *
  * Note that nothing in `tui-dsl` routes events through an `ActionHandler` today; it is a value an application composes
  * for itself, and `TuiApp.bindings` still takes plain functions.
  */
inline def bindAction[A](inline handler: A => Unit): ActionHandler[A] =
  ActionHandler.of(handler)

/** Walks the case class's field labels `L` and field types `T` in lockstep — the two tuples always have the same length
  * — so that an unsupported type can be reported against the name of the field that carries it.
  */
private inline def fieldInputs[L <: Tuple, T <: Tuple]: List[FieldInput] =
  inline erasedValue[(L, T)] match
    case _: (EmptyTuple, EmptyTuple)      => Nil
    case _: (name *: names, head *: tail) => fieldInputOf[name, head] :: fieldInputs[names, tail]

/** Picks the input control for one field, given its label type `N` (a string literal type) and its declared type `H`.
  * Anything other than `String`, `Int` or `Boolean` aborts compilation; `constValue[N]` is a constant expression, so
  * the field's name folds into the message `error` reports.
  */
private inline def fieldInputOf[N, H]: FieldInput =
  inline erasedValue[H] match
    case _: String  => FieldInput.TextField
    case _: Int     => FieldInput.IntField
    case _: Boolean => FieldInput.BoolField
    case _          =>
      error(
        "deriveForm: field \"" + constValue[N] + "\" has an unsupported type — only String, Int and Boolean fields " +
          "can be derived. Drop the field from the derived case class and add the control for it by hand, or split " +
          "the unsupported part out into a nested form."
      )
