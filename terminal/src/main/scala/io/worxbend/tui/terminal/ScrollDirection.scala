package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Cell, Position, Rect}

/** Which way a scroll region moves its rows — see [[Backend.scrollRegionUp]] and [[Backend.scrollRegionDown]].
  *
  * A named pair rather than a boolean argument, because `scroll(region, 3, Up)` says at the call site what
  * `scroll(region, 3, true)` does not — and because getting it backwards moves real rows the wrong way on a real
  * screen, which is worth asking the type system for help with.
  */
enum ScrollDirection:

  /** Rows move up: the top rows of the region are discarded, blank rows appear at its bottom. This is a list scrolling
    * forwards, which is the common direction.
    */
  case Up

  /** Rows move down: the bottom rows of the region are discarded, blank rows appear at its top. */
  case Down

object ScrollDirection:

  /** The escape sequence that scrolls the current scroll region by `lines` in `direction`. */
  private[terminal] def sequence(direction: ScrollDirection, lines: Int): String =
    direction match
      case Up   => AnsiSequences.scrollUp(lines)
      case Down => AnsiSequences.scrollDown(lines)

  /** `frame` with the rows of `region` moved `lines` rows in `direction`, as the terminal will have moved them.
    *
    * This is what keeps a backend's frame diff honest after it has asked the terminal to scroll. `draw` writes only the
    * cells that differ from the frame it last flushed; once the terminal has shifted rows on its own, that record no
    * longer describes the screen, and every row of the band would be reported as changed — repainting precisely what
    * the scroll was meant to avoid. Applying the same shift to the record instead leaves only the newly exposed rows
    * differing, which is the part the application really does still have to write.
    *
    * The exposed rows come back as empty cells, which is what a terminal puts there. An application drawing on a
    * coloured background will therefore see those rows written again by the next frame; that is correct rather than
    * wasteful, because the terminal's idea of "blank" and the application's genuinely differ.
    *
    * Rows named outside `frame`'s own area are ignored rather than rejected: this models what the terminal did, and the
    * terminal has its own view of the screen. `lines` at or beyond the height of the band blanks it entirely, as the
    * terminal also would.
    *
    * Pure — `frame` is not modified and the returned buffer is new.
    */
  private[terminal] def shifted(frame: Buffer, region: RowRange, lines: Int, direction: ScrollDirection): Buffer =
    val moved = frame.snapshot
    if lines <= 0 then moved
    else
      val area   = frame.area
      val top    = math.max(area.y, region.top)
      val bottom = math.min(area.y + area.height - 1, region.bottom)
      if top > bottom then moved
      else
        val height    = bottom - top + 1
        // rows that survive the scroll; the rest of the band is what the terminal blanks
        val surviving = math.max(0, height - lines)
        moved.fill(Rect(area.x, top, area.width, height), Cell.Empty)
        if surviving > 0 then
          // `blit` rather than a cell loop: it is the one copy in the library that keeps a two-column grapheme and its
          // continuation column together, so a CJK or emoji row survives a scroll instead of being torn in half
          direction match
            case Up   =>
              moved.blit(frame, Position(area.x, top), Rect(area.x, top + lines, area.width, surviving))
            case Down =>
              moved.blit(frame, Position(area.x, top + lines), Rect(area.x, top, area.width, surviving))
        moved
