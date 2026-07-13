package io.worxbend.tui.macros

import org.scalatest.funsuite.AnyFunSuite

final class DerivationSpec extends AnyFunSuite:

  private final case class Signup(username: String, age: Int, subscribe: Boolean)

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

  test("bindAction dispatches directly to the bound handler"):
    enum AppAction:
      case Increment, Reset
    var seen    = List.empty[AppAction]
    val handler = bindAction[AppAction](action => seen = action :: seen)
    handler.handle(AppAction.Increment)
    handler.handle(AppAction.Reset)
    assert(seen == List(AppAction.Reset, AppAction.Increment))

  test("Field.int rejects non-numeric input with a message"):
    val field = Field.int("age")
    assert(field.parse("42") == Right(42))
    assert(field.parse("nope").isLeft)

  test("mapValidated composes lazily onto the parse result"):
    val adult = Field.int("age").mapValidated(age => if age >= 18 then Right(age) else Left("must be 18+"))
    assert(adult.parse("30") == Right(30))
    assert(adult.parse("12") == Left("must be 18+"))
    assert(adult.parse("x").isLeft)

  test("map transforms a valid value"):
    val upper = Field.text("name").map(_.toUpperCase)
    assert(upper.parse("ada") == Right("ADA"))
