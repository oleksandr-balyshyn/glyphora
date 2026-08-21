package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, CharWidth}

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
    */
  def encode(previous: Buffer, next: Buffer): String =
    val body                        = StringBuilder()
    var expectedX                   = -1
    var currentY                    = -1
    var currentStyle                = ""
    var currentLink: Option[String] = None
    previous.diff(
      next,
      (x, y, cell) => {
        if y != currentY || x != expectedX then body ++= AnsiSequences.moveTo(x, y)
        val sgr = AnsiSequences.sgr(cell.style, colorDepth)
        if sgr != currentStyle then
          body ++= sgr
          currentStyle = sgr
        if cell.style.link != currentLink then
          if currentLink.nonEmpty then body ++= AnsiSequences.LinkClose
          cell.style.link.foreach(url => body ++= AnsiSequences.linkOpen(url))
          currentLink = cell.style.link
        body ++= cell.symbol
        currentY = y
        expectedX = x + math.max(1, CharWidth.of(cell.symbol))
      },
    )
    // a link left open would swallow everything drawn after this frame
    if currentLink.nonEmpty then body ++= AnsiSequences.LinkClose
    body.result()
