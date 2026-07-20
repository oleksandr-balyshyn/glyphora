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

## Render and submit

```scala
def view(using ReactiveScope): Element =
  panel("Create account")(
    Form(signup),
    spacer(1),
    signup.result.get match
      case Some(value) => text(s"Welcome, ${value.username}!").color(Color.Green)
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
can return a user-facing error.

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
