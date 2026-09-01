package io.worxbend.tui.terminal

import io.worxbend.tui.core.Size

/** The terminal window measured both ways it can be measured: in character cells, and in device pixels.
  *
  * One value rather than two separate calls, because the only useful thing to do with a pixel size is divide it by the
  * cell size — and a caller that read the two from separate calls could straddle a window resize and compute a cell
  * geometry that never existed on anyone's screen.
  *
  * `cells` is always known: every terminal can say how many columns and rows it has. `pixels` is `None` whenever the
  * terminal did not answer the query, which is the ordinary outcome rather than a failure — a great many terminals,
  * including most of the Windows ones, do not implement it at all. Callers must treat `None` as "unknown" and fall back
  * to an assumed cell shape; they must never read it as zero.
  */
final case class WindowSize(cells: Size, pixels: Option[Size]):

  /** How many pixels wide and tall one character cell is, or `None` when that cannot be worked out.
    *
    * `None` covers three cases and deliberately does not distinguish them, because a caller can do nothing different
    * about any of them: the terminal reported no pixels, it reported a zero-sized window, or it reported a zero-sized
    * cell grid. All three mean "assume a cell shape instead of measuring one".
    */
  def cellPixels: Option[Size] =
    pixels
      .filter(reported => reported.width > 0 && reported.height > 0 && cells.width > 0 && cells.height > 0)
      .map(reported => Size(reported.width / cells.width, reported.height / cells.height))

  /** How many times taller than wide one cell is — about `2.0` on a typical terminal, which is why the half-block image
    * renderers assume that number. `None` when [[cellPixels]] is `None`.
    */
  def cellAspectRatio: Option[Double] =
    cellPixels.filter(_.width > 0).map(cell => cell.height.toDouble / cell.width.toDouble)
