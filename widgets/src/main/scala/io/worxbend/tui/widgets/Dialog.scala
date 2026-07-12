package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Line, Rect, Style, Text, Widget}

/** A modal-style dialog drawn over existing content: clears a centered box, borders it, renders the message and a row
  * of buttons with one highlighted.
  *
  * There is no screen stack (SPEC.md §7 non-goal) — a dialog is just an element rendered last in the view, so it paints
  * over whatever was drawn before it.
  */
final case class Dialog(
    title: String,
    message: Text,
    buttons: Seq[String] = Seq("OK"),
    selectedButton: Int = 0,
    style: Style = Style.Default,
    borderType: BorderType = BorderType.Double,
    selectedStyle: Style = Style.Default.reverse,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    val box = Dialog.centered(area, math.min(area.width, math.max(message.width + 4, 20)), message.height + 4)
    if box.width >= 4 && box.height >= 4 then
      clear(box, buffer)
      Block(Some(Line.styled(title, style)), borderType, style).render(box, buffer)
      val inner = box.inset(1)
      Paragraph(message, alignment = Alignment.Center, style = style).render(
        Rect(inner.x, inner.y, inner.width, inner.height - 1),
        buffer,
      )
      renderButtons(inner, buffer)

  private def clear(box: Rect, buffer: Buffer): Unit =
    var y = box.y
    while y < box.bottom do
      var x = box.x
      while x < box.right do
        buffer.set(x, y, Cell(" ", style))
        x += 1
      y += 1

  private def renderButtons(inner: Rect, buffer: Buffer): Unit =
    val labels = buttons.map(label => s"[ $label ]")
    val totalWidth = labels.map(CharWidth.of).sum + math.max(0, labels.size - 1)
    var x = inner.x + math.max(0, (inner.width - totalWidth) / 2)
    val y = inner.bottom - 1
    labels.zipWithIndex.foreach { (label, index) =>
      val buttonStyle = if index == selectedButton then style.patch(selectedStyle) else style
      buffer.setString(x, y, label, buttonStyle)
      x += CharWidth.of(label) + 1
    }

object Dialog:
  /** A `width` x `height` box centered inside `area`, clamped to fit. */
  def centered(area: Rect, width: Int, height: Int): Rect =
    val w = math.min(width, area.width)
    val h = math.min(height, area.height)
    Rect(area.x + (area.width - w) / 2, area.y + (area.height - h) / 2, w, h)
