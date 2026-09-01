package io.worxbend.tui.core

/** An absolute terminal coordinate: `x` is the column, `y` the row, both zero-based. */
final case class Position(x: Int, y: Int)

object Position:
  /** The top-left cell of the terminal: column 0, row 0. */
  val Origin: Position = Position(0, 0)

  /** The top-left corner of `rect`; the same value as `rect.position`, spelled from the `Position` side. */
  def apply(rect: Rect): Position = Position(rect.x, rect.y)
