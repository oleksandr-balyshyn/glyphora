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

  /** The cell at `position`, or [[Cell.Empty]] when it falls outside `area`.
    *
    * The same read as `get(x, y)`, spelled for callers that already hold a [[Position]] — a mouse event's coordinate, a
    * [[Rect]]'s `position`, an entry from [[diff]] — so they need not unpack it into two arguments.
    */
  def get(position: Position): Cell = cellAt(position.x, position.y)

  /** Writes `cell` at `position`, with exactly the semantics of `set(x, y, cell)` including the clipping and the
    * wide-grapheme bookkeeping described there.
    */
  def set(position: Position, cell: Cell): Unit = set(position.x, position.y, cell)

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
    writeString(x, y, text, style, area.right)

  /** Writes `text` at `(x, y)` stopping after at most `maxWidth` columns, and answers how many columns it wrote.
    *
    * Two things this adds over the four-argument [[setString]]. The bound: the write stops at whichever comes first,
    * the area's right edge or `x + maxWidth`, so a caller with a column budget no longer has to cut the string to size
    * beforehand. And the answer: the returned column count is exactly how far the cursor advanced, so a caller laying a
    * row out as a run of segments adds it to `x` instead of measuring the text a second time.
    *
    * The count can be smaller than `maxWidth` even with text left over. A two-column cluster that would only half-fit
    * inside the bound is dropped whole rather than split — a terminal handed half a wide glyph draws it across the
    * column beyond the budget — so a budget of 3 filled with `漢字` writes 2 columns and reports 2. A negative `maxWidth`
    * is treated as zero: nothing is written and the answer is 0.
    */
  def setString(x: Int, y: Int, text: String, style: Style, maxWidth: Int): Int =
    writeString(x, y, text, style, math.min(area.right, x + math.max(0, maxWidth)))

  /** Writes `span`'s content at `(x, y)` in at most `maxWidth` columns, and answers how many columns it wrote.
    *
    * The span's own [[Style]] is layered over `baseStyle` — the argument supplies whatever the span says nothing about,
    * which is how a theme colour reaches text that already chose to be bold. Clipping is [[setString]]'s: the write
    * stops at the area's right edge or at `x + maxWidth`, whichever comes first, and a two-column cluster that would
    * only half-fit inside that bound is dropped whole rather than split, so the answer can be smaller than the budget
    * with content left over.
    *
    * A [[Span]] is a `tui-core` value, and so is the buffer it is drawn into; before this existed the code that put one
    * into the other lived inside `tui-widgets`, out of reach of every other module. Nothing about it needs a widget.
    */
  def setSpan(x: Int, y: Int, span: Span, maxWidth: Int, baseStyle: Style = Style.Default): Int =
    setString(x, y, span.content, baseStyle.patch(span.style), maxWidth)

  /** Writes `line`'s spans left to right from `(x, y)`, sharing one budget of `maxWidth` columns between them, and
    * answers how many columns were written in total.
    *
    * Each span's style is layered over `baseStyle`, exactly as [[setSpan]] describes. The spans are written end to end
    * with nothing inserted between them, and the budget is spent as it goes, so a line wider than its budget stops part
    * way through whichever span reaches the edge instead of dropping that span and every one after it. Writing stops as
    * soon as the budget is gone, so the tail of a very long line is never even measured.
    *
    * This places the line where it is told and nowhere else. Centring or right-aligning a row against a wider area is a
    * decision about layout rather than about writing cells, and belongs to the widget making it — see
    * `io.worxbend.tui.core.Alignment` for the arithmetic the widgets share.
    */
  def setLine(x: Int, y: Int, line: Line, maxWidth: Int, baseStyle: Style = Style.Default): Int =
    val budget  = math.max(0, maxWidth)
    var written = 0
    val spans   = line.spans.iterator
    while spans.hasNext && written < budget do
      written += setSpan(x + written, y, spans.next(), budget - written, baseStyle)
    written

  /** The shared body of both [[setString]] overloads: writes clusters left to right while the write head stays below
    * `limit` (an exclusive column bound already clipped to the area), and answers the columns written.
    */
  private def writeString(x: Int, y: Int, text: String, style: Style, limit: Int): Int =
    if y < area.y || y >= area.bottom || limit <= x then 0
    else if CharWidth.isPrintableAscii(text) then writeAsciiString(x, y, text, style, limit)
    else
      var column   = x
      // set by the branch below, so that "dropped a half-fitting cluster" is a distinct exit from "ran out of columns"
      var stopped  = false
      val clusters = CharWidth.graphemeClusters(text)
      while clusters.hasNext && !stopped && column < limit do
        val cluster = clusters.next()
        // `ofCluster`, not `of`: the iterator has already established this is exactly one cluster, and `of` would
        // build a second cluster iterator over it. The width is then handed to `setMeasured` rather than re-derived.
        val width   = CharWidth.ofCluster(cluster)
        // a zero-width cluster (a combining mark with no base character before it) claims no cell at all
        if width > 0 then
          if column + width <= limit then
            setMeasured(column, y, Cell(cluster, style), width)
            column += width
          else stopped = true // a wide cluster that only half-fits at the edge
      end while
      column - x

  /** Allocation-free [[writeString]] for printable ASCII: one column per char, symbols taken from a shared table. */
  private def writeAsciiString(x: Int, y: Int, text: String, style: Style, limit: Int): Int =
    var index  = 0
    var column = x
    while index < text.length && column < limit do
      // printable ASCII is one column by definition, so the width needs no measuring at all
      setMeasured(column, y, Cell(CharWidth.asciiSymbol(text.charAt(index)), style), 1)
      column += 1
      index += 1
    column - x

  /** Writes `cell` into every column of `region` that falls inside `area`; anything outside is clipped away, exactly as
    * [[set]] clips a single write.
    *
    * This is the one owner of "blank a box". An overlay — a dialog, a popup, a menu — has to paint its whole rectangle
    * in its own style so that whatever was drawn underneath cannot show through, and before this method every such
    * widget wrote its own pair of `while` loops to do it, which is how one ends up a column short on one edge.
    *
    * The cell's display width is measured once for the whole region instead of once per column, and a two-column symbol
    * advances two columns at a time so that the continuation cell [[set]] reserves is not immediately painted over. If
    * the region's width is not a whole multiple of that symbol's width, the leftover column at the right edge is stored
    * as a blank in `cell`'s style — that is [[set]]'s existing rule for a wide symbol with no room to its right, and it
    * keeps a background fill continuous to the edge.
    */
  def fill(region: Rect, cell: Cell): Unit =
    val clipped = region.intersection(area)
    // a zero-width symbol (a lone combining mark, say) claims no column, and stepping by zero would never terminate
    val step    = math.max(1, CharWidth.ofCluster(cell.symbol))
    var y       = clipped.y
    while y < clipped.bottom do
      var x = clipped.x
      while x < clipped.right do
        // A two-column grapheme starting in the last column of an odd-width region would paint its second half
        // *outside* the region: `setMeasured` only refuses to leave the buffer, and the column past the region is
        // usually still inside it. Blank that column in the fill's own style instead, the same way `blit` blanks a
        // grapheme the window edge cuts in half. The region keeps its background and the neighbour keeps its glyph.
        if x + step > clipped.right then setMeasured(x, y, Cell(" ", cell.style), 1)
        else setMeasured(x, y, cell, step)
        x += step
      y += 1

  /** Replaces the style of every cell of `region` that falls inside `area`, leaving the symbols exactly as they are.
    *
    * This is the "patch over the top" write. A widget draws its content first, and then a caller tints the rectangle
    * for a selection highlight, a disabled overlay or a focus ring without needing to know which symbols happen to be
    * there. Neither [[set]] nor [[setString]] can do that: both take the symbol as an argument, so restyling through
    * them means overwriting the content.
    *
    * Blank cells are restyled too. That is deliberate — it is what makes a background tint reach the whole rectangle
    * rather than only the columns that carry a glyph.
    *
    * Continuation cells (the second column of a two-column grapheme) are left alone: the terminal paints the wide
    * grapheme across both columns from the *left* cell's style, so restyling the right one changes nothing on screen
    * while breaking the invariant that a flagged cell holds [[Cell.Empty]] — the test [[set]] uses to tell a caller's
    * own filler apart from real content landing on a reserved column.
    */
  def setStyle(region: Rect, style: Style): Unit =
    mapStyle(region)(_ => style)

  /** [[setStyle]] with the replacement derived from each cell's current style — a dim, a tint, a background swap that
    * keeps the foreground it finds.
    *
    * `transform` must be a pure function of the style it is handed, because it is not called once per cell:
    * neighbouring cells nearly always share one style, so the most recent input and its result are remembered and the
    * transform re-run only where the style actually changes. Without that, a full-frame pass would build a fresh
    * `Style` for each of ten thousand cells to arrive at the same answer ten thousand times.
    */
  def mapStyle(region: Rect)(transform: Style => Style): Unit =
    val clipped = region.intersection(area)
    if !clipped.isEmpty then
      // `primed` distinguishes "no cell seen yet" from "the last cell happened to carry Style.Default", so the
      // transform is never run on a style no cell in the region actually has
      var primed  = false
      var lastIn  = Style.Default
      var lastOut = Style.Default
      var y       = clipped.y
      while y < clipped.bottom do
        var x = clipped.x
        while x < clipped.right do
          val index = indexOf(x, y)
          if !continuations(index) then
            val cell = cells(index)
            // reference equality first: a run of cells written by one call shares the very same `Style` object
            if !primed || !((cell.style eq lastIn) || cell.style == lastIn) then
              lastIn = cell.style
              lastOut = transform(cell.style)
              primed = true
            if lastOut != cell.style then cells(index) = cell.copy(style = lastOut)
          x += 1
        y += 1

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
            if dx == 0 && source.isContinuation(clipped.x, y) then Cell.Empty
            else if dx == clipped.width - 1 && CharWidth.ofCluster(cell.symbol) == 2 then Cell.Empty
            else cell
          set(originX + dx, originY + dy, safe)
          dx += 1
        dy += 1

  /** Copies all of `source` into this buffer at `at`. */
  def blit(source: Buffer, at: Position): Unit =
    blit(source, at, source.area)

  /** A new buffer covering the union of this buffer's area and `other`'s, holding this buffer's content with `other`'s
    * composited on top.
    *
    * The difference from [[blit]] is who decides the size. `blit` writes into a destination whose bounds the caller
    * already fixed and throws away whatever falls outside them; this grows the result to the smallest rectangle
    * covering both inputs, so nothing is lost. That is the operation for composing offscreen fragments whose extent is
    * only known once they have been produced — a tooltip measured from its own text, say, laid over a panel.
    *
    * Neither input is modified, and neither is resized: a `Buffer`'s `area` is fixed for its lifetime because
    * [[snapshot]], [[diff]] and a backend's last-flushed frame all assume the grid they hold cannot change size
    * underneath them. The result is a third buffer, owned by the caller.
    *
    * Coordinates are absolute (see the class documentation), so each input lands at the position it already claims.
    * Cells of the union that neither input covers stay [[Cell.Empty]]. Where the two overlap, `other` wins, and it wins
    * as a whole grapheme: the copy goes through [[blit]], so a two-column cluster cut by a seam is blanked rather than
    * drawn torn.
    *
    * Both buffers must belong to the calling thread for the duration of the call, for the same reason [[blit]] does.
    */
  def merged(other: Buffer): Buffer =
    val combined = Buffer(area.union(other.area))
    combined.blit(this, area.position)
    combined.blit(other, other.area.position)
    combined

  /** Applies `visit(x, y, cell)` to every cell of `region` that this buffer covers, in row-major order.
    *
    * The traversal every reader of a finished frame — a golden-frame writer, a test assertion, an alternative encoder —
    * would otherwise write for itself as a nested loop over [[get]]. Two reasons to have it here rather than there: the
    * bounds check happens once, on `region`, instead of once per cell; and nothing is allocated per cell, no `Position`
    * and no tuple, which matters because a 200x50 frame is 10 000 cells.
    *
    * `visit` sees the raw grid, continuation cells included — they arrive as the [[Cell.Empty]] they hold. A caller
    * rebuilding what a terminal displays must skip them with [[isContinuation]] rather than print their blank, or a row
    * containing a two-column grapheme comes out one column too wide.
    *
    * Writing into this buffer from inside `visit` is not supported: a write can move a neighbouring cell (see [[set]])
    * and the traversal has already decided which positions it will read.
    */
  def foreachIn(region: Rect)(visit: (Int, Int, Cell) => Unit): Unit =
    val clipped = region.intersection(area)
    var y       = clipped.y
    while y < clipped.bottom do
      var x = clipped.x
      while x < clipped.right do
        visit(x, y, cells(indexOf(x, y)))
        x += 1
      y += 1

  /** [[foreachIn]] over the whole buffer. */
  def foreach(visit: (Int, Int, Cell) => Unit): Unit =
    foreachIn(area)(visit)

  /** Whether `(x, y)` is the second column of a two-column grapheme — the half a terminal paints when it draws the
    * cluster in the cell to the left, which therefore must not be drawn a second time.
    *
    * The state is *recorded* by [[set]], never re-measured here; see [[continuations]] for why measuring it was a bug.
    * Coordinates outside `area` are never continuations.
    */
  def isContinuation(x: Int, y: Int): Boolean =
    area.contains(x, y) && continuations(indexOf(x, y))

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

  /** Overwrites this buffer's contents with `source`'s, allocating nothing.
    *
    * The in-place counterpart of [[snapshot]]. A backend that keeps a baseline of the frame it last flushed needs a
    * private copy of it, and taking that copy with `snapshot` allocates a fresh `Buffer` plus two grid-sized arrays on
    * every frame — 10 000 cells on a 200x50 screen, at the tick rate. Recycling one buffer with this method gives the
    * same private copy and allocates nothing at all.
    *
    * Both buffers must cover the same `area` and be owned by the calling thread, for the same reason [[snapshot]] must
    * be taken on the owning thread: the copy is unsynchronized, so another thread writing into `source` part-way
    * through leaves this buffer holding half of one frame and half of the next. Copying a buffer onto itself is a no-op
    * rather than an error.
    *
    * @throws IllegalArgumentException
    *   if `source.area != area`. That is a programmer error, not a condition to recover from: the grids have different
    *   shapes, so there is no correct thing to copy. A caller that can be handed a differently sized buffer — anything
    *   that survives a terminal resize — must allocate a new buffer for the new area instead.
    */
  def copyFrom(source: Buffer): Unit =
    require(source.area == area, s"cannot copy a buffer covering ${source.area} into one covering $area")
    if source ne this then
      Array.copy(source.cells, 0, cells, 0, cells.length)
      Array.copy(source.continuations, 0, continuations, 0, continuations.length)

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
    * columns. The two buffers must start at the same origin and be the same width; see [[emitAll]] for the frame after
    * a resize, where the previous frame is unusable and there is nothing to compare against.
    *
    * One column is emitted even when its content did not change: a column that was the right half of a wide grapheme in
    * this (previous) frame, is not one in `next`, and whose grapheme painted a style that shows through a blank — see
    * [[visibleOnBlank]]. Both frames hold [[Cell.Empty]] there, so a plain content compare calls it unchanged, yet the
    * terminal is still painting the old glyph's background across that half-cell.
    */
  def diff(next: Buffer): Iterator[(Position, Cell)] =
    val changes = Iterator.newBuilder[(Position, Cell)]
    diff(next, (x, y, cell) => changes += ((Position(x, y), cell)))
    changes.result()

  /** [[diff]] without the intermediate objects: calls `emit(x, y, cell)` for each changed cell, in row-major order.
    *
    * This is what backends use on the hot path — it allocates nothing per cell (no `Position`, no tuple, no iterator
    * state), which matters because a 200x50 frame is 10 000 cells and runs at the tick rate.
    *
    * Both frames are walked by array index rather than by coordinate. `require` above has already established that they
    * are the same width and start at the same column, which is exactly the condition under which one index names the
    * same position in both grids, so the bounds check `get(x, y)` performs per read has nothing left to discover. A
    * coordinate is computed only for a cell that is actually emitted. Each row is compared as a whole first: on a
    * typical frame almost every row is untouched, and one scan that stops at the first difference is cheaper than
    * running the per-cell emit machinery across it.
    */
  def diff(next: Buffer, emit: (Int, Int, Cell) => Unit): Unit =
    diff(next, emit, clearEmojiTrailingCell = false)

  /** [[diff]] with a workaround for terminals that draw an emoji presentation sequence in one column.
    *
    * A cluster containing U+FE0F — the variation selector that asks for a character's colourful emoji form — is two
    * columns wide by the Unicode rules this toolkit measures with, so the buffer reserves the column to its right as
    * filler and never emits that column: painting the glyph paints both halves. Several terminals draw such a sequence
    * in a single column anyway, and then the second column is never repainted at all, so whatever stood there in an
    * earlier frame stays on screen next to the emoji.
    *
    * With `clearEmojiTrailingCell` set, a changed cell holding such a cluster is followed by a blank emitted into its
    * reserved column, carrying the owning cell's style so a background fill stays continuous across the pair.
    *
    * It is off by default, and it is a backend's decision rather than the buffer's, because the opposite artifact is
    * just as real: on a terminal that does draw both columns, that blank lands on the right half of the glyph and clips
    * it. Only the code that knows which terminal it is talking to can pick the lesser of the two.
    *
    * The extra emission costs a boolean test per changed cell — the reserved-column flag is an array read and is
    * checked before the cluster is scanned for the selector — so a frame with no emoji in it pays nothing measurable.
    */
  def diff(next: Buffer, emit: (Int, Int, Cell) => Unit, clearEmojiTrailingCell: Boolean): Unit =
    require(
      area.x == next.area.x && area.y == next.area.y && area.width == next.area.width,
      s"diff expects two buffers with the same origin and width, got $area and ${next.area}; " +
        "call emitAll on the new frame when the previous one is unusable (a resize, a resume from suspend)",
    )
    // a next frame one row shorter is diffed over the rows the two share, the way ratatui does; the rows only `next`
    // has are the caller's to paint, because this buffer has nothing to compare them against
    val rows       = math.min(area.height, next.area.height)
    val width      = area.width
    val originX    = area.x
    val originY    = area.y
    // `next.cells` and `next.continuations` are readable from here because Scala's `private` is private to the class,
    // not to the instance: no accessor is added, and neither array escapes the method
    val nextCells  = next.cells
    val nextFiller = next.continuations
    var row        = 0
    while row < rows do
      val start = row * width
      val end   = start + width
      if !rowUnchanged(nextCells, nextFiller, start, end) then
        val y     = originY + row
        var index = start
        while index < end do
          val candidate = nextCells(index)
          // reference equality first: unchanged cells are usually the *same* object, and Cell.equals walks a String
          val changed   = !sameCell(cells(index), candidate) || vacatedTrailing(nextFiller, index, start)
          if changed && !nextFiller(index) then
            emit(originX + index - start, y, candidate)
            // the reserved column exists only for a two-column cluster, so this is the pair the workaround is about
            if clearEmojiTrailingCell && index + 1 < end && nextFiller(index + 1)
              && CharWidth.hasEmojiPresentationSelector(candidate.symbol)
            then emit(originX + index + 1 - start, y, Cell(" ", candidate.style))
          index += 1
      row += 1

  /** Whether the row spanning `[from, until)` of the flat grids is identical in both frames — the same cells and the
    * same wide-grapheme filler flags.
    *
    * The flags are part of the comparison and not an afterthought: [[vacatedTrailing]] emits a column whose cell did
    * not change but whose filler flag did, and a scan that looked only at cells would skip the row it lives in.
    *
    * `previous` is this buffer; the arrays are index-aligned because [[diff]] has already required both frames to be
    * the same width and to start at the same column.
    */
  private def rowUnchanged(nextCells: Array[Cell], nextFiller: Array[Boolean], from: Int, until: Int): Boolean =
    var index = from
    while index < until && sameCell(cells(index), nextCells(index)) && continuations(index) == nextFiller(index) do
      index += 1
    index == until

  /** Emits every cell of this buffer, in the same row-major order and with the same continuation rule as [[diff]] — the
    * full repaint the first frame after a resize or a resume from suspend needs.
    *
    * This is the honest way to spell "there is no usable previous frame". [[diff]] used to answer that case itself, by
    * quietly emitting everything whenever the two areas disagreed, which meant a caller who really had made a mistake —
    * diffing an off-screen staging buffer against the frame, say — got a silent full repaint every frame instead of an
    * error. Splitting the two makes the resize path something a test can name, and turns the mistake back into one.
    *
    * The columns holding the right half of a two-column grapheme are skipped here as well: emitting the grapheme itself
    * repaints both of its columns, and a terminal handed the filler would draw a stray blank over the half already
    * painted.
    */
  def emitAll(emit: (Int, Int, Cell) => Unit): Unit =
    var y = area.y
    while y < area.bottom do
      var x = area.x
      while x < area.right do
        val index = indexOf(x, y)
        if !continuations(index) then emit(x, y, cells(index))
        x += 1
      y += 1

  /** Content equality: the same `area`, the same [[Cell]] at every position, and the same continuation flags.
    *
    * The continuation flags are part of the comparison because they are part of what gets drawn: two buffers whose
    * cells all match but whose flags differ disagree about which columns a terminal is allowed to paint, and they
    * produce different diffs.
    *
    * '''A `Buffer` is mutable''', so this answer — and [[hashCode]] with it — changes as a frame is rendered into the
    * buffer. Compare or hash one only while nothing is writing to it, and never use one as a key in a hash map: the key
    * would move out from under the map on the next `set`.
    */
  override def equals(other: Any): Boolean = other match
    case that: Buffer =>
      (this eq that) || (area == that.area && sameCells(that) && sameContinuations(that))
    case _            => false

  private def sameCells(that: Buffer): Boolean =
    var index = 0
    var same  = true
    while same && index < cells.length do
      same = sameCell(cells(index), that.cells(index))
      index += 1
    same

  private def sameContinuations(that: Buffer): Boolean =
    java.util.Arrays.equals(continuations, that.continuations)

  /** Hashes the area and every cell. See [[equals]] for why a mutable buffer must not be used as a hash-map key. */
  override def hashCode(): Int =
    var hash  = area.hashCode()
    var index = 0
    while index < cells.length do
      hash = hash * 31 + cells(index).hashCode()
      hash = hash * 31 + (if continuations(index) then 1 else 0)
      index += 1
    hash

  /** A dump of the whole grid, for the one place a buffer is printed: the message of a failed test.
    *
    * The default `toString` of a class is its name and an object hash, which tells a reader nothing about why two
    * frames differ. This prints three sections instead, following ratatui's `Debug for Buffer`:
    *
    *   - `content` — one quoted string per row, every column included. Continuation cells hold [[Cell.Empty]] by
    *     construction, so they print as a space and the rows stay aligned in the dump even though the wide glyph before
    *     them takes two columns on a real terminal.
    *   - `styles` — one line per *change* of style in row-major order, not one line per cell. A frame is usually a few
    *     long runs of one style, so listing the runs is what makes a wrong colour visible instead of drowned.
    *   - `hidden` — the positions holding the second column of a two-column grapheme, which the `content` section
    *     cannot show because they carry no symbol of their own.
    *
    * Building the string walks every cell, so this is a debugging aid and not something to call per frame.
    */
  override def toString: String =
    val out = StringBuilder()
    out ++= s"Buffer(area=$area, content=[\n"
    var y   = area.y
    while y < area.bottom do
      out ++= "  \""
      var x = area.x
      while x < area.right do
        out ++= cells(indexOf(x, y)).symbol
        x += 1
      out ++= "\",\n"
      y += 1
    out ++= "], styles=[\n"
    appendStyleRuns(out)
    out ++= "], hidden=[\n"
    appendHiddenPositions(out)
    out ++= "])"
    out.result()

  /** Appends one line per style run to `out`: the first cell of the buffer, then every cell whose style differs from
    * the cell before it in row-major order.
    */
  private def appendStyleRuns(out: StringBuilder): Unit =
    // `None` is "no run has started yet", so the very first cell always opens one
    var previous: Option[Style] = None
    var y                       = area.y
    while y < area.bottom do
      var x = area.x
      while x < area.right do
        val style = cells(indexOf(x, y)).style
        if !previous.contains(style) then
          out ++= s"  x: $x, y: $y, $style\n"
          previous = Some(style)
        x += 1
      y += 1

  /** Appends one line per continuation cell to `out` — the columns a wide grapheme draws over from the left. */
  private def appendHiddenPositions(out: StringBuilder): Unit =
    var y = area.y
    while y < area.bottom do
      var x = area.x
      while x < area.right do
        if continuations(indexOf(x, y)) then out ++= s"  x: $x, y: $y, hidden by a two-column grapheme\n"
        x += 1
      y += 1

  /** Whether two cells would render identically: a reference-equality fast path before the structural compare, because
    * `Cell.equals` walks a `String`.
    */
  private def sameCell(a: Cell, b: Cell): Boolean =
    (a eq b) || a == b

  /** Whether a style still paints its column when the glyph in it is a blank space.
    *
    * A background colour is drawn across the whole cell rather than behind the glyph's ink, and so are reverse video,
    * underline, blink and crossed-out (all four draw something — a filled block, a rule, a strike — that a space does
    * not hide). A foreground colour or bold, by contrast, is invisible on a space. This is the test for "the terminal
    * would still be showing something here", which is what makes a vacated column worth repainting.
    */
  private def visibleOnBlank(style: Style): Boolean =
    style.bg.exists(_ != Color.Reset) ||
      style.modifiers.hasAny(Modifiers.Reverse | Modifiers.Underline | Modifiers.Blink | Modifiers.CrossedOut)

  /** Whether `(x, y)` is a column this frame gave up: the previous frame drew the right half of a wide grapheme there,
    * `next` does not, and that grapheme's style painted across the column.
    *
    * Before this test, such a column was never flushed. Both frames hold [[Cell.Empty]] at it — the previous frame's
    * filler is an ordinary blank, and so is the new content — so the cell compare said "unchanged" and the backend
    * skipped it. On screen the terminal was still painting the right half of the old glyph: replace a red-backed `漢`
    * with a plain `a` and the red block to its right stayed. Emitting the new (blank) cell repaints it.
    *
    * The `index - 1` read is the grapheme that owned the filler. `rowStart` is where the row begins in the flat grid,
    * and a filler flag can never sit in the row's first column — a wide grapheme reserves the cell to its *right* — so
    * the guard against reading the previous row's last cell is the flag itself, checked first.
    *
    * `nextFiller` is `next`'s flag array and `index` is the position in both grids, which [[diff]] may pass directly
    * because it has required the two frames to be the same width and to start at the same column.
    */
  private def vacatedTrailing(nextFiller: Array[Boolean], index: Int, rowStart: Int): Boolean =
    continuations(index) && !nextFiller(index) && index > rowStart && visibleOnBlank(cells(index - 1).style)

  /** The single bounds-check-and-index site behind [[get]]. Kept separate from [[get]] so the hot diff loop reads a
    * `private` method the compiler can inline freely.
    */
  private def cellAt(x: Int, y: Int): Cell =
    if area.contains(x, y) then cells(indexOf(x, y)) else Cell.Empty

  private def indexOf(x: Int, y: Int): Int =
    (y - area.y) * area.width + (x - area.x)

/** Ways of building a buffer from content that is already laid out, rather than from an empty rectangle.
  *
  * `Buffer(rect)` — the class's own constructor — remains how a render target is allocated. These factories are for the
  * other direction: a test that wants to write the frame it expects as a literal, instead of allocating a `Rect` of the
  * right size by hand and calling `setString` once per row.
  */
object Buffer:

  /** A fresh buffer covering `area` with every cell already set to `cell` — the starting point for a layer that is
    * opaque rather than transparent. The plain constructor `Buffer(area)` still starts out as [[Cell.Empty]]
    * everywhere, which is what a frame that composes onto other content wants.
    */
  def filled(area: Rect, cell: Cell): Buffer =
    val buffer = Buffer(area)
    buffer.fill(area, cell)
    buffer

  /** A buffer as tall as `lines` and as wide as the widest of them, with each line's spans painted left to right at
    * their own styles, starting at the origin.
    *
    * Column advance comes from `Span.width`, which measures display columns rather than characters, so a two-column
    * grapheme takes two columns here exactly as it does when a widget draws it — an expectation written with CJK or
    * emoji in it lines up with the frame under test instead of drifting one column per wide cluster. Cells no line
    * reaches keep [[Cell.Empty]].
    *
    * Example — the expected frame for a two-row widget whose second row is highlighted:
    * {{{
    * val expected = Buffer.withLines(Line.raw("Total"), Line.styled("  42", Style.Default.bold))
    * }}}
    *
    * A caller holding a `Seq[Line]` splats it: `Buffer.withLines(rows*)`.
    */
  def withLines(lines: Line*): Buffer =
    val height = lines.size
    val width  = if lines.isEmpty then 0 else lines.map(_.width).max
    val buffer = Buffer(Rect(0, 0, width, height))
    lines.zipWithIndex.foreach { (line, y) =>
      var x = 0
      line.spans.foreach { span =>
        x += buffer.setString(x, y, span.content, span.style, width - x)
      }
    }
    buffer

  /** [[withLines]] for a whole [[Text]] value, for a caller that already has one. */
  def withText(text: Text): Buffer = withLines(text.lines*)
