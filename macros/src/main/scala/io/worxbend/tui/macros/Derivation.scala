package io.worxbend.tui.macros

import scala.compiletime.{constValueTuple, erasedValue, error}
import scala.deriving.Mirror

/** Handles an application action value. Bound at compile time via [[bindAction]] — never through runtime
  * reflection (the TamboUI `@OnAction` lesson, SPEC.md §6).
  */
trait ActionHandler[A]:
  def handle(action: A): Unit

object ActionHandler:
  /** Non-inline factory so the wrapper class exists once instead of being duplicated at every inline site. */
  def of[A](handler: A => Unit): ActionHandler[A] =
    new ActionHandler[A]:
      def handle(action: A): Unit = handler(action)

/** Derives a [[FormSpec]] for a case class at compile time via `Mirror.ProductOf` — field names become field
  * specs, field types choose the input kind (`String` → text, `Int` → int, `Boolean` → bool). Unsupported
  * field types are a compile error, not a runtime surprise. No runtime reflection anywhere.
  */
inline def deriveForm[A](using m: Mirror.ProductOf[A]): FormSpec[A] =
  val names = constValueTuple[m.MirroredElemLabels].toList.map(_.toString)
  val inputs = fieldInputs[m.MirroredElemTypes]
  val fields = names.zip(inputs).map(FieldSpec(_, _))
  FormSpec(fields, values => m.fromProduct(Tuple.fromArray(values.toArray)))

/** Binds a handler function to an action type at compile time. The `inline` keeps the dispatch a direct call
  * in the generated code — no lookup table, no reflective invocation.
  */
inline def bindAction[A](inline handler: A => Unit): ActionHandler[A] =
  ActionHandler.of(handler)

private inline def fieldInputs[T <: Tuple]: List[FieldInput] =
  inline erasedValue[T] match
    case _: EmptyTuple => Nil
    case _: (head *: tail) => fieldInputOf[head] :: fieldInputs[tail]

private inline def fieldInputOf[H]: FieldInput =
  inline erasedValue[H] match
    case _: String  => FieldInput.TextField
    case _: Int     => FieldInput.IntField
    case _: Boolean => FieldInput.BoolField
    case _ =>
      error("deriveForm supports String, Int, and Boolean fields; wrap other types or add a custom Field")
