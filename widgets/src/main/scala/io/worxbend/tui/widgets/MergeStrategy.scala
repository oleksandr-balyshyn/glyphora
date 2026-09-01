package io.worxbend.tui.widgets

/** What a border cell does when it lands on a cell that already holds a box-drawing glyph.
  *
  * Two panels laid side by side share a column: the left panel draws its right wall there, and the right panel then
  * draws its left wall over the top. With `Replace` the second write simply wins and the result is one wall — but the
  * corners do not join, so the seam reads as `┐┌` stacked over `┘└` rather than as `┬` over `┴`.
  *
  * The other two strategies decode the glyph already in the buffer and the one being written into their four arms (the
  * line weight running right, up, left and down out of the cell), combine the arms, and look the combined shape back
  * up:
  *
  *   - `Exact` writes the combined glyph when Unicode has one, and otherwise leaves the incoming glyph in place;
  *   - `Fuzzy` does the same, but when no combined glyph exists it retries with every double-line arm weakened to a
  *     single line, because Unicode has no glyph for a double line meeting a heavy one. A double-walled panel touching
  *     a thick-walled one then still joins, at the cost of drawing the joint one weight lighter.
  *
  * `Replace` is the default everywhere, so a frame drawn without asking for merging is unchanged.
  */
enum MergeStrategy:
  case Replace, Exact, Fuzzy
