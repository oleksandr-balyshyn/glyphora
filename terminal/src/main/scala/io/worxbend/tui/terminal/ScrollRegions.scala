package io.worxbend.tui.terminal

import io.worxbend.tui.core.Size

import org.jline.terminal.Terminal

import scala.util.control.NonFatal

/** Hardware scroll regions for [[JLine3Backend]]: confining scrolling to a band of rows (DECSTBM), scrolling the band,
  * and releasing the region again.
  *
  * The release is not deferred to teardown: a region left set makes every later scroll — this app's, and the user's
  * shell after it exits — refuse to touch the rest of the screen.
  *
  * The diff baseline is shifted to match, so the next frame writes only the rows the scroll newly exposed. That shift
  * is the entire saving. Without it the following frame would find every row of the band changed and repaint the lot,
  * which is the work a scroll exists to avoid.
  *
  * Render thread only: the baseline shift touches the render-thread-private [[FrameBaseline]]. The write goes out under
  * the backend's `screenOwnership` monitor, like every write, so a Ctrl+Z handover cannot interleave with it.
  */
private[terminal] final class ScrollRegions(
    screenOwnership: AnyRef,
    terminal: Terminal,
    baseline: FrameBaseline,
):

  /** Confines scrolling to `region`, scrolls it by `lines` in `direction`, and releases the region again.
    *
    * A region reaching past the bottom of the terminal is refused rather than clamped: clamping would scroll a band the
    * caller did not name, and the wrong rows moving is far harder to notice than a rejected call. `lines <= 0` is a
    * successful no-op, so a caller computing a delta needs no guard.
    */
  def scroll(region: RowRange, lines: Int, direction: ScrollDirection): Either[BackendError, Unit] =
    if lines <= 0 then Right(())
    else
      size.flatMap { terminalSize =>
        if region.bottom >= terminalSize.height then
          Left(BackendError.UnsupportedTerminal(s"row range $region does not fit a terminal of $terminalSize"))
        else
          attempt {
            screenOwnership.synchronized {
              terminal
                .writer()
                .write(
                  AnsiSequences.setScrollRegion(region.top, region.bottom) +
                    ScrollDirection.sequence(direction, lines) +
                    AnsiSequences.ResetScrollRegion
                )
              terminal.writer().flush()
            }
            baseline.shift(region, lines, direction)
          }
      }

  private def size: Either[BackendError, Size] =
    attempt {
      val jlineSize = terminal.getSize
      Size(jlineSize.getColumns, jlineSize.getRows)
    }

  private def attempt[A](body: => A): Either[BackendError, A] =
    try Right(body)
    catch case NonFatal(error) => Left(BackendError.Io(error))
