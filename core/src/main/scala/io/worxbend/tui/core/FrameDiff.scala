package io.worxbend.tui.core

/** The frame-diff engine behind [[Buffer.diff]]: the row scan and per-cell emit pass that decide which cells of the
  * next frame a terminal backend must repaint.
  *
  * This lives apart from [[Buffer]] so the grid's write path ([[Buffer.set]], [[Buffer.fill]], [[Buffer.blit]]) and its
  * read-out path can evolve separately; the public `diff` methods on [[Buffer]] validate their arguments and delegate
  * here. One instance is one diff: it is created per call, walks the two frames once, and is discarded.
  */
private[core] final class FrameDiff(
    previous: Buffer,
    next: Buffer,
    emit: (Int, Int, Cell) => Unit,
    trailingCellPolicy: TrailingCellPolicy,
):

  private val previousCells: Array[Cell]            = previous.cells
  private val previousContinuations: Array[Boolean] = previous.continuations
  private val previousDirectives: Array[Byte]       = previous.directives
  private val nextCells: Array[Cell]                = next.cells
  private val nextContinuations: Array[Boolean]     = next.continuations
  private val nextDirectives: Array[Byte]           = next.directives

  /** Walks both frames row by row, emitting every changed cell in row-major order.
    *
    * Both frames are walked by array index rather than by coordinate. [[Buffer.diff]] has already required the two
    * frames to be the same width and to start at the same column, which is exactly the condition under which one index
    * names the same position in both grids, so the bounds check `Buffer.get(x, y)` performs per read has nothing left
    * to discover. A coordinate is computed only for a cell that is actually emitted. Each row is compared as a whole
    * first: on a typical frame almost every row is untouched, and one scan that stops at the first difference is
    * cheaper than running the per-cell emit machinery across it.
    */
  def run(): Unit =
    // a next frame one row shorter is diffed over the rows the two share, the way ratatui does; the rows only `next`
    // has are the caller's to paint, because the previous frame has nothing to compare them against
    val rows    = math.min(previous.area.height, next.area.height)
    val width   = previous.area.width
    val originX = previous.area.x
    val originY = previous.area.y
    var row     = 0
    while row < rows do
      val start = row * width
      val end   = start + width
      if !rowUnchanged(start, end) then emitChangedRow(start, end, originX, originY + row)
      row += 1

  /** The per-cell emit pass over the row spanning `[start, end)`: decides which positions changed and hands each one to
    * `emit`, together with the emoji trailing-cell workaround when that is switched on.
    */
  private def emitChangedRow(start: Int, end: Int, originX: Int, y: Int): Unit =
    var index = start
    while index < end do
      val candidate = nextCells(index)
      // reference equality first: unchanged cells are usually the *same* object, and Cell.equals walks a String
      val changed   = nextDirectives(index) == Buffer.AlwaysUpdateCode ||
        !previous.sameCell(previousCells(index), candidate) ||
        vacatedContinuation(index, start) ||
        released(index)
      if changed && !nextContinuations(index) && nextDirectives(index) != Buffer.SkipCode then
        emit(originX + index - start, y, candidate)
        // the reserved column exists only for a two-column cluster, so this is the pair the workaround is about
        if trailingCellPolicy == TrailingCellPolicy.Clear && index + 1 < end && nextContinuations(index + 1)
          && CharWidth.hasEmojiPresentationSelector(candidate.symbol)
        then emit(originX + index + 1 - start, y, Cell(" ", candidate.style))
      index += 1

  /** Whether the row spanning `[from, until)` of the flat grids is identical in both frames — the same cells and the
    * same wide-grapheme continuation flags.
    *
    * The flags are part of the comparison and not an afterthought: [[vacatedContinuation]] emits a column whose cell
    * did not change but whose continuation flag did, and a scan that looked only at cells would skip the row it lives
    * in.
    */
  private def rowUnchanged(from: Int, until: Int): Boolean =
    var index = from
    while index < until && positionUnchanged(index) do index += 1
    index == until

  /** Whether one position of the row can be passed over without running the per-cell emit machinery.
    *
    * Three questions, and a "no" to any of them puts the row back on the slow path: the cell must be the same value,
    * the wide-grapheme continuation flag must be the same (see [[vacatedContinuation]]), and the [[DiffDirective]] must
    * be the same *and* not [[DiffDirective.AlwaysUpdate]] — a position that asks to be re-emitted is by definition one
    * this scan must not declare finished.
    */
  private def positionUnchanged(index: Int): Boolean =
    previous.sameCell(previousCells(index), nextCells(index)) &&
      previousContinuations(index) == nextContinuations(index) &&
      previousDirectives(index) == nextDirectives(index) &&
      nextDirectives(index) != Buffer.AlwaysUpdateCode

  /** Whether `index` is a column this frame gave up: the previous frame drew the right half of a wide grapheme there,
    * `next` does not, and that grapheme's style painted across the column.
    *
    * Before this test, such a column was never flushed. Both frames hold [[Cell.Empty]] at it — the previous frame's
    * continuation cell is an ordinary blank, and so is the new content — so the cell compare said "unchanged" and the
    * backend skipped it. On screen the terminal was still painting the right half of the old glyph: replace a
    * red-backed `漢` with a plain `a` and the red block to its right stayed. Emitting the new (blank) cell repaints it.
    *
    * The `index - 1` read is the grapheme that owned the continuation. `rowStart` is where the row begins in the flat
    * grid, and a continuation flag can never sit in the row's first column — a wide grapheme reserves the cell to its
    * *right* — so the guard against reading the previous row's last cell is the flag itself, checked first.
    */
  private def vacatedContinuation(index: Int, rowStart: Int): Boolean =
    previousContinuations(index) && !nextContinuations(index) && index > rowStart &&
      previousCells(index - 1).style.visibleOnBlank

  /** Whether `index` is a position an out-of-band painter has just given back: [[DiffDirective.Skip]] in the previous
    * frame and not in `next`.
    *
    * Such a position has to be emitted whatever its content says. The buffer never flushed it while it was skipped, so
    * the grid's memory of it describes a cell the terminal was never told about — and the picture that was really there
    * is one this renderer cannot compare against. An ordinary content compare would call the position unchanged and
    * leave the last frame of a dismissed image on screen.
    */
  private def released(index: Int): Boolean =
    previousDirectives(index) == Buffer.SkipCode && nextDirectives(index) != Buffer.SkipCode

private[core] object FrameDiff:

  /** The body of [[Buffer.emitAll]]: every cell of `buffer`, in the same row-major order and with the same continuation
    * rule as [[FrameDiff]]'s diff — the full repaint the first frame after a resize or a resume from suspend needs.
    */
  def emitAll(buffer: Buffer, emit: (Int, Int, Cell) => Unit): Unit =
    buffer.foreachIndex(buffer.area): (x, y, index) =>
      if !buffer.continuations(index) && buffer.directives(index) != Buffer.SkipCode then
        emit(x, y, buffer.cells(index))
