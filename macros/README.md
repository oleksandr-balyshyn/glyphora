# tui-macros

Compile-time codegen: everywhere the framework bridges *user-defined*
code, the bridge is generated at compile time — never runtime reflection. This is the
constraint that keeps GraalVM native-image builds free of reflect-config JSON.

- **`deriveForm[A]`** — derives a `FormSpec[A]` from a case class via
  `Mirror.ProductOf` (`inline`, stdlib-only): field names become `FieldSpec`s, and each
  field's type contributes its control and its parser through the `FormFieldType`
  summoned for it. A field type with no instance in scope is a compile error.
- **`FormFieldType[A]`** — the one thing a type must supply to be derivable: which
  control to render and how to parse the text back. `String`, `Int`, `Double`,
  `Boolean` and `Option` of those come with the module; a type of your own joins by
  declaring `given FormFieldType[YourType]` in its companion.
- **`FormSpec` / `FieldSpec` / `FieldInput`** — owned here so `tui-dsl` can consume
  them without a circular dependency.
- **`Field[A]`** — cue4s-style lazily-composed parsing/validation:
  `Field.int("age").mapValidated(a => if a >= 18 then Right(a) else Left("must be 18+"))`.

CI enforces the zero-reflection rule with a grep over all main sources
(`.github/workflows/ci.yml`).
