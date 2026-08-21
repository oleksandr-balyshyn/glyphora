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

  final case class BoolLike(
      spec: FieldSpec,
      value: Signal[Boolean],
      parse: Boolean => Either[String, Any],
  ) extends FieldBinding

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
      case FieldBinding.BoolLike(spec, value, parse) => spec.name -> parse(value.peek)
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
    * (only the name is taken from their own `FieldSpec` — position comes from the derived spec).
    *
    * A validator naming a field the spec does not declare, or two validators naming the same field, is a programmer
    * error and throws here, the way a malformed key spec throws from [[binding]]. Both are static declarations, and a
    * silently dropped validator is invisible at runtime: the form submits unvalidated data and looks like it passed.
    */
  def of[A](spec: FormSpec[A], validators: Field[?]*): FormState[A] =
    val declared = spec.fields.map(_.name).toSet
    val unknown  = validators.map(_.spec.name).distinct.filterNot(declared.contains)
    if unknown.nonEmpty then
      throw IllegalArgumentException(
        s"validator(s) for field(s) ${unknown.mkString(", ")} that the form does not declare; " +
          s"it declares ${spec.fields.map(_.name).mkString(", ")}"
      )
    val repeated =
      validators.groupBy(_.spec.name).collect { case (name, declaredTwice) if declaredTwice.sizeIs > 1 => name }
    if repeated.nonEmpty then
      throw IllegalArgumentException(s"more than one validator for field(s) ${repeated.mkString(", ")}")

    val byName                                       = validators.map(field => field.spec.name -> field).toMap
    // A field with no caller-supplied validator still needs one, and which parser a field type gets is
    // `deriveForm`'s decision, not this method's — `Field.default` is the other half of that mapping.
    def validatorFor(fieldSpec: FieldSpec): Field[?] = byName.getOrElse(fieldSpec.name, Field.default(fieldSpec))

    val bindings = spec.fields.map { fieldSpec =>
      fieldSpec.input match
        case FieldInput.BoolField                       =>
          // a checkbox holds a Boolean, so its validator sees the same `"true"`/`"false"` text `Field.bool` parses
          FieldBinding.BoolLike(
            fieldSpec,
            Signal(false),
            checked => validatorFor(fieldSpec).parse(checked.toString).map(value => value: Any),
          )
        case FieldInput.TextField | FieldInput.IntField =>
          val field = validatorFor(fieldSpec)
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
        case FieldBinding.BoolLike(spec, value, _)      =>
          Element.checkbox(spec.name, value)
      val error = currentErrors.get(binding.spec.name).map { message =>
        Element.text(s"${" ".repeat(labelWidth)}! $message").color(Color.Red).length(1)
      }
      field +: error.toSeq
    }
    Element.column(rows*)

  /** A screen-reader-friendly rendering of the same [[FormState]] (Huh's `WithAccessible`): every field on its own
    * labeled line announced as "Field N of M", checkbox state spelled out as text, and validation failures prefixed
    * with "Error:" rather than signalled by color alone. Pair with a plain Tab/Enter key flow for assistive tech.
    */
  def accessible[A](state: FormState[A])(using ReactiveScope): Element =
    val currentErrors = state.errors.get
    val total         = state.bindings.size
    val rows          = state.bindings.zipWithIndex.flatMap { case (binding, index) =>
      val position = s"Field ${index + 1} of $total"
      val field    = binding match
        case FieldBinding.TextLike(spec, inputState, _) =>
          Element.column(
            Element.text(s"$position: ${spec.name}").length(1),
            Element.input(inputState).fill.length(1),
          )
        case FieldBinding.BoolLike(spec, value, _)      =>
          val announced = if value.get then "checked" else "unchecked"
          Element.column(
            Element.text(s"$position: ${spec.name} ($announced)").length(1),
            Element.checkbox(spec.name, value).length(1),
          )
      val error    = currentErrors.get(binding.spec.name).map { message =>
        Element.text(s"Error: $message").color(Color.Red).length(1)
      }
      field +: error.toSeq
    }
    Element.column(rows*)
