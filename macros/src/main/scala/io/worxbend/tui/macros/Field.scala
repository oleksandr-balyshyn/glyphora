package io.worxbend.tui.macros

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime}
import java.util.UUID

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

/** The starting points for a validator. Each one is the field a derivation would have produced for that type, so
  * `Field.int("age").mapValidated(…)` and the derived `age` field parse identically until the extra step runs — both
  * come from the same [[FormFieldType]].
  *
  * A type of your own needs no factory here: `summon[FormFieldType[Email]].field("email")` builds its field from
  * whatever instance the type supplies.
  *
  * One limit is worth knowing before reaching for these on a derived form. `Form` catches "this validator was built for
  * the wrong type" by comparing the [[FieldSpec.input]] the factory stamped in, and several types deliberately share a
  * control: `int` and `long` are both the whole-number field, `double` and `bigDecimal` are both the decimal one, and
  * `text`, `uuid`, `localDate`, `localTime`, `localDateTime` and `duration` are all the plain text field. So attaching
  * a `Field.int("count")` validator to a field the case class declares as `Long` passes that check and instead fails on
  * submit with a `ClassCastException` from the case class's constructor — the same residual case [[Field.map]]
  * describes. Pick the factory named after the field's declared type, not after the control it happens to render as.
  */
object Field:

  def text(name: String): Field[String] = FormFieldType.text.field(name)

  def int(name: String): Field[Int] = FormFieldType.int.field(name)

  def double(name: String): Field[Double] = FormFieldType.decimal.field(name)

  def bool(name: String): Field[Boolean] = FormFieldType.bool.field(name)

  def long(name: String): Field[Long] = FormFieldType.long.field(name)

  def bigDecimal(name: String): Field[BigDecimal] = FormFieldType.bigDecimal.field(name)

  def uuid(name: String): Field[UUID] = FormFieldType.uuid.field(name)

  def localDate(name: String): Field[LocalDate] = FormFieldType.localDate.field(name)

  def localTime(name: String): Field[LocalTime] = FormFieldType.localTime.field(name)

  def localDateTime(name: String): Field[LocalDateTime] = FormFieldType.localDateTime.field(name)

  def duration(name: String): Field[Duration] = FormFieldType.duration.field(name)
