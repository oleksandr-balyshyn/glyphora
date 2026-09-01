package io.worxbend.tui.core

/** A terminal extent in cells: `width` columns by `height` rows. */
final case class Size(width: Int, height: Int)

object Size:
  /** A zero extent: no columns and no rows. The mirror of [[Rect.Zero]], so call sites stop spelling `Size(0, 0)`. */
  val Zero: Size = Size(0, 0)

  /** The extent of `rect`, discarding where it sits. The inverse direction of `Rect.apply(size)`. */
  def apply(rect: Rect): Size = Size(rect.width, rect.height)
