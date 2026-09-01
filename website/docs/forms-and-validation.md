---
title: Forms & validation
description: Derive reflection-free forms from Scala case classes, add validators, submit values, and improve accessible output.
---

# Forms & validation

glyphora can derive a live form from a Scala 3 case class at compile time. Each field's
type becomes a terminal control; submission parses every value, publishes inline errors,
and assembles the case class only when all fields are valid.

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

## Which field types derive

| Field type | Control | Blank input |
| --- | --- | --- |
| `String` | text input | the empty string |
| `Int` | number input — non-digits are refused as you type | a parse error |
| `Double` | number input that also takes one decimal point | a parse error |
| `Long` | the same whole-number input as `Int`, without `Int`'s range limit | a parse error |
| `BigDecimal` | the same decimal input as `Double`, kept exact rather than rounded to binary | a parse error |
| `Boolean` | checkbox | — |
| `java.util.UUID` | text input; the usual hyphenated form, `123e4567-e89b-12d3-a456-426614174000` | a parse error |
| `java.time.LocalDate` | text input; ISO-8601 `YYYY-MM-DD`, for example `2026-09-01` | a parse error |
| `java.time.LocalTime` | text input; ISO-8601 `HH:MM` or `HH:MM:SS` | a parse error |
| `java.time.LocalDateTime` | text input; the two joined by a literal `T`, `2026-09-01T14:30` | a parse error |
| `java.time.Duration` | text input; ISO-8601 duration, `PT5M30S` is five minutes thirty seconds | a parse error |
| `Option[A]` | the same control `A` would get | `None` |
| an enum of parameterless cases | a picklist, once it opts in (below) | — |

The date, time and identifier types render as a plain text field because this library has
no calendar picker or masked entry yet. Their text is trimmed before parsing, so a stray
leading space is not an error, and a value the parser cannot read comes back as the
message shown next to the field — never as an exception, which on the render thread would
end the application rather than let the user correct the typo.

Anything else — a `java.net.URI`, a nested case class, a domain type of your own — is a
compile error rather than a runtime surprise:

```
deriveForm: no form control is defined for a field of type java.net.URI. Out of the box
String, Int, Long, Double, BigDecimal, Boolean, java.util.UUID,
java.time.LocalDate/LocalTime/LocalDateTime/Duration and Option of those are supported;
define a `given FormFieldType[java.net.URI]` next to your own type to teach the
derivation about it.
```

One consequence worth knowing when you write a validator by hand: `Form` catches "this
validator was built for the wrong type" by comparing which control the field wants, and
several types deliberately share one. `Field.int` and `Field.long` are both the
whole-number control; `Field.double` and `Field.bigDecimal` are both the decimal one;
`Field.text`, `Field.uuid`, `Field.localDate`, `Field.localTime`, `Field.localDateTime`
and `Field.duration` are all the plain text one. Attaching a `Field.int("seats")`
validator to a field the case class declares as `Long` therefore slips past that check
and fails on submit instead. Name the factory after the field's declared type, not after
the control it happens to look like.

## Teach the derivation about your own type

The list above is not a fixed set the library owns; it is just the instances that ship
with it. A `FormFieldType[A]` says two things — which control the field is edited with,
and how the typed text becomes an `A` (or a message to show the user). Put one in your
type's companion object and it derives like any built-in:

```scala
import io.worxbend.tui.macros.{FieldInput, FormFieldType}

opaque type Email = String

object Email:
  def from(raw: String): Either[String, Email] =
    if raw.contains("@") then Right(raw.trim) else Left(s"'$raw' is not an email address")

  given FormFieldType[Email] = FormFieldType(FieldInput.TextField)(Email.from)

final case class Contact(email: Email, cc: Option[Email])
// deriveForm[Contact] now compiles; `cc` accepts blank as None
```

`parse` runs on the render thread when the user submits, so keep it pure and quick — no
network calls, no locks.

### An enum becomes a picklist

An enum whose cases all take no parameters is the most natural choice field there is, and
one line in its companion turns it into one:

```scala
import io.worxbend.tui.macros.FormFieldType

enum Environment:
  case Development, Staging, Production

object Environment:
  given FormFieldType[Environment] = FormFieldType.ofEnum[Environment]

final case class Deployment(service: String, environment: Environment)
// deriveForm[Deployment] now compiles; `environment` renders as a one-row cycler
```

`FormFieldType.ofEnum` reads the case names out of the type and collects the case values
by implicit search, both at compile time, so nothing about this reads a class at runtime
and a native image needs no reflection configuration for it. Every case's name becomes one
option; the label the user chose is matched back to the case, ignoring surrounding
whitespace and letter case. A case that takes parameters has no singleton value, so it
fails to compile naming that case rather than producing a picklist that cannot represent
it.

It is opt-in rather than automatic on purpose. An unconditional given for every
`Mirror.SumOf` would collide with the one for `Option`, which is a sum type too, and would
also quietly claim every sealed hierarchy whose cases happen to take no parameters —
including ones that are not a choice a user should be offered.

`FormFieldType.ofLabels` is the same thing with labels you choose, for when the case names
are not what the user should read:

```scala
given FormFieldType[Tier] = FormFieldType.ofLabels(Seq("no charge" -> Tier.Free, "billed" -> Tier.Paid))
```

A picklist always has something showing, so unlike a text field there is no "nothing
entered" state: an untouched form submits the first option. Validate it like any other
field, with `Field.enumeration`:

```scala
FormState.of(
  deriveForm[Deployment],
  Field.enumeration[Environment]("environment").mapValidated {
    case Environment.Production => Left("production needs an approval")
    case other                  => Right(other)
  },
)
```

## Render and submit

```scala
def view(using ReactiveScope, Theme): Element =
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

A `FormFieldType` covers a field the user types into. A layout the derivation cannot
express — a nested case class, a radio group over an enum, a suggestion list that queries
a service as you type — is still an ordinary column of controls:

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
