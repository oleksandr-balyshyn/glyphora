---
title: Forms & validation
description: Derive reflection-free forms from Scala case classes, add validators, submit values, and improve accessible output.
---

# Forms & validation

glyphora can derive a live form from a Scala 3 case class at compile time. String,
`Int`, and `Boolean` fields become terminal controls; submission parses every value,
publishes inline errors, and assembles the case class only when all fields are valid.

There is no runtime reflection, annotation scanner, or `reflect-config.json`.

## Derive a form

```scala
import io.worxbend.tui.dsl.*
import io.worxbend.tui.macros.{deriveForm, Field}

final case class Signup(
  username: String,
  age: Int,
  subscribe: Boolean,
)

private val signup = FormState.of(
  deriveForm[Signup],
  Field.text("username").mapValidated { name =>
    val clean = name.trim
    if clean.nonEmpty then Right(clean) else Left("required")
  },
  Field.int("age").mapValidated { age =>
    if age >= 18 then Right(age) else Left("must be 18 or older")
  },
)
```

`deriveForm[Signup]` produces field metadata and a direct constructor call during
compilation. Validators are matched by field name and replace the default parser for
that field.

**A name that matches nothing is a construction error, not a silent no-op.** A typo, a
case difference, or a renamed case-class field throws from `FormState.of` at
declaration time — the same way a malformed key spec throws from `binding`:

```scala
FormState.of(deriveForm[Signup], Field.text("userName"))
// java.lang.IllegalArgumentException: validator(s) for field(s) userName that the
// form does not declare; it declares username, age, subscribe
```

Failing here rather than at submit is the whole point: a dropped validator is
invisible at runtime, so the form would submit completely unvalidated data and look
like it had passed. Two validators for the same field are rejected for the same
reason, instead of silently last-wins.

**Building a validator from the wrong factory is a construction error too.** Each
`Field.*` factory stamps the kind of input it parses into its spec, so a `Field.text`
aimed at an `Int` field is caught where the form is declared:

```scala
FormState.of(deriveForm[Signup], Field.text("age"))
// java.lang.IllegalArgumentException: field 'age' is declared as IntField but its
// validator produces TextField; use Field.int("age")
```

Until this check existed, that validator bound as though it fitted and then handed a
`String` into the `Int` position of the case class's constructor. The failure arrived
as a `ClassCastException` from deep inside `Mirror.fromProduct`, on the render thread,
at the moment the user pressed submit — a whole application away from the line that
caused it.

One case slips past: `.map` keeps the spec it was called on, so
`Field.int("age").map(_.toString)` still claims to be an `IntField` while producing a
`String`. Use `.map` to normalise a value, not to change what it is.

Boolean fields are validated like any other — a `Field.bool` validator sees the
checkbox's own value:

```scala
Field.bool("subscribe").mapValidated { accepted =>
  if accepted then Right(accepted) else Left("you must accept the terms")
}
```

## Render and submit

```scala
def view(using ReactiveScope): Element =
  panel("Create account")(
    Form(signup),
    spacer(1),
    signup.result.get match
      case Some(value) => text(s"Welcome, ${value.username}!").fg(Color.Green)
      case None        => text("Tab next · Space toggle · Ctrl+S submit").dim,
  ).rounded.onKey(Key.CtrlS) {
    signup.submit()
  }
```

`FormState` exposes two signals:

- `errors: Signal[Map[String, String]]` contains validation failures by field name;
- `result: Signal[Option[A]]` contains the assembled value after a valid submit.

Calling `submit()` validates the entire form. On failure it replaces `errors` and
clears `result`; on success it clears errors and publishes the case class.

## Compose validators

`Field` parsers compose in a small, typed pipeline:

```scala
val port = Field
  .int("port")
  .mapValidated(value =>
    if value >= 1 && value <= 65535 then Right(value)
    else Left("must be between 1 and 65535")
  )

val slug = Field
  .text("project")
  .map(_.trim.toLowerCase)
  .mapValidated(value =>
    if value.matches("[a-z0-9-]+") then Right(value)
    else Left("use lowercase letters, numbers, and dashes")
  )
```

Use `.map` for an infallible transformation and `.mapValidated` when the transform
can return a user-facing error. Neither may change the *type* of a field validating a
derived form: the assembled values go to the case class's constructor unchecked, and
the declaration-time guard above sees the factory, not the transformed type.

## Accessible form output

Color should never be the only signal. `Form.accessible` renders the same state with
explicit position and status text:

```scala
val formView =
  if accessibleMode.get then Form.accessible(signup)
  else Form(signup)
```

The accessible variant announces `Field 1 of 3`, spells out checkbox state, and
prefixes failures with `Error:`. Pair it with a straightforward `Tab`/`Enter` key
flow and a high-contrast theme.

## Build a manual form when needed

Derivation intentionally covers a small, predictable type set. For dates, nested
objects, async suggestions, or custom domain types, compose controls directly:

```scala
import io.worxbend.tui.widgets.TextInputState

private val environment = Signal(0)
private val replicas = TextInputState("2")

column(
  radioGroup(Seq("staging", "production"), environment),
  numberInput(replicas),
  button("Deploy") { submitDeployment() },
).gap(1)
```

Manual forms follow the same ownership model: the application owns control state,
the widget renders it, and focused built-in handlers mutate it.

## Complete example

Run the repository example:

```bash
./mill examples.form-demo.run
```

Its source and headless test live under
[`examples/form-demo`](https://github.com/oleksandr-balyshyn/glyphora/tree/main/examples/form-demo).
For modals and multi-step flows, continue with [The app shell](./app-shell).
