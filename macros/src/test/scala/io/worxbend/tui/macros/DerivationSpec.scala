package io.worxbend.tui.macros

import org.scalatest.funsuite.AnyFunSuite

/** Top level so the snippet compiled by `typeCheckErrors` can name it. `shippedOn` is a `java.time.LocalDate`, a type
  * that brings no `FormFieldType` of its own, so the derivation must refuse it.
  */
private final case class Parcel(label: String, shippedOn: java.time.LocalDate)

/** A domain type of the application's own, with its own instance next to it — the extension point that keeps the set of
  * derivable types open. `deriveForm` has no branch for `Email`; it finds this given by implicit search.
  */
opaque type Email = String

object Email:
  def from(raw: String): Either[String, Email] =
    if raw.contains("@") then Right(raw.trim) else Left(s"'$raw' is not an email address")

  given FormFieldType[Email] = FormFieldType(FieldInput.TextField)(from)

/** Forty fields is not an interesting number in itself — it is comfortably past the 32-frame `-Xmax-inlines` budget
  * that the previous per-field `inline` recursion spent one frame of per field. Deriving this pins the ceiling as gone;
  * an edit that reintroduces recursive inlining fails here instead of failing in a user's own project with a compiler
  * message that names nothing of this library.
  */
private final case class Wide(
    f1: String,
    f2: String,
    f3: String,
    f4: String,
    f5: String,
    f6: String,
    f7: String,
    f8: String,
    f9: String,
    f10: String,
    f11: String,
    f12: String,
    f13: String,
    f14: String,
    f15: String,
    f16: String,
    f17: String,
    f18: String,
    f19: String,
    f20: String,
    f21: String,
    f22: String,
    f23: String,
    f24: String,
    f25: String,
    f26: String,
    f27: String,
    f28: String,
    f29: String,
    f30: String,
    f31: String,
    f32: String,
    f33: String,
    f34: String,
    f35: String,
    f36: String,
    f37: String,
    f38: String,
    f39: String,
    f40: String,
)

final class DerivationSpec extends AnyFunSuite:

  private final case class Signup(username: String, age: Int, subscribe: Boolean)

  private final case class Measurement(label: String, weight: Double, note: Option[String], count: Option[Int])

  private final case class Contact(email: Email)

  test("deriveForm reads field names and input kinds from the case class"):
    val spec = deriveForm[Signup]
    assert(
      spec.fields == Seq(
        FieldSpec("username", FieldInput.TextField),
        FieldSpec("age", FieldInput.IntField),
        FieldSpec("subscribe", FieldInput.BoolField),
      )
    )

  test("assemble rebuilds the case class from submitted values"):
    val spec = deriveForm[Signup]
    assert(spec.assemble(Seq("ada", 36, true)) == Signup("ada", 36, true))

  test("assemble rejects the wrong number of values by naming the fields it wanted"):
    val spec    = deriveForm[Signup]
    val failure = intercept[IllegalArgumentException](spec.assemble(Seq("ada", 36)))
    assert(failure.getMessage.contains("username, age, subscribe"))

  test("a derived field parses with the parser its type supplies"):
    val spec = deriveForm[Signup]
    assert(spec.defaults.map(_.parse("42")) == Seq(Right("42"), Right(42), Left("'42' is not true/false")))

  test("deriveForm refuses a field type with no FormFieldType and says how to add one"):
    val errors  = scala.compiletime.testing.typeCheckErrors("deriveForm[Parcel]")
    val message = errors.map(_.message).mkString("; ")
    assert(message.contains("LocalDate"), message)
    assert(message.contains("FormFieldType"), s"the message should say what to define: $message")

  test("a wide case class derives — no inline-depth ceiling"):
    val spec = deriveForm[Wide]
    assert(spec.fields.sizeIs == 40)
    assert(spec.fields.map(_.name).head == "f1" && spec.fields.map(_.name).last == "f40")
    assert(spec.assemble(Seq.tabulate(40)(index => s"v$index")).f40 == "v39")

  test("Double fields derive as a decimal control and parse as numbers"):
    val spec = deriveForm[Measurement]
    assert(
      spec.fields.map(_.input) == Seq(FieldInput.TextField, FieldInput.DecimalField) ++
        Seq(FieldInput.TextField, FieldInput.IntField)
    )
    assert(spec.defaults(1).parse("2.5") == Right(2.5))
    assert(spec.defaults(1).parse("wide").isLeft)

  test("an Option field takes the control of the type inside it and reads blank as None"):
    val spec = deriveForm[Measurement]
    assert(spec.defaults(2).parse("") == Right(None))
    assert(spec.defaults(3).parse("   ") == Right(None))
    assert(spec.defaults(3).parse("7") == Right(Some(7)))
    assert(spec.defaults(3).parse("seven") == Left("'seven' is not a whole number"))
    assert(spec.assemble(Seq("crate", 1.5, None, Some(3))) == Measurement("crate", 1.5, None, Some(3)))

  test("a type of the application's own derives through the given next to it"):
    val spec = deriveForm[Contact]
    assert(spec.fields == Seq(FieldSpec("email", FieldInput.TextField)))
    assert(spec.defaults.head.parse(" ada@example.com ") == Right("ada@example.com"))
    assert(spec.defaults.head.parse("ada") == Left("'ada' is not an email address"))

  test("Field.int rejects non-numeric input with a message"):
    val field = Field.int("age")
    assert(field.parse("42") == Right(42))
    assert(field.parse("nope").isLeft)

  test("Field.double accepts a decimal point"):
    val field = Field.double("weight")
    assert(field.spec == FieldSpec("weight", FieldInput.DecimalField))
    assert(field.parse("2.5") == Right(2.5))
    assert(field.parse("2,5") == Left("'2,5' is not a number"))

  test("mapValidated composes lazily onto the parse result"):
    val adult = Field.int("age").mapValidated(age => if age >= 18 then Right(age) else Left("must be 18+"))
    assert(adult.parse("30") == Right(30))
    assert(adult.parse("12") == Left("must be 18+"))
    assert(adult.parse("x").isLeft)

  test("map transforms a valid value"):
    val upper = Field.text("name").map(_.toUpperCase)
    assert(upper.parse("ada") == Right("ADA"))
