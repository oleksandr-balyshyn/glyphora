package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Measured, Rect, Style, Text, Widget}

/** A small bordered popup of help text, meant to be layered near the thing it describes.
  *
  * Stateless: it draws itself into whatever area it is given. Ask [[widthAt]]/[[heightAt]] for the box to size the
  * overlay to before placing it (the DSL `tooltip` helper anchors one next to a focused element); neither depends on
  * the other axis, because the text is never wrapped.
  */
final case class Tooltip(
    text: String,
    style: Style = Style.Default,
    borderStyle: Style = Style.Default,
    borderType: BorderType = BorderType.Rounded,
) extends Widget
    with Measured:

  private def lines: Seq[String] = Text.splitLines(text).toIndexedSeq

  /** Natural width: the widest line plus a padding cell each side and the borders. Independent of the rows given. */
  override def widthAt(height: Int): Option[Int] =
    val _ = height
    Some(lines.map(CharWidth.of).maxOption.getOrElse(0) + 4)

  /** Natural height: one row per line plus the top and bottom borders. Independent of the columns given. */
  override def heightAt(width: Int): Option[Int] =
    val _ = width
    Some(lines.size + 2)

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      // the blank column either side of the text is real padding, not a leading space baked into the string, so
      // `inner` already reports the columns the text may use and nothing here re-derives the same offset
      val block = Block(borderType = borderType, borderStyle = borderStyle, padding = Padding.horizontal(1))
      block.render(area, buffer)
      val inner = block.inner(area)
      if !inner.isEmpty then
        lines.take(inner.height).zipWithIndex.foreach { (line, row) =>
          val fitted = CharWidth.substringByWidth(line, inner.width)
          buffer.setString(inner.x, inner.y + row, fitted, style)
        }
