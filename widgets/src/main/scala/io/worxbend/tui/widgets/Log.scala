package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Line, Rect, StatefulWidget, Style}

import scala.collection.mutable

/** Caller-owned [[Log]] state: a bounded ring of lines plus follow-tail scrolling.
  *
  * While `follow` is on (the default), the view pins to the newest lines; scrolling up detaches it and scrolling back
  * to the bottom re-attaches.
  */
final class LogState(maxLines: Int = 1000):

  private val ring = mutable.ArrayDeque[Line]()
  var follow: Boolean = true
  var offset: Int = 0
  private[widgets] var lastViewportHeight: Int = 1

  def append(text: String): Unit = append(Line.raw(text))

  def append(line: Line): Unit =
    ring.append(line)
    while ring.size > maxLines do
      val _ = ring.removeHead()
      if !follow then offset = math.max(0, offset - 1)

  def size: Int = ring.size

  def clear(): Unit =
    ring.clear()
    offset = 0
    follow = true

  def scrollUp(count: Int = 1): Unit =
    follow = false
    offset = math.max(0, offset - count)

  /** Scrolling down past the end re-enables follow. `viewportHeight` defaults to the height of the last render, so
    * interactive scrolling needs no extra bookkeeping.
    */
  def scrollDown(count: Int = 1, viewportHeight: Int = -1): Unit =
    val height = if viewportHeight > 0 then viewportHeight else lastViewportHeight
    val maxOffset = math.max(0, ring.size - height)
    offset = math.min(maxOffset, offset + count)
    if offset >= maxOffset then follow = true

  private[widgets] def visibleSlice(height: Int): Seq[Line] =
    lastViewportHeight = height
    if follow then offset = math.max(0, ring.size - height)
    ring.slice(offset, offset + height).toSeq

/** An append-only scrolling text panel (build/log output, chat transcripts). */
final case class Log(style: Style = Style.Default) extends StatefulWidget[LogState]:

  def render(area: Rect, buffer: Buffer, state: LogState): Unit =
    if !area.isEmpty then
      state.visibleSlice(area.height).zipWithIndex.foreach { (line, row) =>
        val _ = LineRenderer.render(buffer, area.x, area.y + row, line, area.width, style)
      }
