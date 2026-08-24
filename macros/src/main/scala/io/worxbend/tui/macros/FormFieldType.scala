package io.worxbend.tui.macros

import scala.annotation.implicitNotFound

/** How one *type* becomes one form field: which control the user types into, and how the text they typed turns back
  * into a value of that type.
  *
  * [[deriveForm]] summons one of these per case-class field. That is the whole extension point: a type the library has
  * never heard of joins the derivation the moment a `given FormFieldType[ThatType]` is in its implicit scope — which
  * for a type you declared yourself means "next to it, in its companion object".
  *
  * {{{
  * opaque type Email = String
  *
  * object Email:
  *   def from(raw: String): Either[String, Email] =
  *     if raw.contains("@") then Right(raw) else Left(s"'\$raw' is not an email address")
  *
  *   given FormFieldType[Email] = FormFieldType(FieldInput.TextField)(Email.from)
  *
  * final case class Signup(email: Email, age: Int)
  * deriveForm[Signup] // compiles: Email brought its own instance
  * }}}
  *
  * Pure and thread-agnostic: `parse` reads its argument and allocates a result, touching no shared state, so it is safe
  * to call from any thread including the render thread. An implementation that blocks or mutates shared state breaks
  * that promise — the form calls `parse` on the render thread when the user submits.
  */
@implicitNotFound(
  "deriveForm: no form control is defined for a field of type ${A}. Out of the box String, Int, Double, Boolean " +
    "and Option of those are supported; define a `given FormFieldType[${A}]` next to your own type to teach the " +
    "derivation about it."
)
trait FormFieldType[A]:

  /** The control this type is edited with. */
  def input: FieldInput

  /** Turns the raw text of the control into an `A`, or into the message the form shows next to the field. */
  def parse(raw: String): Either[String, A]

  /** The parser a derived field gets when the application supplies no validator of its own.
    *
    * It lives here rather than in [[deriveForm]] because a `FormFieldType[?]` cannot be taken apart from the outside:
    * writing `Field(spec, control.parse)` against a wildcard instance loses the connection between the parser's result
    * type and the instance's own, and no longer type-checks.
    */
  final def field(name: String): Field[A] = Field(FieldSpec(name, input), parse)

object FormFieldType:

  /** Builds an instance from the control to render and the parser to run — the shape an application writes when
    * teaching the derivation about one of its own types.
    */
  def apply[A](control: FieldInput)(parser: String => Either[String, A]): FormFieldType[A] =
    new FormFieldType[A]:
      def input: FieldInput                     = control
      def parse(raw: String): Either[String, A] = parser(raw)

  given text: FormFieldType[String] = apply(FieldInput.TextField)(raw => Right(raw))

  given int: FormFieldType[Int] =
    apply(FieldInput.IntField)(raw => raw.trim.toIntOption.toRight(s"'$raw' is not a whole number"))

  given decimal: FormFieldType[Double] =
    apply(FieldInput.DecimalField)(raw => raw.trim.toDoubleOption.toRight(s"'$raw' is not a number"))

  given bool: FormFieldType[Boolean] =
    apply(FieldInput.BoolField)(raw => raw.trim.toBooleanOption.toRight(s"'$raw' is not true/false"))

  /** An optional field renders with the same control as the type inside it, and treats blank input as "not given"
    * rather than as a parse failure. Anything else is handed to the inner instance, so `Option[Int]` still rejects
    * `"abc"` with the message `Int` would have given.
    *
    * One combination is optional in name only: an `Option[Boolean]` renders as a checkbox, and a checkbox is always
    * either ticked or not, so the field always submits a `Some`. Declare a plain `Boolean` unless the `Option` means
    * something to the rest of your program.
    */
  given option[A](using inner: FormFieldType[A]): FormFieldType[Option[A]] =
    apply(inner.input)(raw => if raw.trim.isEmpty then Right(None) else inner.parse(raw).map(Some(_)))
