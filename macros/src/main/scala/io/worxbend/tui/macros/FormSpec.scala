package io.worxbend.tui.macros

/** What kind of input control a form field needs — derived from the field's type. */
enum FieldInput:
  case TextField
  case IntField
  case BoolField

/** One field of a derived form: the case-class field name and its input kind. */
final case class FieldSpec(name: String, input: FieldInput)

/** A compile-time-derived description of a form for `A`: the fields to render and how to assemble the submitted values
  * back into an `A`. Produced by [[deriveForm]]; owned by `tui-macros` so `tui-dsl` can consume it without a circular
  * dependency.
  *
  * `assemble` is the one untyped seam in this module, and the compiler cannot check its argument for you. Whoever calls
  * it must pass a `Seq[Any]` that:
  *
  *   - has exactly `fields.size` elements — no field omitted, none added;
  *   - is in the same order as `fields`, which is the case class's declaration order;
  *   - holds, at every position, a value that is already of the corresponding case-class field's declared type (a
  *     `String` where the class declares `String`, a boxed `Int` where it declares `Int`, and so on).
  *
  * Breaking any of those is a programmer error rather than something a user can trigger. A wrong number of values is
  * rejected up front with an `IllegalArgumentException` naming the fields that were expected. A value of the wrong type
  * in the right position cannot be detected here — it surfaces later as a `ClassCastException` thrown from the
  * `Mirror.fromProduct` call that builds the `A`.
  *
  * @param fields
  *   the derived fields, in the case class's declaration order
  * @param assemble
  *   builds an `A` from one value per field, subject to the contract above
  */
final case class FormSpec[A](fields: Seq[FieldSpec], assemble: Seq[Any] => A)

object FormSpec:

  /** Builds a [[FormSpec]] from already-derived `fields` and the `Mirror.fromProduct` of the target case class.
    *
    * This is a plain (non-inline) method on purpose. `deriveForm` is `inline`, so everything written in its body is
    * copied into every call site; keeping the arity check and its error message here means one copy of that code exists
    * for the whole program instead of one per `deriveForm` call. `ActionHandler.of` exists for the same reason.
    *
    * @param fields
    *   the derived fields, in the case class's declaration order
    * @param fromProduct
    *   the mirror's constructor: turns a tuple of field values into an `A`
    */
  def ofProduct[A](fields: Seq[FieldSpec], fromProduct: Product => A): FormSpec[A] =
    FormSpec(
      fields,
      values =>
        if values.sizeIs != fields.size then
          throw IllegalArgumentException(
            s"assembling a form value needs one value per field, in order — ${fields.size} " +
              s"(${fields.map(_.name).mkString(", ")}) — but got ${values.size}"
          )
        fromProduct(Tuple.fromArray(values.toArray)),
    )
