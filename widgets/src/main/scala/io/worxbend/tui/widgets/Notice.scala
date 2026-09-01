package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Measured, Rect, Style, Text, Widget}

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** One styled message line: an icon, an optional timestamp, and the message.
  *
  * `[12:04:31] ✔ deployed 8 services`
  *
  * The timestamp is passed in rather than read from the clock, because a widget that called `LocalTime.now()` would
  * render differently on every frame — the frame would stop being a pure function of its inputs, golden-frame tests
  * would be impossible, and a redraw triggered by something else entirely would silently change the displayed time.
  * Stamp the message when the event happens, which is the moment the reader actually cares about.
  *
  * A message longer than the area is clipped, not wrapped. Pass `overflow = Overflow.Wrap` for a notice that should
  * grow onto further rows instead, and ask [[heightAt]] how many rows that turns out to be.
  */
final case class Notice(
    message: String,
    level: NoticeLevel = NoticeLevel.Info,
    timestamp: Option[LocalTime] = None,
    style: Style = Style.Default,
    accentStyle: Style = Style.Default,
    timestampStyle: Style = Style.Default.dim,
    icon: Option[String] = None,
    overflow: Overflow = Overflow.Clip,
) extends Widget
    with Measured:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val cursor = RowCursor(buffer, area.y, area.x, area.right)
      timestamp.foreach(at => cursor.write(s"[${Notice.Clock.format(at)}] ", timestampStyle))
      cursor.write(s"${icon.getOrElse(level.icon)} ", accentStyle)
      if overflow.wraps then
        // the body gets whatever the prefix left; an empty rect renders nothing, which is the "no room" case
        body.render(Rect(cursor.at, area.y, cursor.remaining, area.height), buffer)
      else cursor.write(message, style)

  /** How many rows this notice needs at `width` — one unless it wraps, and then however many rows the message needs in
    * the columns the prefix leaves it. Always an answer: a notice always knows its own height.
    */
  override def heightAt(width: Int): Option[Int] =
    if !overflow.wraps then Some(1)
    else
      val bodyWidth = width - CharWidth.of(prefixText)
      if bodyWidth <= 0 then Some(1) else body.heightAt(bodyWidth)

  /** The message on its own, drawn the way this notice draws it — one owner for both the wrapped render and the
    * measurement of it, so they cannot disagree about how many rows the text takes.
    */
  private def body: Paragraph = Paragraph(Text.raw(message), overflow = overflow, style = style)

  /** Everything drawn before the message — the timestamp and the icon — so a caller can measure the room the body has
    * left.
    */
  def prefixText: String =
    val stamp = timestamp.fold("")(at => s"[${Notice.Clock.format(at)}] ")
    s"$stamp${icon.getOrElse(level.icon)} "

object Notice:

  /** Seconds precision: a notice is a human-facing log line, and milliseconds are noise at that scale. */
  private val Clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

  def success(message: String, style: Style = Style.Default): Notice =
    Notice(message, NoticeLevel.Success, accentStyle = style)

  def info(message: String, style: Style = Style.Default): Notice =
    Notice(message, NoticeLevel.Info, accentStyle = style)

  def warning(message: String, style: Style = Style.Default): Notice =
    Notice(message, NoticeLevel.Warning, accentStyle = style)

  def error(message: String, style: Style = Style.Default): Notice =
    Notice(message, NoticeLevel.Error, accentStyle = style)
