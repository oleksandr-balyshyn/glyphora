package io.worxbend.tui.widgets

import io.worxbend.tui.core.Style

/** One row of a [[Table]], with the vertical room it takes and the style it is drawn in.
  *
  * A [[Table]]'s `rows` accepts either a bare sequence of cells — one terminal line tall, which is all a row could ever
  * be before this type existed — or a `TableRow`, and the two may be mixed in one table. The union follows the shape
  * [[ListView]] already uses for its `String | Line` items: a caller who wants none of this pays no ceremony for it,
  * and every table written against the older signature still compiles and still draws the same thing.
  *
  * What the extra room buys is spacing a table could not express at all. `bottomMargin = 1` on the last header-like row
  * puts a blank line under it; `height = 2` gives a row breathing space in a sparse table; `topMargin` opens a gap
  * above a group of rows.
  *
  * @param cells
  *   the row's cells, laid out on the table's solved column widths. A cell is either a bare `Line`, which occupies one
  *   column, or a [[TableCell]], which can span several. Extra cells beyond the last column are dropped, as they always
  *   were.
  * @param height
  *   how many terminal lines the row occupies. A cell is a single line of text, so the cells are drawn on the *first*
  *   of those lines and the rest are left blank — this reserves vertical room, it does not wrap text. Clamped to at
  *   least one: a zero-height row would draw nothing while still being walked, which is exactly the shape that lets a
  *   ten-thousand-row table scan itself looking for something to show.
  * @param topMargin
  *   blank lines above the row. Negative counts clamp to zero.
  * @param bottomMargin
  *   blank lines below the row. Negative counts clamp to zero.
  * @param style
  *   the style this row is drawn in, layered over the table's own `style`, or `None` to use the table's style
  *   unchanged. This is the per-row level the reference implementations have between the table style and the cell
  *   style: before it, a table applied one style to every row and a caller who wanted to tint one of them — a failing
  *   check, a stale record — had to restyle every [[io.worxbend.tui.core.Span]] in it by hand.
  */
final case class TableRow(
    cells: Seq[TableCell.Source],
    height: Int = 1,
    topMargin: Int = 0,
    bottomMargin: Int = 0,
    style: Option[Style] = None,
):

  /** The row's height as the renderer spends it: at least one line for the content. */
  def contentHeight: Int = math.max(1, height)

  /** Every line the row occupies, margins included — what the render loop advances `y` by. */
  def totalHeight: Int = math.max(0, topMargin) + contentHeight + math.max(0, bottomMargin)

  /** How far below the row's first line its cells are drawn. */
  private[widgets] def contentOffset: Int = math.max(0, topMargin)

object TableRow:

  /** Either shape a [[Table]] accepts for one row. */
  type Source = Seq[TableCell.Source] | TableRow

  /** A bare sequence of cells read as the one-line, unmargined, unstyled row it has always meant. */
  def of(source: Source): TableRow =
    source match
      case row: TableRow                           => row
      case cells: Seq[TableCell.Source @unchecked] => TableRow(cells)

  /** The rows that fit in `lines`, normalised, walking no further into `sources` than it had to.
    *
    * Bounded on purpose: drawing twenty visible rows must not touch a ten-thousand-row sequence, and now that a row can
    * be taller than one line the bound is a running sum rather than a count. A row that would start past the bottom
    * edge is not taken at all; a row that starts inside the area but overruns it *is* taken, and the renderer clips it,
    * which is the library-wide silent-clipping rule.
    */
  private[widgets] def fitting(sources: Iterator[Source], lines: Int): Seq[TableRow] =
    val taken = Seq.newBuilder[TableRow]
    var used  = 0
    while used < lines && sources.hasNext do
      val row = of(sources.next())
      taken += row
      used += row.totalHeight
    taken.result()
