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

  /** `widths` when the caller supplied any, otherwise one equal-share column per column present in `cellCounts`, capped
    * at `maxColumns`.
    *
    * `cellCounts` is the number of cells in each row the table is about to draw — the caller passes an iterator so that
    * the data is walked only in the fallback case, and only over rows that fit on screen. Zero columns yields no
    * constraints, which renders nothing, because there is nothing to render.
    *
    * `Fill(1)` per column rather than a precomputed `Length(width / count)`: the layout solver already divides the
    * leftover space between equal `Fill` weights, and letting it do the division means the columns stay equal after
    * `columnSpacing` has been deducted, which a division done here would have to repeat.
    *
    * `maxColumns` — pass the area's width. `cellCounts` is a row's cells counted at [[TableCell.columnCount]]'s full
    * per-span width, which a caller controls directly and a single mistyped or adversarial `TableCell(_, columnSpan =
    * 1000000)` inflates arbitrarily; nothing upstream of this fallback clamps it, because [[Table.render]]'s own
    * per-cell span clamp only runs *after* this decides how many columns exist. A column narrower than one cell is
    * already useless — `Layout`'s `Fill` split gives the columns past the area's width nothing to divide — so capping
    * here at the one number that bounds anything worth allocating turns a caller's typo into a merely-too-narrow table
    * instead of a multi-gigabyte `Seq.fill`.
    */
  def resolve(widths: Seq[Constraint], cellCounts: => Iterator[Int], maxColumns: Int): Seq[Constraint] =
    if widths.nonEmpty then widths
    else Seq.fill(math.max(0, math.min(maxColumns, cellCounts.foldLeft(0)(math.max))))(Constraint.Fill(1))
