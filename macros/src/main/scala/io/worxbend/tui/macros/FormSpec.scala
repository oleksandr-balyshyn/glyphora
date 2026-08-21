package io.worxbend.tui.macros

/** What kind of input control a form field needs — derived from the field's type. */
enum FieldInput:
  case TextField
  case IntField
  case BoolField

/** One field of a derived form: the case-class field name and its input kind. */
final case class FieldSpec(name: String, input: FieldInput)

/** A compile-time-derived description of a form for `A`: the fields to render and how to assemble the submitted values
  * back into an `A`. Produced by [[deriveForm]]; owned by `tui-macros` so `tui-dsl` can consume it without a circular
  * dependency.
  *
  * `assemble` is the one untyped seam in this module, and the compiler cannot check its argument for you. Whoever calls
  * it must pass a `Seq[Any]` that:
  *
  *   - has exactly `fields.size` elements — no field omitted, none added;
  *   - is in the same order as `fields`, which is the case class's declaration order;
  *   - holds, at every position, a value that is already of the corresponding case-class field's declared type (a
  *     `String` where the class declares `String`, a boxed `Int` where it declares `Int`, and so on).
  *
  * Breaking any of those is a programmer error rather than something a user can trigger. A wrong number of values is
  * rejected up front with an `IllegalArgumentException` naming the fields that were expected. A value of the wrong type
  * in the right position cannot be detected here — it surfaces later as a `ClassCastException` thrown from the
  * `Mirror.fromProduct` call that builds the `A`.
  *
  * @param fields
  *   the derived fields, in the case class's declaration order
  * @param assemble
  *   builds an `A` from one value per field, subject to the contract above
  */
final case class FormSpec[A](fields: Seq[FieldSpec], assemble: Seq[Any] => A)

/** A form field with parsing and validation, composing cue4s-style: transforms are stored lazily and run when the
  * field's raw input is submitted.
  */
final case class Field[A](spec: FieldSpec, parse: String => Either[String, A]):

  /** Runs `f` on every successfully parsed value, leaving parse failures untouched.
    *
    * Beware the type change when the result is used as a validator for a derived [[FormSpec]]: the assembled values are
    * handed to the case class's constructor without any type check, so `B` must still be the declared type of that
    * field. `Field.text("name").map(_.length)` compiles happily and then fails on submit with a `ClassCastException`
    * that points at the constructor rather than at this call. Use `map` to normalise a value (trim it, lower-case it),
    * not to change what it is.
    */
  def map[B](f: A => B): Field[B] =
    mapValidated(value => Right(f(value)))

  /** Chains a validation step onto the parser: `f` may reject a parsed value by returning `Left(message)`, and that
    * message is what the form shows next to the field.
    *
    * The same caveat as [[map]] applies — when this field validates a derived [[FormSpec]], `B` must remain the case
    * class's declared field type. `mapValidated` is for rejecting values, not for changing their type.
    */
  def mapValidated[B](f: A => Either[String, B]): Field[B] =
    Field(spec, raw => parse(raw).flatMap(f))

object Field:

  /** The parser a derived field gets when the application supplies no validator of its own.
    *
    * This is the other half of the mapping [[deriveForm]] performs: the derivation turns a case-class field's *type*
    * into a [[FieldInput]], and this turns that `FieldInput` back into the [[Field]] that parses the raw input for it.
    * Both halves live in `tui-macros` so that adding a fourth input kind is one exhaustive match away from a compile
    * error here, instead of a silently missing branch in whichever module renders the form.
    *
    * Pure and thread-agnostic: it allocates a `Field` and touches no shared state, so it is safe to call from any
    * thread, including the render thread.
    *
    * @param spec
    *   the derived field to build a parser for; its name is carried over unchanged
    * @return
    *   a `Field[String]`, `Field[Int]` or `Field[Boolean]` according to `spec.input`
    */
  def default(spec: FieldSpec): Field[?] =
    spec.input match
      case FieldInput.TextField => text(spec.name)
      case FieldInput.IntField  => int(spec.name)
      case FieldInput.BoolField => bool(spec.name)

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
