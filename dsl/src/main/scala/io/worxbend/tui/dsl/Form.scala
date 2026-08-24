package io.worxbend.tui.dsl

import io.worxbend.tui.core.{CharWidth, Color}
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

/** Live state for a compile-time-derived form: boolean fields become checkboxes, everything else an input; [[submit]]
  * runs each field's parser/validators — errors land in [[errors]] per field, a fully valid form lands in [[result]].
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

  /** The `Field` factory that produces `input`, named so a rejected validator can say what to write instead. */
  private def factoryFor(input: FieldInput): String = input match
    case FieldInput.TextField    => "Field.text"
    case FieldInput.IntField     => "Field.int"
    case FieldInput.DecimalField => "Field.double"
    case FieldInput.BoolField    => "Field.bool"

  /** Builds live state from a derived [[FormSpec]]; `validators` override the default per-type parsers by field name
    * (only the name and the input kind are taken from their own `FieldSpec` — position comes from the derived spec).
    *
    * Three things are programmer errors and throw here, the way a malformed key spec throws from [[binding]]: a
    * validator naming a field the spec does not declare, two validators naming the same field, and a validator built
    * from the wrong `Field` factory for the field's declared type. All three are static declarations, and all three are
    * invisible at runtime otherwise — the first two silently drop the validator so the form submits unvalidated data
    * and looks like it passed, and the third hands a value of the wrong type to the case class's constructor, which
    * fails as a `ClassCastException` out of `Mirror.fromProduct` on the render thread when the user presses submit.
    *
    * The type check compares [[FieldInput]]s, which is what a `Field.*` factory stamps into its spec. It cannot see
    * through a `map` that changes the value type without changing the factory — `Field.int("age").map(_.toString)`
    * still says `IntField` — so [[Field.map]] keeps its own warning about that one residual case.
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

    val declaredInput = spec.fields.map(field => field.name -> field.input).toMap
    val mismatched    = validators.flatMap { validator =>
      declaredInput
        .get(validator.spec.name)
        .filterNot(_ == validator.spec.input)
        .map(declared =>
          s"field '${validator.spec.name}' is declared as $declared but its validator produces " +
            s"${validator.spec.input}; use ${factoryFor(declared)}(\"${validator.spec.name}\")"
        )
    }
    if mismatched.nonEmpty then throw IllegalArgumentException(mismatched.mkString("; "))

    val byName = validators.map(field => field.spec.name -> field).toMap

    val bindings = spec.defaults.map { derived =>
      // A field with no caller-supplied validator falls back to the one the derivation already built for it, whose
      // parser came from the field type's own `FormFieldType`. Nothing here maps an input kind back to a parser, which
      // is what lets an application's own field type carry a parser this module has never heard of.
      val field     = byName.getOrElse(derived.spec.name, derived)
      val fieldSpec = derived.spec
      fieldSpec.input match
        case FieldInput.BoolField =>
          // a checkbox holds a Boolean, so its validator sees the same `"true"`/`"false"` text `Field.bool` parses
          FieldBinding.BoolLike(fieldSpec, Signal(false), checked => field.parse(checked.toString).map(v => v: Any))
        case _                    =>
          FieldBinding.TextLike(fieldSpec, TextInputState(), raw => field.parse(raw).map(value => value: Any))
    }
    new FormState(bindings, spec.assemble)

/** Renders a [[FormState]] as labeled controls with inline validation errors, composed from `input`/`checkbox` so it
  * inherits focus traversal for free.
  */
object Form:

  /** The traversal both renderings share: one column of fields, each optionally followed by its validation error.
    *
    * `field` renders one binding (the index is its zero-based position, which only [[accessible]] announces) and
    * `errorText` decorates the failure message for that rendering. Reading `state.errors` here is what subscribes the
    * caller's `ReactiveScope`, so a failed submit repaints the form.
    */
  private def fieldColumn[A](state: FormState[A], errorText: String => String)(
      field: (FieldBinding, Int) => Element
  )(using ReactiveScope): Element =
    val currentErrors = state.errors.get
    val rows          = state.bindings.zipWithIndex.flatMap { (binding, index) =>
      val error = currentErrors.get(binding.spec.name).map(message => errorRow(errorText(message)))
      field(binding, index) +: error.toSeq
    }
    Element.column(rows*)

  /** The single-row rendering of a validation failure, shared by [[apply]] and [[accessible]] so a change to how an
    * error looks lands in both renderings at once. The two differ only in the text they hand in.
    */
  private def errorRow(message: String): Element =
    Element.text(message).fg(Color.Red).length(1)

  /** The control a text-like field is edited with, chosen from what the derivation said the field holds.
    *
    * A numeric field rendered as a plain text input accepts any keystroke, so typing `abc` into an `age` field looks
    * accepted right up until submit answers `'abc' is not a whole number`. `numberInput` refuses the keystroke instead:
    * it claims the same single row and takes the same [[TextInputState]], so it swaps in without changing anything
    * around it. The parser still runs on submit — this only stops the user reaching it with input that cannot parse.
    */
  private def textControl(spec: FieldSpec, state: TextInputState): Element =
    spec.input match
      case FieldInput.IntField     => Element.numberInput(state)
      case FieldInput.DecimalField => Element.numberInput(state).decimal
      case _                       => Element.input(state)

  def apply[A](state: FormState[A])(using ReactiveScope): Element =
    // display columns, not UTF-16 lengths: a field named with CJK or emoji characters otherwise gets a label column
    // narrower than it renders into, and the error line below it no longer lines up with the input
    val labelWidth = state.bindings.map(binding => CharWidth.of(binding.spec.name)).maxOption.getOrElse(0) + 2
    fieldColumn(state, message => s"${" ".repeat(labelWidth)}! $message") { (binding, _) =>
      binding match
        case FieldBinding.TextLike(spec, inputState, _) =>
          Element
            .row(
              Element.text(s"${spec.name}:").length(labelWidth),
              textControl(spec, inputState).fill,
            )
            .length(1)
        case FieldBinding.BoolLike(spec, value, _)      =>
          Element.checkbox(spec.name, value)
    }

  /** A screen-reader-friendly rendering of the same [[FormState]] (Huh's `WithAccessible`): every field on its own
    * labeled line announced as "Field N of M", checkbox state spelled out as text, and validation failures prefixed
    * with "Error:" rather than signalled by color alone. Pair with a plain Tab/Enter key flow for assistive tech.
    */
  def accessible[A](state: FormState[A])(using ReactiveScope): Element =
    val total = state.bindings.size
    fieldColumn(state, message => s"Error: $message") { (binding, index) =>
      val position = s"Field ${index + 1} of $total"
      binding match
        case FieldBinding.TextLike(spec, inputState, _) =>
          Element.column(
            Element.text(s"$position: ${spec.name}").length(1),
            textControl(spec, inputState).fill.length(1),
          )
        case FieldBinding.BoolLike(spec, value, _)      =>
          val announced = if value.get then "checked" else "unchecked"
          Element.column(
            Element.text(s"$position: ${spec.name} ($announced)").length(1),
            Element.checkbox(spec.name, value).length(1),
          )
    }
