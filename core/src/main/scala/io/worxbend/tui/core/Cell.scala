package io.worxbend.tui.core

/** One rendered terminal cell.
  *
  * `symbol` is a `String`, not a `Char`, because a cell can hold a multi-codepoint grapheme cluster (combining
  * characters, emoji ZWJ sequences). A wide (two-column) grapheme lives in its left cell; the cell to its right is a
  * continuation filler that backends skip when flushing.
  */
final case class Cell(symbol: String, style: Style):
  def isBlank: Boolean = symbol.isEmpty || symbol == " "

object Cell:
  val Empty: Cell = Cell(" ", Style.Default)
