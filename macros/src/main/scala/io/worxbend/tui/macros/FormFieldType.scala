package io.worxbend.tui.macros

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime}
import java.util.UUID

import scala.annotation.implicitNotFound
import scala.util.Try

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
  "deriveForm: no form control is defined for a field of type ${A}. Out of the box String, Int, Long, Double, " +
    "BigDecimal, Boolean, java.util.UUID, java.time.LocalDate/LocalTime/LocalDateTime/Duration and Option of those " +
    "are supported; define a `given FormFieldType[${A}]` next to your own type to teach the derivation about it."
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

  /** Builds an instance around a parser that reports a bad value by *throwing* — which is how every parser in the JDK's
    * `java.time` and `java.util.UUID` reports one.
    *
    * The exception is caught here and turned into the `Left` message the form shows next to the field. That conversion
    * is not a stylistic preference: `parse` is called on the render thread when the user submits, so an escaping
    * `DateTimeParseException` would tear down the render loop and end the application, where a `Left` is a line of red
    * text the user can correct. The raw text is trimmed before parsing, matching the built-in `Int`/`Double` instances,
    * so a value the user typed with a stray leading space is still accepted.
    *
    * @param control
    *   the control the field is edited with
    * @param parser
    *   turns already-trimmed text into a value, or throws
    * @param message
    *   builds the message for the *untrimmed* text the user actually typed
    */
  private def catching[A](control: FieldInput)(parser: String => A)(message: String => String): FormFieldType[A] =
    apply(control)(raw => Try(parser(raw.trim)).toOption.toRight(message(raw)))

  /** A whole number too large for `Int`. It shares the whole-number control with `Int`, because the two are the same
    * *kind* of entry to the person filling the form; only the range differs.
    */
  given long: FormFieldType[Long] =
    apply(FieldInput.IntField)(raw => raw.trim.toLongOption.toRight(s"'$raw' is not a whole number"))

  /** An exact decimal — money, quantities — where `Double`'s binary rounding would be wrong. Same decimal control as
    * `Double`.
    */
  given bigDecimal: FormFieldType[BigDecimal] =
    catching(FieldInput.DecimalField)(BigDecimal(_))(raw => s"'$raw' is not a number")

  /** A universally unique identifier, typed in its usual hyphenated form, for example
    * `123e4567-e89b-12d3-a456-426614174000`.
    */
  given uuid: FormFieldType[UUID] =
    catching(FieldInput.TextField)(UUID.fromString)(raw => s"'$raw' is not a UUID")

  /** A calendar date in ISO-8601 form, `YYYY-MM-DD`. It renders as a plain text field because this library has no
    * calendar picker yet; when one arrives it becomes a new [[FieldInput]] case and this instance changes with it.
    */
  given localDate: FormFieldType[LocalDate] =
    catching(FieldInput.TextField)(LocalDate.parse)(raw => s"'$raw' is not a date (YYYY-MM-DD)")

  /** A time of day in ISO-8601 form, `HH:MM` or `HH:MM:SS`. Text field, for the same reason as [[localDate]]. */
  given localTime: FormFieldType[LocalTime] =
    catching(FieldInput.TextField)(LocalTime.parse)(raw => s"'$raw' is not a time (HH:MM)")

  /** A date and time in ISO-8601 form, the two joined by a literal `T`: `2026-09-01T14:30`. */
  given localDateTime: FormFieldType[LocalDateTime] =
    catching(FieldInput.TextField)(LocalDateTime.parse)(raw => s"'$raw' is not a date and time (YYYY-MM-DDTHH:MM)")

  /** A length of time in ISO-8601 duration form: `PT5M30S` is five minutes and thirty seconds, `PT2H` is two hours. */
  given duration: FormFieldType[Duration] =
    catching(FieldInput.TextField)(Duration.parse)(raw => s"'$raw' is not a duration (ISO-8601, e.g. PT5M30S)")

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
