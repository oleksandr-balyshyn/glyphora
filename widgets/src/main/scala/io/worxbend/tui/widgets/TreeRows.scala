package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, Style}

/** The render skeleton every tree-shaped widget in this module shares: re-derive the scroll offset from the selection,
  * slice the visible window out of the rows, and paint each one with the highlight style patched over the selected row.
  *
  * The widgets differ only in what a row *is* and what its text says — [[Tree]] renders node paths, [[DirectoryTree]]
  * renders filesystem entries — so that part is the caller's, as `isSelected` and `rowText`. Keeping the offset
  * arithmetic and the painting here is what stops the two drifting apart the way they did before [[ScrollWindow]]
  * existed.
  */
private[widgets] object TreeRows:

  /** Paints the window of `rows` that `offset` and `selectedIndex` imply, and returns the offset it painted at so the
    * caller can write it back into its state.
    *
    * `isSelected` decides which row wears `style.patch(highlightStyle)`; `rowText` is the row's full text before it is
    * clipped to the area's width.
    */
  def render[T](
      area: Rect,
      buffer: Buffer,
      rows: Seq[T],
      offset: Int,
      selectedIndex: Option[Int],
      isSelected: T => Boolean,
      rowText: T => String,
      style: Style,
      highlightStyle: Style,
  ): Int =
    val newOffset = ScrollWindow.offsetFor(offset, selectedIndex, rows.size, area.height)
    rows.slice(newOffset, newOffset + area.height).zipWithIndex.foreach { (item, row) =>
      val rowStyle = if isSelected(item) then style.patch(highlightStyle) else style
      val text     = CharWidth.substringByWidth(rowText(item), area.width)
      buffer.setString(area.x, area.y + row, text, rowStyle)
    }
    newOffset
