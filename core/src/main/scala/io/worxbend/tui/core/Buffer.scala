package io.worxbend.tui.core

/** A mutable grid of [[Cell]]s covering `area`, the in-memory render target for one frame.
  *
  * `x`/`y` arguments are absolute terminal coordinates (the same space as `area`'s offset), not area-relative — widgets
  * receive a `Rect` positioned in absolute space and write to the buffer at those coordinates. Writes outside `area`
  * are silently clipped, never errors; reads outside `area` return [[Cell.Empty]].
  *
  * Mutability is an implementation detail of the render loop — it never escapes through `Widget.render`'s contract.
  */
final class Buffer(val area: Rect):

  private val cells: Array[Cell] = Array.fill(area.area)(Cell.Empty)

  def get(x: Int, y: Int): Cell =
    if area.contains(Position(x, y)) then cells(indexOf(x, y)) else Cell.Empty

  def set(x: Int, y: Int, cell: Cell): Unit =
    if area.contains(Position(x, y)) then cells(indexOf(x, y)) = cell

  /** Writes `text` starting at `(x, y)`, one grapheme cluster per cell, clipping at the area's right edge.
    *
    * A wide (two-column) cluster occupies its cell plus a continuation cell to the right; a wide cluster that would
    * only half-fit at the right edge is dropped entirely. Grapheme clusters that begin with a combining mark (no base
    * character before them in `text`) are skipped.
    */
  def setString(x: Int, y: Int, text: String, style: Style): Unit =
    var column   = x
    val clusters = CharWidth.graphemeClusters(text)
    while clusters.hasNext && column < area.right do
      val cluster = clusters.next()
      val width   = CharWidth.of(cluster)
      if width > 0 && column + width <= area.right then
        set(column, y, Cell(cluster, style))
        if width == 2 then set(column + 1, y, Cell.Empty)
        column += width
      else if width > 0 then column = area.right // wide cluster that only half-fits: stop
    end while

  /** Copies `region` of `source` into this buffer with the region's top-left landing at `at`.
    *
    * Writes outside this buffer's area are clipped like any other write — this is how offscreen-rendered content
    * (scroll views, overlays) lands on the frame.
    */
  def blit(source: Buffer, at: Position, region: Rect): Unit =
    val clipped = region.intersection(source.area)
    var dy      = 0
    while dy < clipped.height do
      var dx = 0
      while dx < clipped.width do
        set(at.x + dx, at.y + dy, source.get(clipped.x + dx, clipped.y + dy))
        dx += 1
      dy += 1

  /** Copies all of `source` into this buffer at `at`. */
  def blit(source: Buffer, at: Position): Unit =
    blit(source, at, source.area)

  /** An independent copy of this buffer. Backends snapshot the frame they just flushed so later mutation of the
    * caller's buffer cannot corrupt the next diff.
    */
  def snapshot: Buffer =
    val copied = Buffer(area)
    Array.copy(cells, 0, copied.cells, 0, cells.length)
    copied

  /** Resets every cell to [[Cell.Empty]], recycling the buffer for the next frame. */
  def reset(): Unit =
    var index = 0
    while index < cells.length do
      cells(index) = Cell.Empty
      index += 1

  /** The cells that changed going from this buffer (the previous frame) to `next` (the frame to display).
    *
    * This is what a terminal backend flushes each frame instead of redrawing everything. Positions covered by the
    * continuation cell of a wide grapheme in `next` are never emitted — flushing the wide cell itself repaints both
    * columns. If the two buffers cover different areas (e.g. after a resize), every cell of `next` is emitted.
    */
  def diff(next: Buffer): Iterator[(Position, Cell)] =
    val emitAll   = area != next.area
    val positions =
      for
        y <- Iterator.range(next.area.y, next.area.bottom)
        x <- Iterator.range(next.area.x, next.area.right)
      yield Position(x, y)
    positions
      .filter(pos => emitAll || get(pos.x, pos.y) != next.get(pos.x, pos.y))
      .filterNot(pos => next.isContinuation(pos))
      .map(pos => (pos, next.get(pos.x, pos.y)))

  private def isContinuation(pos: Position): Boolean =
    pos.x > area.x && CharWidth.of(get(pos.x - 1, pos.y).symbol) == 2

  private def indexOf(x: Int, y: Int): Int =
    (y - area.y) * area.width + (x - area.x)
