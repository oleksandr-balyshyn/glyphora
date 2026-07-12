package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, Style, Widget}

import java.time.format.TextStyle as JTextStyle
import java.time.{DayOfWeek, LocalDate, YearMonth}
import java.util.Locale

/** A month grid: title row, weekday header (weeks start Monday), and day numbers with an optional highlighted
  * day. Needs 20 columns and `3 + weeks` rows to show fully; overflow clips like everything else.
  */
final case class Calendar(
    year: Int,
    month: Int,
    selected: Option[Int] = None,
    style: Style = Style.Default,
    headerStyle: Style = Style.Default.bold,
    selectedStyle: Style = Style.Default.reverse,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val yearMonth = YearMonth.of(year, month)
      drawTitle(area, buffer, yearMonth)
      drawWeekdayHeader(area, buffer)
      drawDays(area, buffer, yearMonth)

  private def drawTitle(area: Rect, buffer: Buffer, yearMonth: YearMonth): Unit =
    val title = s"${yearMonth.getMonth.getDisplayName(JTextStyle.FULL, Locale.ENGLISH)} $year"
    val fitted = CharWidth.substringByWidth(title, area.width)
    val offset = (math.min(area.width, GridWidth) - CharWidth.of(fitted)) / 2
    buffer.setString(area.x + math.max(0, offset), area.y, fitted, headerStyle)

  private def drawWeekdayHeader(area: Rect, buffer: Buffer): Unit =
    val header = WeekDays.map(_.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH).take(2)).mkString(" ")
    buffer.setString(area.x, area.y + 1, CharWidth.substringByWidth(header, area.width), headerStyle)

  private def drawDays(area: Rect, buffer: Buffer, yearMonth: YearMonth): Unit =
    val firstColumn = columnOf(yearMonth.atDay(1))
    (1 to yearMonth.lengthOfMonth).foreach { day =>
      val slot = firstColumn + day - 1
      val x = area.x + (slot % 7) * 3
      val y = area.y + 2 + slot / 7
      val dayStyle = if selected.contains(day) then style.patch(selectedStyle) else style
      buffer.setString(x, y, f"$day%2d", dayStyle)
    }

  /** Monday-first column index of a date's weekday. */
  private def columnOf(date: LocalDate): Int =
    date.getDayOfWeek.getValue - DayOfWeek.MONDAY.getValue

  private val WeekDays: Seq[DayOfWeek] = DayOfWeek.values.toSeq
  private val GridWidth = 20
