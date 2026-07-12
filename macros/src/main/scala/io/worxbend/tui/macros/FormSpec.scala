package io.worxbend.tui.macros

/** What kind of input control a form field needs — derived from the field's type. */
enum FieldInput:
  case TextField
  case IntField
  case BoolField

/** One field of a derived form: the case-class field name and its input kind. */
final case class FieldSpec(name: String, input: FieldInput)

/** A compile-time-derived description of a form for `A`: the fields to render and how to assemble the
  * submitted values back into an `A`. Produced by [[deriveForm]]; owned by `tui-macros` so `tui-dsl` can
  * consume it without a circular dependency (SPEC.md §6).
  */
final case class FormSpec[A](fields: Seq[FieldSpec], assemble: Seq[Any] => A)

/** A form field with parsing and validation, composing cue4s-style: transforms are stored lazily and run when
  * the field's raw input is submitted (`RESEARCH.md`, cue4s `Prompt.mapValidated`).
  */
final case class Field[A](spec: FieldSpec, parse: String => Either[String, A]):

  def map[B](f: A => B): Field[B] =
    mapValidated(value => Right(f(value)))

  def mapValidated[B](f: A => Either[String, B]): Field[B] =
    Field(spec, raw => parse(raw).flatMap(f))

object Field:

  def text(name: String): Field[String] =
    Field(FieldSpec(name, FieldInput.TextField), raw => Right(raw))

  def int(name: String): Field[Int] =
    Field(
      FieldSpec(name, FieldInput.IntField),
      raw => raw.trim.toIntOption.toRight(s"'$raw' is not a whole number"),
    )

  def bool(name: String): Field[Boolean] =
    Field(
      FieldSpec(name, FieldInput.BoolField),
      raw => raw.trim.toBooleanOption.toRight(s"'$raw' is not true/false"),
    )
