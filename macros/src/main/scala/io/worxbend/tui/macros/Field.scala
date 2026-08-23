package io.worxbend.tui.macros

/** A form field with parsing and validation, composing cue4s-style: transforms are stored lazily and run when the
  * field's raw input is submitted.
  */
final case class Field[A](spec: FieldSpec, parse: String => Either[String, A]):

  /** Runs `f` on every successfully parsed value, leaving parse failures untouched.
    *
    * Use `map` to normalise a value — trim it, lower-case it — not to change what it is. Building a validator from the
    * wrong factory for the field's declared type (a `Field.text` for an `Int` field) is now caught where the form is
    * declared, because [[FieldSpec.input]] records which factory made it. One case survives that check: `map` keeps the
    * spec it was called on, so `Field.int("age").map(_.toString)` still claims to be an `IntField` while producing a
    * `String`. The assembled values reach the case class's constructor with no type check, so that one fails on submit
    * with a `ClassCastException` pointing at the constructor rather than at this call.
    */
  def map[B](f: A => B): Field[B] =
    mapValidated(value => Right(f(value)))

  /** Chains a validation step onto the parser: `f` may reject a parsed value by returning `Left(message)`, and that
    * message is what the form shows next to the field.
    *
    * The same residual caveat as [[map]] applies — when this field validates a derived [[FormSpec]], `B` must remain
    * the case class's declared field type. `mapValidated` is for rejecting values, not for changing their type.
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
