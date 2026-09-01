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
    *
    * Style is written in two forms. The first painted cell of a frame gets the absolute sequence, which begins with a
    * reset, so nothing the terminal was left holding can leak into this frame. Every later style change on the same
    * frame gets only the attributes that moved (see [[Sgr.sgrDelta]]) — a run that differs from its predecessor in the
    * bold flag alone costs a few bytes rather than a restatement of both colours.
    */
  def encode(previous: Buffer, next: Buffer): String =
    val body                            = StringBuilder()
    var expectedX                       = -1
    var currentY                        = -1
    var currentStyle: Style             = Style.Default
    var currentLink: Option[String]     = None
    // Whether this frame has emitted an SGR sequence yet. The first one is written in the absolute form
    // ([[Sgr.sgr]], which opens with a reset), because the encoder cannot know what state the terminal was
    // left in by whatever was drawn before — a previous frame, the shell, a subprocess. After that anchor every style
    // change on the frame is written as a delta against the style the encoder itself last emitted, which is a handful
    // of bytes instead of a full restatement of every colour and flag.
    var frameOpened                     = false
    // one frame cannot be described as a difference from a frame of another shape — after a resize the terminal has
    // thrown the old grid away — so that case repaints every cell instead of diffing
    val paint: (Int, Int, Cell) => Unit =
      (x, y, cell) => {
        if y != currentY || x != expectedX then body ++= AnsiSequences.moveTo(x, y)
        // Both halves of the comparison earn their keep: reference equality catches the common case of one `Style`
        // value reused across a run of cells, and the structural compare catches effects such as `Effect.fadeIn`,
        // which hand every cell a freshly allocated but usually equal `Style`. Either way a run of same-styled cells
        // builds no sequence at all.
        if !frameOpened then
          body ++= Sgr.sgr(cell.style, colorDepth)
          currentStyle = cell.style
          frameOpened = true
        else if !((cell.style eq currentStyle) || cell.style == currentStyle) then
          body ++= Sgr.sgrDelta(currentStyle, cell.style, colorDepth)
          currentStyle = cell.style
        currentLink = carryLink(body, currentLink, cell.style.link)
        body ++= cell.symbol
        currentY = y
        expectedX = x + advanceOf(next, x, y)
      }
    if previous.area == next.area then previous.diff(next, paint) else next.emitAll(paint)
    // a link left open would swallow everything drawn after this frame
    carryLink(body, currentLink, None)
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
          val sgr  = Sgr.sgr(cell.style, colorDepth)
          if sgr != currentStyle then
            body ++= sgr
            currentStyle = sgr
          currentLink = carryLink(body, currentLink, cell.style.link)
          body ++= cell.symbol
        x += 1
      carryLink(body, currentLink, None)
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

  /** Emits the OSC-8 transitions that carry the hyperlink state from `open` to `next`, and answers `next`.
    *
    * A link is closed only when one was open, and opened only when the new cell has one, so a run of cells sharing a
    * link (or sharing no link) writes nothing. Passing `None` for `next` is how both encoders close the frame or row: a
    * link left open would swallow everything drawn afterwards.
    */
  private def carryLink(body: StringBuilder, open: Option[String], next: Option[String]): Option[String] =
    if next != open then
      if open.nonEmpty then body ++= AnsiSequences.LinkClose
      next.foreach(url => body ++= AnsiSequences.linkOpen(url))
    next
