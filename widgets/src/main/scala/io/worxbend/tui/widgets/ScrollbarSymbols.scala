package io.worxbend.tui.widgets

/** The glyphs a [[Scrollbar]] paints with: the track it draws along its whole length, the thumb it draws over the part
  * of the track the viewport covers, and two optional arrow caps drawn in the strip's first and last cell.
  *
  * A cap costs a cell of track, so a bar with both caps has two fewer cells to place the thumb in. `None` — no cap — is
  * therefore the right choice for a short strip, and for a terminal whose font has no arrow glyphs; it is also what a
  * `Scrollbar` draws when nothing is overridden, so the presets below are opt-in and change nothing on their own.
  *
  * A plain immutable value with no state and no thread constraints: build one anywhere, share it freely.
  *
  * @param track
  *   the cell drawn everywhere the thumb is not
  * @param thumb
  *   the cell drawn where the thumb is
  * @param begin
  *   the cap drawn in the first cell of the strip — the top of a vertical bar, the left end of a horizontal one — or
  *   `None` for no cap, leaving that cell as ordinary track
  * @param end
  *   the cap drawn in the last cell of the strip, or `None`
  */
final case class ScrollbarSymbols(
    track: String,
    thumb: String,
    begin: Option[String] = None,
    end: Option[String] = None,
)

object ScrollbarSymbols:

  /** Track and thumb only, no caps — the shape a `Scrollbar` draws when nothing is overridden.
    *
    * Two places take their default glyphs from here: `Scrollbar`'s own parameter defaults, and the DSL's `scrollbar`
    * factory. Changing either glyph changes the look of every scrollbar in both layers at once.
    */
  val Plain: ScrollbarSymbols = ScrollbarSymbols("│", "█")

  /** A single-line vertical bar capped with `↑` and `↓`. */
  val Vertical: ScrollbarSymbols = ScrollbarSymbols("│", "█", Some("↑"), Some("↓"))

  /** A single-line horizontal bar capped with `←` and `→`. */
  val Horizontal: ScrollbarSymbols = ScrollbarSymbols("─", "█", Some("←"), Some("→"))

  /** A double-line vertical bar capped with the solid triangles `▲` and `▼`. */
  val DoubleVertical: ScrollbarSymbols = ScrollbarSymbols("║", "█", Some("▲"), Some("▼"))

  /** A double-line horizontal bar capped with the solid triangles `◄` and `►`. */
  val DoubleHorizontal: ScrollbarSymbols = ScrollbarSymbols("═", "█", Some("◄"), Some("►"))

  /** Nothing outside printable ASCII, for a terminal or font that draws box-drawing characters badly or not at all. */
  val Ascii: ScrollbarSymbols = ScrollbarSymbols("|", "#", Some("^"), Some("v"))
