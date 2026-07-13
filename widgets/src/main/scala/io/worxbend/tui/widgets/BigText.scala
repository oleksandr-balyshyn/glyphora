package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Rect, Style, Widget}

/** Large banner text drawn with block glyphs from a built-in 3x5 pixel font (A–Z, 0–9, and common punctuation) — the
  * splash-screen and header building block. Unknown characters render as blanks; lowercase maps to uppercase. Each
  * glyph pixel is one terminal cell, glyphs are separated by one blank column.
  */
final case class BigText(
    content: String,
    style: Style = Style.Default,
    pixel: String = "█",
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      var x = area.x
      content.foreach { char =>
        val glyph = BigText.Font.getOrElse(char.toUpper, BigText.Blank)
        if x + BigText.GlyphWidth <= area.right then
          glyph.zipWithIndex.foreach { (row, dy) =>
            if dy < area.height then
              row.zipWithIndex.foreach { (bit, dx) =>
                if bit == '#' then buffer.set(x + dx, area.y + dy, Cell(pixel, style))
              }
          }
        x += BigText.GlyphWidth + 1
      }

object BigText:

  /** Rows x columns of one glyph. */
  val GlyphWidth: Int  = 3
  val GlyphHeight: Int = 5

  private[widgets] val Blank: Vector[String] = Vector("...", "...", "...", "...", "...")

  /** The width in cells `content` occupies when rendered. */
  def widthOf(content: String): Int =
    if content.isEmpty then 0 else content.length * (GlyphWidth + 1) - 1

  private[widgets] val Font: Map[Char, Vector[String]] = Map(
    ' ' -> Blank,
    '0' -> Vector("###", "#.#", "#.#", "#.#", "###"),
    '1' -> Vector(".#.", "##.", ".#.", ".#.", "###"),
    '2' -> Vector("###", "..#", "###", "#..", "###"),
    '3' -> Vector("###", "..#", "###", "..#", "###"),
    '4' -> Vector("#.#", "#.#", "###", "..#", "..#"),
    '5' -> Vector("###", "#..", "###", "..#", "###"),
    '6' -> Vector("###", "#..", "###", "#.#", "###"),
    '7' -> Vector("###", "..#", "..#", "..#", "..#"),
    '8' -> Vector("###", "#.#", "###", "#.#", "###"),
    '9' -> Vector("###", "#.#", "###", "..#", "###"),
    'A' -> Vector("###", "#.#", "###", "#.#", "#.#"),
    'B' -> Vector("##.", "#.#", "##.", "#.#", "##."),
    'C' -> Vector("###", "#..", "#..", "#..", "###"),
    'D' -> Vector("##.", "#.#", "#.#", "#.#", "##."),
    'E' -> Vector("###", "#..", "###", "#..", "###"),
    'F' -> Vector("###", "#..", "###", "#..", "#.."),
    'G' -> Vector("###", "#..", "#.#", "#.#", "###"),
    'H' -> Vector("#.#", "#.#", "###", "#.#", "#.#"),
    'I' -> Vector("###", ".#.", ".#.", ".#.", "###"),
    'J' -> Vector("..#", "..#", "..#", "#.#", "###"),
    'K' -> Vector("#.#", "#.#", "##.", "#.#", "#.#"),
    'L' -> Vector("#..", "#..", "#..", "#..", "###"),
    'M' -> Vector("#.#", "###", "#.#", "#.#", "#.#"),
    'N' -> Vector("##.", "#.#", "#.#", "#.#", "#.#"),
    'O' -> Vector("###", "#.#", "#.#", "#.#", "###"),
    'P' -> Vector("###", "#.#", "###", "#..", "#.."),
    'Q' -> Vector("###", "#.#", "#.#", "###", "..#"),
    'R' -> Vector("###", "#.#", "##.", "#.#", "#.#"),
    'S' -> Vector("###", "#..", "###", "..#", "###"),
    'T' -> Vector("###", ".#.", ".#.", ".#.", ".#."),
    'U' -> Vector("#.#", "#.#", "#.#", "#.#", "###"),
    'V' -> Vector("#.#", "#.#", "#.#", "#.#", ".#."),
    'W' -> Vector("#.#", "#.#", "#.#", "###", "#.#"),
    'X' -> Vector("#.#", "#.#", ".#.", "#.#", "#.#"),
    'Y' -> Vector("#.#", "#.#", ".#.", ".#.", ".#."),
    'Z' -> Vector("###", "..#", ".#.", "#..", "###"),
    '-' -> Vector("...", "...", "###", "...", "..."),
    '.' -> Vector("...", "...", "...", "...", ".#."),
    ':' -> Vector("...", ".#.", "...", ".#.", "..."),
    '!' -> Vector(".#.", ".#.", ".#.", "...", ".#."),
    '?' -> Vector("###", "..#", ".#.", "...", ".#."),
    '%' -> Vector("#.#", "..#", ".#.", "#..", "#.#"),
    '/' -> Vector("..#", "..#", ".#.", "#..", "#.."),
  )
