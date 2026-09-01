package io.worxbend.tui.dsl

import io.worxbend.tui.macros.{deriveForm, Field}

import org.scalatest.funsuite.AnyFunSuite

/** A top-level case class so `deriveForm` can summon its `Mirror`. */
final case class Registration(email: String, age: Int, terms: Boolean)

/** `FormState.of` matches validators to derived fields by name, which is the same silent-no-match hazard that made
  * `binding("+", …)` throw: a name that matches nothing used to be dropped without a word, and the form then submitted
  * completely unvalidated data while looking like it had passed. These pin the loud failures instead.
  */
final class FormValidatorWiringSpec extends AnyFunSuite:

  private val spec = deriveForm[Registration]

  test("a validator naming a field the form does not declare is rejected at construction"):
    val thrown = intercept[IllegalArgumentException]:
      FormState.of(spec, Field.text("emial").mapValidated(_ => Left("never runs")))
    assert(thrown.getMessage.contains("emial"))
    assert(thrown.getMessage.contains("email"), "the message should list the names that would have worked")

  /** Case and padding are part of the name: neither is normalized, so neither may be silently forgiven. */
  test("a validator whose name differs only in case or padding is rejected too"):
    assert(intercept[IllegalArgumentException](FormState.of(spec, Field.text("Email"))).getMessage.contains("Email"))
    assert(intercept[IllegalArgumentException](FormState.of(spec, Field.text("email "))).getMessage.contains("email"))

  test("two validators for the same field are rejected rather than silently last-wins"):
    val thrown = intercept[IllegalArgumentException]:
      FormState.of(
        spec,
        Field.text("email").mapValidated(_ => Left("first")),
        Field.text("email").mapValidated(_ => Left("second")),
      )
    assert(thrown.getMessage.contains("email"))

  /** A validator built from the wrong factory used to bind as though it fitted, and then handed a `String` into the
    * `Int` position of the case class's constructor. The failure surfaced as a `ClassCastException` out of
    * `Mirror.fromProduct` on the render thread, at the moment the user pressed submit — a whole application away from
    * the line that caused it. `FieldSpec.input` records which factory made a field, so the mismatch is now a
    * declaration-time error.
    */
  test("a validator built from the wrong factory for the field's type is rejected at construction"):
    val thrown = intercept[IllegalArgumentException](FormState.of(spec, Field.text("age")))
    assert(thrown.getMessage.contains("age"), s"the message should name the field: ${thrown.getMessage}")
    assert(thrown.getMessage.contains("IntField"), "it should say what the form declares")
    assert(thrown.getMessage.contains("TextField"), "and what the validator produces")
    assert(thrown.getMessage.contains("""Field.int("age")"""), "and what to write instead")

  test("a text validator on a boolean field, and a boolean one on a text field, are both rejected"):
    assert(intercept[IllegalArgumentException](FormState.of(spec, Field.text("terms"))).getMessage.contains("terms"))
    assert(intercept[IllegalArgumentException](FormState.of(spec, Field.bool("email"))).getMessage.contains("email"))

  /** The one case the guard cannot see: `map` keeps the spec it was called on, so this still claims to be an `IntField`
    * while producing a `String`. Asserting on the `ClassCastException` it eventually raises would pin
    * `Mirror.fromProduct`'s wording rather than anything this library promises, so it is left untested on purpose —
    * `Field.map`'s Scaladoc is where the caveat lives.
    */
  test("a validator that changes the value type without changing the factory still builds"):
    val state = FormState.of(spec, Field.int("age").map(_.toString))
    assert(state.bindings.sizeIs == 3)

  test("a correctly named validator still runs"):
    val state =
      FormState.of(spec, Field.text("email").mapValidated(v => if v.contains("@") then Right(v) else Left("no @")))
    state.submit()
    assert(state.errors.peek.get("email").contains("no @"))
    assert(state.result.peek.isEmpty)

  /** `Field.bool` was accepted by `of` and then never consulted, so a "must accept the terms" checkbox validator never
    * ran and the form submitted as valid with the box unticked.
    */
  test("a boolean field's validator runs on submit"):
    val state = FormState.of(
      spec,
      Field.text("email").map(identity),
      Field.int("age").map(identity),
      Field.bool("terms").mapValidated(accepted => if accepted then Right(accepted) else Left("you must accept")),
    )
    state.submit()
    assert(state.errors.peek.get("terms").contains("you must accept"), "an unticked required checkbox must fail")
    assert(state.result.peek.isEmpty)

  test("a boolean field with a validator passes once it is ticked"):
    val state = FormState.of(
      spec,
      Field.bool("terms").mapValidated(accepted => if accepted then Right(accepted) else Left("you must accept")),
    )
    state.bindings.foreach:
      case bound: FieldBinding.BoolLike                                          => bound.value.set(true)
      case FieldBinding.TextLike(fieldSpec, input, _) if fieldSpec.name == "age" => input.insert("30")
      case FieldBinding.TextLike(_, input, _)                                    => input.insert("someone@example.com")
      case _: FieldBinding.SelectLike                                            => () // this form declares no picklist
    state.submit()
    assert(state.errors.peek.isEmpty, s"unexpected errors: ${state.errors.peek}")
    assert(state.result.peek.contains(Registration("someone@example.com", 30, true)))

  /** With no validator at all a boolean field must keep submitting its raw value — the default may not become a parse
    * that can fail.
    */
  test("a boolean field with no validator submits its value unchanged"):
    val state = FormState.of(spec)
    state.submit()
    assert(state.errors.peek.get("terms").isEmpty)
