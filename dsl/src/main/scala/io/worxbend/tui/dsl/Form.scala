package io.worxbend.tui.dsl

import io.worxbend.tui.core.Color
import io.worxbend.tui.macros.{Field, FieldInput, FieldSpec, FormSpec}
import io.worxbend.tui.runtime.{ReactiveScope, Signal}
import io.worxbend.tui.widgets.TextInputState

/** One rendered form field: its derived spec, the control state holding its raw value, and the parser that validates
  * the raw value on submit.
  */
private[dsl] sealed trait FieldBinding:
  def spec: FieldSpec

private[dsl] object FieldBinding:
  final case class TextLike(
      spec: FieldSpec,
      state: TextInputState,
      parse: String => Either[String, Any],
  ) extends FieldBinding

  final case class BoolLike(spec: FieldSpec, value: Signal[Boolean]) extends FieldBinding

/** Live state for a compile-time-derived form: text/int fields become inputs, boolean fields become checkboxes;
  * [[submit]] runs each field's parser/validators — errors land in [[errors]] per field, a fully valid form lands in
  * [[result]].
  *
  * Custom validation attaches per field name via cue4s-style [[Field]] composition:
  * `FormState.of(deriveForm[Signup], Field.int("age").mapValidated(...))`.
  */
final class FormState[A] private (private[dsl] val bindings: Seq[FieldBinding], assemble: Seq[Any] => A):

  val errors: Signal[Map[String, String]] = Signal(Map.empty)
  val result: Signal[Option[A]]           = Signal(None)

  /** Validates every field; either publishes per-field errors or the assembled value. */
  def submit(): Unit =
    val parsed: Seq[(String, Either[String, Any])] = bindings.map {
      case FieldBinding.TextLike(spec, state, parse) => spec.name -> parse(state.value)
      case FieldBinding.BoolLike(spec, value)        => spec.name -> Right(value.peek)
    }
    val failed                                     = parsed.collect { case (name, Left(message)) => name -> message }
    if failed.nonEmpty then
      errors.set(failed.toMap)
      result.set(None)
    else
      errors.set(Map.empty)
      result.set(Some(assemble(parsed.collect { case (_, Right(value)) => value })))

object FormState:

  /** Builds live state from a derived [[FormSpec]]; `validators` override the default per-type parsers by field name
    * (their own `FieldSpec` is ignored — position comes from the derived spec).
    */
  def of[A](spec: FormSpec[A], validators: Field[?]*): FormState[A] =
    val byName   = validators.map(field => field.spec.name -> field).toMap
    val bindings = spec.fields.map { fieldSpec =>
      fieldSpec.input match
        case FieldInput.BoolField                       =>
          FieldBinding.BoolLike(fieldSpec, Signal(false))
        case FieldInput.TextField | FieldInput.IntField =>
          val default =
            if fieldSpec.input == FieldInput.IntField then Field.int(fieldSpec.name) else Field.text(fieldSpec.name)
          val field   = byName.getOrElse(fieldSpec.name, default)
          FieldBinding.TextLike(fieldSpec, TextInputState(), raw => field.parse(raw).map(value => value: Any))
    }
    new FormState(bindings, spec.assemble)

/** Renders a [[FormState]] as labeled controls with inline validation errors — the Tier 2 `Form` widget, composed from
  * `input`/`checkbox` so it inherits focus traversal for free.
  */
object Form:

  def apply[A](state: FormState[A])(using ReactiveScope): Element =
    val currentErrors = state.errors.get
    val labelWidth    = state.bindings.map(_.spec.name.length).maxOption.getOrElse(0) + 2
    val rows          = state.bindings.flatMap { binding =>
      val field = binding match
        case FieldBinding.TextLike(spec, inputState, _) =>
          Element
            .row(
              Element.text(s"${spec.name}:").length(labelWidth),
              Element.input(inputState).fill,
            )
            .length(1)
        case FieldBinding.BoolLike(spec, value)         =>
          Element.checkbox(spec.name, value)
      val error = currentErrors.get(binding.spec.name).map { message =>
        Element.text(s"${" ".repeat(labelWidth)}! $message").color(Color.Red).length(1)
      }
      field +: error.toSeq
    }
    Element.column(rows*)
