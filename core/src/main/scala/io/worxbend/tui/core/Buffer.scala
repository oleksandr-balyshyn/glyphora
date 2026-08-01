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
    if CharWidth.isPrintableAscii(text) then setAsciiString(x, y, text, style)
    else
      var column   = x
      val clusters = CharWidth.graphemeClusters(text)
      while clusters.hasNext && column < area.right do
        val cluster = clusters.next()
        val width   = CharWidth.of(cluster)
        // a zero-width cluster (a combining mark with no base character before it) claims no cell at all
        if width > 0 then
          if column + width <= area.right then
            set(column, y, Cell(cluster, style))
            if width == 2 then set(column + 1, y, Cell.Empty)
            column += width
          else column = area.right // a wide cluster that only half-fits at the edge: stop
      end while

  /** Allocation-free [[setString]] for printable ASCII: one column per char, symbols taken from a shared table. */
  private def setAsciiString(x: Int, y: Int, text: String, style: Style): Unit =
    var index  = 0
    var column = x
    while index < text.length && column < area.right do
      set(column, y, Cell(CharWidth.asciiSymbol(text.charAt(index)), style))
      column += 1
      index += 1

  /** Copies `region` of `source` into this buffer with the region's top-left landing at `at`.
    *
    * Writes outside this buffer's area are clipped like any other write — this is how offscreen-rendered content
    * (scroll views, overlays) lands on the frame.
    */
  def blit(source: Buffer, at: Position, region: Rect): Unit =
    val clipped = region.intersection(source.area)
    var dy      = 0
    while dy < clipped.height do
      val y  = clipped.y + dy
      var dx = 0
      while dx < clipped.width do
        val cell = source.get(clipped.x + dx, y)
        val safe =
          // a wide grapheme cut in half by the window edge would render torn — blank the half instead
          if dx == 0 && CharWidth.of(source.get(clipped.x - 1, y).symbol) == 2 then Cell.Empty
          else if dx == clipped.width - 1 && CharWidth.of(cell.symbol) == 2 then Cell.Empty
          else cell
        set(at.x + dx, at.y + dy, safe)
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
    val changes = Iterator.newBuilder[(Position, Cell)]
    diff(next, (x, y, cell) => changes += ((Position(x, y), cell)))
    changes.result()

  /** [[diff]] without the intermediate objects: calls `emit(x, y, cell)` for each changed cell, in row-major order.
    *
    * This is what backends use on the hot path — it allocates nothing per cell (no `Position`, no tuple, no iterator
    * state), which matters because a 200x50 frame is 10 000 cells and runs at the tick rate.
    */
  def diff(next: Buffer, emit: (Int, Int, Cell) => Unit): Unit =
    val emitAll = area != next.area
    var y       = next.area.y
    while y < next.area.bottom do
      var x = next.area.x
      while x < next.area.right do
        val candidate = next.cellAt(x, y)
        // reference equality first: unchanged cells are usually the *same* object, and Cell.equals walks a String
        val changed   = emitAll || !sameCell(cellAt(x, y), candidate)
        if changed && !next.isContinuationAt(x, y) then emit(x, y, candidate)
        x += 1
      y += 1

  private def sameCell(a: Cell, b: Cell): Boolean =
    (a.asInstanceOf[AnyRef] eq b.asInstanceOf[AnyRef]) || a == b

  /** Like [[get]] but without the `Position` allocation `Rect.contains` would need. */
  private def cellAt(x: Int, y: Int): Cell =
    if x >= area.x && x < area.right && y >= area.y && y < area.bottom then cells(indexOf(x, y)) else Cell.Empty

  private def isContinuationAt(x: Int, y: Int): Boolean =
    x > area.x && CharWidth.of(cellAt(x - 1, y).symbol) == 2

  private def indexOf(x: Int, y: Int): Int =
    (y - area.y) * area.width + (x - area.x)
