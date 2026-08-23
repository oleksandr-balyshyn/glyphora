package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Line, Rect, StatefulWidget, Style}

import scala.collection.mutable

/** Caller-owned [[Log]] state: a bounded ring of lines plus follow-tail scrolling.
  *
  * While `follow` is on (the default), the view pins to the newest lines; scrolling up detaches it and scrolling back
  * to the bottom re-attaches.
  */
final class LogState(maxLines: Int = 1000):

  private val ring                             = mutable.ArrayDeque[Line]()
  var follow: Boolean                          = true
  var offset: Int                              = 0
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

  /** Scrolls `count` lines towards the newest ones, against the viewport of the last render; scrolling down past the
    * end re-enables follow. The mirror of [[scrollUp]].
    *
    * There is no viewport parameter: by the time any key or wheel handler runs, the widget has already rendered at
    * least once and `lastViewportHeight` is the height the user is looking at. A caller-supplied height could only
    * disagree with what is on screen. Render thread only, like every other method here.
    */
  def scrollDown(count: Int = 1): Unit =
    val maxOffset = math.max(0, ring.size - lastViewportHeight)
    offset = math.min(maxOffset, offset + count)
    if offset >= maxOffset then follow = true

  private[widgets] def visibleSlice(height: Int): Seq[Line] =
    lastViewportHeight = height
    // `offset` is a public var and the viewport can grow between frames, so clamp on the way out as well as on the
    // way in — an offset past the last useful row renders the tail of the log followed by blank rows, or nothing.
    offset = math.max(0, math.min(offset, math.max(0, ring.size - height)))
    if follow then offset = math.max(0, ring.size - height)
    ring.slice(offset, offset + height).toSeq

/** An append-only scrolling text panel (build/log output, chat transcripts). */
final case class Log(style: Style = Style.Default) extends StatefulWidget[LogState]:

  def render(area: Rect, buffer: Buffer, state: LogState): Unit =
    if !area.isEmpty then
      state.visibleSlice(area.height).zipWithIndex.foreach { (line, row) =>
        val _ = LineRenderer.render(buffer, area.x, area.y + row, line, area.width, style)
      }
