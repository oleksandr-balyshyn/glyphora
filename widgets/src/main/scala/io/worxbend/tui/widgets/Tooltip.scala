package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, Style, Widget}

/** A small bordered popup of help text, meant to be layered near the thing it describes.
  *
  * Stateless: it draws itself into whatever area it is given. Use [[width]]/[[height]] to size the overlay before
  * placing it (the DSL `tooltip` helper anchors one next to a focused element).
  */
final case class Tooltip(
    text: String,
    style: Style = Style.Default,
    borderStyle: Style = Style.Default,
    borderType: BorderType = BorderType.Rounded,
) extends Widget:

  private def lines: Seq[String] = text.split("\n", -1).toIndexedSeq

  /** Natural width: the widest line plus a padding cell each side and the borders. */
  def width: Int = lines.map(CharWidth.of).maxOption.getOrElse(0) + 4

  /** Natural height: one row per line plus the top and bottom borders. */
  def height: Int = lines.size + 2

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val block = Block(borderType = borderType, borderStyle = borderStyle, padding = 0)
      block.render(area, buffer)
      val inner = block.inner(area)
      if !inner.isEmpty then
        lines.take(inner.height).zipWithIndex.foreach { (line, row) =>
          val fitted = CharWidth.substringByWidth(line, inner.width - 1)
          buffer.setString(inner.x + 1, inner.y + row, fitted, style)
        }
