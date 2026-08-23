package io.worxbend.tui.core

/** A mutable grid of [[Cell]]s covering `area`, the in-memory render target for one frame.
  *
  * `x`/`y` arguments are absolute terminal coordinates (the same space as `area`'s offset), not area-relative — widgets
  * receive a `Rect` positioned in absolute space and write to the buffer at those coordinates. Writes outside `area`
  * are silently clipped, never errors; reads outside `area` return [[Cell.Empty]].
  *
  * Mutability is an implementation detail of the render loop — it never escapes through `Widget.render`'s contract.
  *
  * '''Not thread-safe.''' The grid is a plain array with no synchronization, so a `Buffer` is owned by whichever thread
  * renders into it — in a running application that is the runner's render thread, the same thread `Signal` writes are
  * pinned to. Staging content on a background thread is safe only if the buffer is fully written before the reference
  * is published to the render thread (`RenderThread.capture` in `tui-runtime` is how background work hands its
  * continuation back) and is never touched again afterwards. Concurrent writes, or reads racing a write, produce a torn
  * frame or a dropped diff rather than an error.
  */
final class Buffer(val area: Rect):

  private val cells: Array[Cell] = Array.fill(area.area)(Cell.Empty)

  /** Which cells are the second column of a two-column grapheme, recorded by [[set]] rather than measured.
    *
    * Two invariants the rest of this class relies on: a flagged cell always holds [[Cell.Empty]], so releasing a flag
    * can never destroy content; and the cell to the left of a flagged cell is always two columns wide.
    *
    * The relationship used to be inferred at diff time by measuring the left neighbour's width, which misclassified
    * real content as filler as soon as a second, independent write landed on that column — and [[diff]] then silently
    * refused to flush it.
    */
  private val continuations: Array[Boolean] = Array.fill(area.area)(false)

  /** The cell at `(x, y)`, or [[Cell.Empty]] when the coordinates fall outside `area`. */
  def get(x: Int, y: Int): Cell = cellAt(x, y)

  /** Writes `cell` at `(x, y)`; writes outside `area` are silently clipped.
    *
    * A write can also mutate the neighbouring cell, because a two-column grapheme owns two cells and a terminal cannot
    * draw half of one: writing a wide cell reserves the cell to its right as its continuation; overwriting a wide cell
    * releases the continuation it held; and writing real content over a continuation blanks the wide grapheme that
    * owned it, since that grapheme can no longer draw across a column somebody else has claimed.
    *
    * Writing [[Cell.Empty]] over a continuation is the one exception and leaves the pair intact — that pair is how
    * callers spell the filler itself, so treating it as a claim would erase the grapheme they just wrote.
    *
    * A wide cell aimed at the last column of a row is stored as a blank in `cell`'s style rather than as itself: there
    * is no column left to reserve, and a terminal handed a two-column glyph in a one-column slot wraps the row. The
    * style is kept so a background fill stays continuous to the edge.
    */
  def set(x: Int, y: Int, cell: Cell): Unit =
    setMeasured(x, y, cell, CharWidth.ofCluster(cell.symbol))

  /** [[set]] for a caller that already knows `cell`'s display width, so the cluster is measured once per written cell
    * rather than once by the caller and again here. `width` must be `CharWidth.ofCluster(cell.symbol)`.
    */
  private def setMeasured(x: Int, y: Int, cell: Cell, width: Int): Unit =
    if area.contains(x, y) then
      val index    = indexOf(x, y)
      // `&&` short-circuits, so the structural compare (which walks `Style`) only runs on the rare flagged column
      val isFiller = continuations(index) && cell == Cell.Empty
      if !isFiller then
        if continuations(index) then cells(index - 1) = Cell.Empty
        write(index, x, cell, width)

  /** [[set]]'s tail, once the addressed cell is known not to be a wide grapheme's filler: stores `cell` and re-derives
    * the continuation to its right. `x` is passed only to test the row's right edge — `index + 1` would otherwise wrap
    * onto the next row.
    */
  private def write(index: Int, x: Int, cell: Cell, width: Int): Unit =
    val hasRight = x + 1 < area.right
    val isWide   = width == 2
    // the cell being replaced may itself be a wide grapheme, in which case it owns the column to its right
    if hasRight && continuations(index + 1) then releaseContinuation(index + 1)
    // a wide grapheme with no column left to claim would render across a column this buffer does not own
    cells(index) = if isWide && !hasRight then Cell(" ", cell.style) else cell
    continuations(index) = false
    if isWide && hasRight then
      // the column being claimed may itself be a wide grapheme's left half, in which case it owns the column beyond
      // it; that continuation is orphaned the moment we blank its owner, so release it in the same breath
      if x + 2 < area.right && continuations(index + 2) then releaseContinuation(index + 2)
      claimContinuation(index + 1)

  /** Gives up the continuation flag at `index`, blanking the cell with it.
    *
    * A flagged cell only ever holds [[Cell.Empty]], so this can never destroy content. Leaving a stale flag set would
    * make [[diff]] suppress a column the frame has already blanked.
    */
  private def releaseContinuation(index: Int): Unit =
    cells(index) = Cell.Empty
    continuations(index) = false

  /** Reserves `index` as the second column of the two-column grapheme to its left.
    *
    * The cell is blanked as well as flagged, which is the invariant [[continuations]] documents: a terminal draws the
    * wide grapheme across both columns, so the second one must carry no symbol of its own.
    */
  private def claimContinuation(index: Int): Unit =
    cells(index) = Cell.Empty
    continuations(index) = true

  /** Writes `text` starting at `(x, y)`, one grapheme cluster per cell, clipping at the area's right edge.
    *
    * A wide (two-column) cluster occupies its cell plus a continuation cell to the right, which [[set]] reserves; a
    * wide cluster that would only half-fit at the right edge is dropped entirely. Grapheme clusters that begin with a
    * combining mark (no base character before them in `text`) are skipped.
    *
    * A row outside `area` returns at once. That is the same clipping [[set]] already applies cell by cell, moved ahead
    * of the work: without it a line drawn above or below the buffer still split itself into grapheme clusters and built
    * one [[Cell]] per character, only for every one of them to be rejected. Scrolled content, where most rows are
    * off-window by design, is what makes that the common case rather than a corner one.
    */
  def setString(x: Int, y: Int, text: String, style: Style): Unit =
    if y < area.y || y >= area.bottom then ()
    else if CharWidth.isPrintableAscii(text) then setAsciiString(x, y, text, style)
    else
      var column   = x
      // set by the branch below, so that "dropped a half-fitting cluster" is a distinct exit from "ran out of columns"
      var stopped  = false
      val clusters = CharWidth.graphemeClusters(text)
      while clusters.hasNext && !stopped && column < area.right do
        val cluster = clusters.next()
        // `ofCluster`, not `of`: the iterator has already established this is exactly one cluster, and `of` would
        // build a second cluster iterator over it. The width is then handed to `setMeasured` rather than re-derived.
        val width   = CharWidth.ofCluster(cluster)
        // a zero-width cluster (a combining mark with no base character before it) claims no cell at all
        if width > 0 then
          if column + width <= area.right then
            setMeasured(column, y, Cell(cluster, style), width)
            column += width
          else stopped = true // a wide cluster that only half-fits at the edge
      end while

  /** Allocation-free [[setString]] for printable ASCII: one column per char, symbols taken from a shared table. */
  private def setAsciiString(x: Int, y: Int, text: String, style: Style): Unit =
    var index  = 0
    var column = x
    while index < text.length && column < area.right do
      // printable ASCII is one column by definition, so the width needs no measuring at all
      setMeasured(column, y, Cell(CharWidth.asciiSymbol(text.charAt(index)), style), 1)
      column += 1
      index += 1

  /** Copies `region` of `source` into this buffer with the region's top-left landing at `at`.
    *
    * Writes outside this buffer's area are clipped like any other write — this is how offscreen-rendered content
    * (scroll views, overlays) lands on the frame. A `region` reaching past `source`'s own area is trimmed to it, and
    * the landing point moves by however much the trim shifted the region's origin, so the surviving cells keep their
    * position relative to `at` instead of sliding onto the ones that were dropped.
    *
    * Blitting a buffer onto itself works on a snapshot: without it, overlapping rows would read cells this call has
    * already overwritten and smear them across the region.
    *
    * Both buffers must belong to the calling thread for the duration of the call: `source` is read cell by cell with no
    * synchronization, so another thread writing into it mid-blit copies half of one frame and half of the next.
    */
  def blit(source: Buffer, at: Position, region: Rect): Unit =
    if source eq this then blit(source.snapshot, at, region)
    else
      val clipped = region.intersection(source.area)
      val originX = at.x + (clipped.x - region.x)
      val originY = at.y + (clipped.y - region.y)
      var dy      = 0
      while dy < clipped.height do
        val y  = clipped.y + dy
        var dx = 0
        while dx < clipped.width do
          val cell = source.get(clipped.x + dx, y)
          val safe =
            // a wide grapheme cut in half by the *window* edge would render torn — blank the half instead. This is a
            // different edge from the destination's own: `set` blanks a wide cell that does not fit the destination
            // row, but a window narrower than the destination would otherwise let the glyph spill one column past
            // the region the caller asked to copy.
            if dx == 0 && source.isContinuationAt(clipped.x, y) then Cell.Empty
            else if dx == clipped.width - 1 && CharWidth.ofCluster(cell.symbol) == 2 then Cell.Empty
            else cell
          set(originX + dx, originY + dy, safe)
          dx += 1
        dy += 1

  /** Copies all of `source` into this buffer at `at`. */
  def blit(source: Buffer, at: Position): Unit =
    blit(source, at, source.area)

  /** An independent copy of this buffer. Backends snapshot the frame they just flushed so later mutation of the
    * caller's buffer cannot corrupt the next diff.
    *
    * The copy is unsynchronized, so it must be taken on the thread that owns this buffer; the result is a fresh buffer
    * owned by the caller, which is what makes it safe to hand to another thread once no further writes follow.
    */
  def snapshot: Buffer =
    val copied = Buffer(area)
    Array.copy(cells, 0, copied.cells, 0, cells.length)
    Array.copy(continuations, 0, copied.continuations, 0, continuations.length)
    copied

  /** Resets every cell to [[Cell.Empty]], recycling the buffer for the next frame. */
  def reset(): Unit =
    var index = 0
    while index < cells.length do
      cells(index) = Cell.Empty
      continuations(index) = false
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

  /** Whether two cells would render identically: a reference-equality fast path before the structural compare, because
    * `Cell.equals` walks a `String`.
    */
  private def sameCell(a: Cell, b: Cell): Boolean =
    (a eq b) || a == b

  /** The single bounds-check-and-index site behind [[get]]. Kept separate from [[get]] so the hot diff loop reads a
    * `private` method the compiler can inline freely.
    */
  private def cellAt(x: Int, y: Int): Cell =
    if area.contains(x, y) then cells(indexOf(x, y)) else Cell.Empty

  /** Whether `(x, y)` is the second column of a two-column grapheme. The state is *recorded* by [[set]], never measured
    * here: inferring it from the left neighbour's width is what made [[diff]] silently drop every cell written over a
    * wide grapheme's second column. Out-of-area coordinates are never continuations.
    */
  private def isContinuationAt(x: Int, y: Int): Boolean =
    area.contains(x, y) && continuations(indexOf(x, y))

  private def indexOf(x: Int, y: Int): Int =
    (y - area.y) * area.width + (x - area.x)
