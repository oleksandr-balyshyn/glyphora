package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, Style, Text, Widget}

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
  * A message longer than the area is clipped, not wrapped: see [[Notice.heightOf]] and pass `wrap = true` for a notice
  * that should grow instead.
  */
final case class Notice(
    message: String,
    level: NoticeLevel = NoticeLevel.Info,
    timestamp: Option[LocalTime] = None,
    style: Style = Style.Default,
    accentStyle: Style = Style.Default,
    timestampStyle: Style = Style.Default.dim,
    icon: Option[String] = None,
    wrap: Boolean = false,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      var x = area.x
      timestamp.foreach: at =>
        val stamped = CharWidth.substringByWidth(s"[${Notice.Clock.format(at)}] ", area.right - x)
        buffer.setString(x, area.y, stamped, timestampStyle)
        x += CharWidth.of(stamped)
      if x < area.right then
        val marker = CharWidth.substringByWidth(s"${icon.getOrElse(level.icon)} ", area.right - x)
        buffer.setString(x, area.y, marker, accentStyle)
        x += CharWidth.of(marker)
      if x < area.right then
        if wrap then
          val body = Rect(x, area.y, area.right - x, area.height)
          Paragraph(Text.raw(message), wrap = true, style = style).render(body, buffer)
        else buffer.setString(x, area.y, CharWidth.substringByWidth(message, area.right - x), style)

  /** How many rows this notice needs at `width` — one unless it wraps. */
  def heightOf(width: Int): Int =
    if !wrap then 1
    else
      val prefix = CharWidth.of(prefixText)
      if width - prefix <= 0 then 1
      else Paragraph.heightOf(Text.raw(message), width - prefix)

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
