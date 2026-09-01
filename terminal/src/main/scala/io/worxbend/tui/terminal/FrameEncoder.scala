package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Cell, Style}

/** Turns the difference between two frames into the ANSI text that moves the terminal from one to the other.
  *
  * Pure: it touches no terminal, no signal state and nothing mutable outside the call, so an instance may be shared and
  * `encode` may be called from any thread. [[JLine3Backend]] keeps one and calls it from the render thread.
  *
  * Separated from the backend because this is where a wrong frame comes from — the carry-over rules below are the
  * subtlest logic in the module, and as a method on the backend they could only be exercised by driving a whole JLine
  * terminal over streams.
  *
  * `colorDepth` decides how a [[io.worxbend.tui.core.Style]]'s colours are written; it is fixed for the lifetime of the
  * encoder because it is a property of the terminal.
  */
private[terminal] final class FrameEncoder(colorDepth: ColorDepth):

  /** The cell-level ANSI for everything that differs between `previous` and `next`, in row-major order.
    *
    * Cursor position, SGR state and the open hyperlink carry across cells, so each is re-emitted only where it actually
    * changes — that suppression is most of the reason a diffed frame is small enough to write in one go. Returns the
    * empty string when the frame is unchanged.
    *
    * `previous` and `next` covering different rectangles is not a difference that can be encoded — that happens when
    * the terminal was resized, and the grid the old frame described no longer exists — so every cell of `next` is
    * written instead. Anything narrower would leave the parts of the new grid the old one never covered unpainted.
    */
  def encode(previous: Buffer, next: Buffer): String =
    val body                            = StringBuilder()
    var expectedX                       = -1
    var currentY                        = -1
    var currentStyle                    = ""
    var currentLink: Option[String]     = None
    // The last style [[AnsiSequences.sgr]] was asked about, and what it answered. Neighbouring cells overwhelmingly
    // share a style, and on a full-change frame — the first one, every resize, every SIGCONT, every tick of a
    // whole-frame effect — rebuilding that sequence per cell and throwing it away was most of the encoding cost.
    // Both halves of the comparison earn their keep: reference equality catches the common case of one `Style` value
    // reused across a run of cells, and the structural compare catches effects such as `Effect.fadeIn`, which hand
    // every cell a freshly allocated but usually equal `Style`.
    // Seeded with `Style.Default` so there is no "nothing cached yet" case, and local to the call so that `encode`
    // stays pure and callable from any thread, as the class doc promises.
    var memoStyle: Style                = Style.Default
    var memoSgr: String                 = AnsiSequences.sgr(Style.Default, colorDepth)
    // one frame cannot be described as a difference from a frame of another shape — after a resize the terminal has
    // thrown the old grid away — so that case repaints every cell instead of diffing
    val paint: (Int, Int, Cell) => Unit =
      (x, y, cell) => {
        if y != currentY || x != expectedX then body ++= AnsiSequences.moveTo(x, y)
        if !((cell.style eq memoStyle) || cell.style == memoStyle) then
          memoStyle = cell.style
          memoSgr = AnsiSequences.sgr(cell.style, colorDepth)
        val sgr = memoSgr
        if sgr != currentStyle then
          body ++= sgr
          currentStyle = sgr
        if cell.style.link != currentLink then
          if currentLink.nonEmpty then body ++= AnsiSequences.LinkClose
          cell.style.link.foreach(url => body ++= AnsiSequences.linkOpen(url))
          currentLink = cell.style.link
        body ++= cell.symbol
        currentY = y
        expectedX = x + advanceOf(next, x, y)
      }
    if previous.area == next.area then previous.diff(next, paint) else next.emitAll(paint)
    // a link left open would swallow everything drawn after this frame
    if currentLink.nonEmpty then body ++= AnsiSequences.LinkClose
    body.result()

  /** One row of `buffer` as styled ANSI text, with no cursor movement in it at all.
    *
    * [[encode]] is for a frame that owns the screen: it positions the cursor at each run of changed cells, which is
    * exactly wrong for text that is being *printed* into the terminal's scrollback, where the terminal itself decides
    * which line the text lands on. This writes only style sequences and glyphs, so the caller can print the result
    * followed by a newline the way it would print any other line of output.
    *
    * Trailing cells that are blank in the default style are dropped: a rendered block is padded to the full width of
    * the terminal, and a scrollback line carrying that padding is one whose background colour runs to the edge of the
    * window and stays there for as long as the line does. Continuation columns — the second half of a two-column
    * grapheme — are skipped, because the terminal paints them from the cell to their left.
    *
    * The result ends with an explicit style reset, so a colour set for the last cell cannot leak into whatever the
    * shell prints next. Rows outside `buffer.area` encode to the empty string.
    */
  def encodeRow(buffer: Buffer, y: Int): String =
    val end = lastInterestingColumn(buffer, y)
    if end < buffer.area.x then ""
    else
      val body                        = StringBuilder()
      var currentStyle                = ""
      var currentLink: Option[String] = None
      var x                           = buffer.area.x
      while x <= end do
        if !buffer.isContinuation(x, y) then
          val cell = buffer.get(x, y)
          val sgr  = AnsiSequences.sgr(cell.style, colorDepth)
          if sgr != currentStyle then
            body ++= sgr
            currentStyle = sgr
          if cell.style.link != currentLink then
            if currentLink.nonEmpty then body ++= AnsiSequences.LinkClose
            cell.style.link.foreach(url => body ++= AnsiSequences.linkOpen(url))
            currentLink = cell.style.link
          body ++= cell.symbol
        x += 1
      if currentLink.nonEmpty then body ++= AnsiSequences.LinkClose
      body ++= AnsiSequences.ResetStyle
      body.result()

  /** The rightmost column of row `y` that carries anything worth writing, or one less than the row's first column when
    * the whole row is blank in the default style.
    */
  private def lastInterestingColumn(buffer: Buffer, y: Int): Int =
    var last = buffer.area.x - 1
    var x    = buffer.area.x
    while x < buffer.area.right do
      val cell = buffer.get(x, y)
      if !buffer.isContinuation(x, y) && (cell.symbol != " " || cell.style != Style.Default) then last = x
      x += 1
    last

  /** How many columns the terminal's cursor has moved after the cell at `(x, y)` was written.
    *
    * The answer comes from the buffer's own bookkeeping rather than from measuring the symbol again: a cell is two
    * columns wide exactly when the buffer reserved the column to its right as that grapheme's continuation, which
    * [[io.worxbend.tui.core.Buffer.isContinuation]] reports. Everything else advances one column, including a
    * zero-width symbol — something was written into the stream for it, and the terminal's own cursor moved by whatever
    * that glyph turned out to be, so the next cell must be positioned with an explicit `moveTo` rather than assumed to
    * be adjacent.
    *
    * This used to be `math.max(1, CharWidth.of(cell.symbol))`, and the difference matters. A `Cell`'s symbol is meant
    * to hold one grapheme cluster, and the buffer reserves columns for it with a per-cluster measurement that can only
    * answer 0, 1 or 2. `CharWidth.of` measures a whole string and sums over every cluster in it, so a symbol that
    * somehow carried more than one cluster — several combining marks after their base character, or a long escape
    * sequence smuggled in as content — measured 3, 40 or 400 columns while the buffer had reserved one. `expectedX`
    * then pointed far to the right of the real cursor, the next changed cell's `moveTo` was suppressed because its `x`
    * "already matched", and the rest of the frame was painted into the wrong columns. ratatui hit this exact bug with a
    * 400-byte escape sequence in a cell; asking the buffer instead of re-measuring makes it unreachable by
    * construction.
    */
  private def advanceOf(next: Buffer, x: Int, y: Int): Int =
    if next.isContinuation(x + 1, y) then 2 else 1
