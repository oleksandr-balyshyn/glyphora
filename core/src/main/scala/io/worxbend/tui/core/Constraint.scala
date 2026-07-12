package io.worxbend.tui.core

/** How much of a split axis one segment claims (see [[Layout.split]]).
  *
  * `Length`/`Percentage`/`Ratio` are fixed demands; `Min` is a floor that also competes for leftover space; `Max` is a
  * cap that only takes leftover space; `Fill` divides leftover space by weight.
  */
enum Constraint:
  case Length(cells: Int)
  case Percentage(pct: Int)
  case Ratio(numerator: Int, denominator: Int)
  case Min(cells: Int)
  case Max(cells: Int)
  case Fill(weight: Int = 1)

object Constraint:
  def fill: Constraint = Fill(1)
