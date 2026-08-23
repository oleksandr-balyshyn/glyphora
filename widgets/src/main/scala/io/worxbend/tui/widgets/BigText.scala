package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Measured, Rect, Style, Widget}

/** Large banner text drawn with block glyphs from a built-in 3x5 pixel font (A–Z, 0–9, and common punctuation) — the
  * splash-screen and header building block. Unknown characters render as blanks; lowercase maps to uppercase. Each
  * glyph pixel is one terminal cell, glyphs are separated by one blank column.
  */
final case class BigText(
    content: String,
    style: Style = Style.Default,
    pixel: String = "█",
) extends Widget
    with Measured:

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

  /** The cells this banner occupies when rendered: one glyph box per character with a blank column between them. The
    * height it is given makes no difference — a glyph is always [[BigText.GlyphHeight]] rows, clipped if there is less
    * room than that.
    */
  override def widthAt(height: Int): Option[Int] =
    val _ = height
    Some(if content.isEmpty then 0 else content.length * (BigText.GlyphWidth + 1) - 1)

  /** A [[BigText]] line is exactly [[BigText.GlyphHeight]] rows tall, whatever width it is given. */
  override def heightAt(width: Int): Option[Int] =
    val _ = width
    Some(BigText.GlyphHeight)

object BigText:

  /** Columns one glyph occupies (glyphs are separated by one further blank column). */
  val GlyphWidth: Int = 3

  /** Rows one glyph occupies — the natural height of a [[BigText]] line. */
  val GlyphHeight: Int = 5

  private[widgets] val Blank: Vector[String] = Vector("...", "...", "...", "...", "...")

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
