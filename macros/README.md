# tui-macros

Compile-time codegen: everywhere the framework bridges *user-defined*
code, the bridge is generated at compile time — never runtime reflection. This is the
constraint that keeps GraalVM native-image builds free of reflect-config JSON.

- **`deriveForm[A]`** — derives a `FormSpec[A]` from a case class via
  `Mirror.ProductOf` (`inline`, stdlib-only): field names become `FieldSpec`s, field
  types choose the input kind (`String`/`Int`/`Boolean`); anything else is a compile
  error.
- **`bindAction[A](handler)`** — wraps a function as a named `ActionHandler[A]` an
  application can pass around; the result is an ordinary function value, so nothing
  reflective is generated for it.
- **`FormSpec` / `FieldSpec` / `FieldInput`** — owned here so `tui-dsl` can consume
  them without a circular dependency.
- **`Field[A]`** — cue4s-style lazily-composed parsing/validation:
  `Field.int("age").mapValidated(a => if a >= 18 then Right(a) else Left("must be 18+"))`.

CI enforces the zero-reflection rule with a grep over all main sources
(`.github/workflows/ci.yml`).
