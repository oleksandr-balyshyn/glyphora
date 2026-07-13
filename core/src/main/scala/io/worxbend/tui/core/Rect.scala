package io.worxbend.tui.core

/** A rectangular region of the terminal in absolute coordinates.
  *
  * `x`/`y` locate the top-left corner; the region spans `width` columns and `height` rows. The right and bottom edges
  * (`x + width`, `y + height`) are exclusive.
  */
final case class Rect(x: Int, y: Int, width: Int, height: Int):

  def area: Int = width * height

  def isEmpty: Boolean = width == 0 || height == 0

  /** Exclusive right edge. */
  def right: Int = x + width

  /** Exclusive bottom edge. */
  def bottom: Int = y + height

  /** The overlapping region of the two rectangles; a zero-sized `Rect` when they do not overlap. */
  def intersection(other: Rect): Rect =
    val left          = math.max(x, other.x)
    val top           = math.max(y, other.y)
    val overlapRight  = math.min(right, other.right)
    val overlapBottom = math.min(bottom, other.bottom)
    if overlapRight <= left || overlapBottom <= top then Rect(left, top, 0, 0)
    else Rect(left, top, overlapRight - left, overlapBottom - top)

  def contains(pos: Position): Boolean =
    pos.x >= x && pos.x < right && pos.y >= y && pos.y < bottom

  /** This rectangle shrunk by `margin` cells on every side; zero-sized when the margin exhausts it. */
  def inset(margin: Int): Rect =
    val shrunkWidth  = math.max(0, width - 2 * margin)
    val shrunkHeight = math.max(0, height - 2 * margin)
    if shrunkWidth == 0 || shrunkHeight == 0 then Rect(x + width / 2, y + height / 2, 0, 0)
    else Rect(x + margin, y + margin, shrunkWidth, shrunkHeight)

object Rect:
  val Zero: Rect = Rect(0, 0, 0, 0)

  def apply(size: Size): Rect = Rect(0, 0, size.width, size.height)
