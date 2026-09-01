package io.worxbend.tui.widgets

import io.worxbend.tui.core.Constraint

/** How a table decides its column constraints when the caller gave none.
  *
  * Shared by [[Table]] and [[DataTable]] so the two cannot disagree about what an empty `widths` means.
  *
  * The rule is the one the reference implementations use: no constraints at all means "divide the area equally between
  * however many columns the data has". Before this existed, an empty `widths` reached `Layout.split`, which returns no
  * segments for no constraints, and the table drew a blank rectangle — a silent nothing rather than a visible mistake,
  * and the DSL's `table(rows)` (whose widths are a varargs list that is allowed to be empty) made that a one-word call.
  */
private[widgets] object TableColumns:

  /** `widths` when the caller supplied any, otherwise one equal-share column per column present in `cellCounts`.
    *
    * `cellCounts` is the number of cells in each row the table is about to draw — the caller passes an iterator so that
    * the data is walked only in the fallback case, and only over rows that fit on screen. Zero columns yields no
    * constraints, which renders nothing, because there is nothing to render.
    *
    * `Fill(1)` per column rather than a precomputed `Length(width / count)`: the layout solver already divides the
    * leftover space between equal `Fill` weights, and letting it do the division means the columns stay equal after
    * `columnSpacing` has been deducted, which a division done here would have to repeat.
    */
  def resolve(widths: Seq[Constraint], cellCounts: => Iterator[Int]): Seq[Constraint] =
    if widths.nonEmpty then widths
    else Seq.fill(cellCounts.foldLeft(0)(math.max))(Constraint.Fill(1))
