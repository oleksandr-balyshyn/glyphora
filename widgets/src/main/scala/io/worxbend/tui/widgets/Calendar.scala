package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, Style, Widget}

import java.time.format.TextStyle as JTextStyle
import java.time.{DayOfWeek, LocalDate, Year, YearMonth}
import java.util.Locale

/** A month grid: title row, weekday header (weeks start Monday), and day numbers with an optional highlighted day.
  *
  * Needs 20 columns (seven three-column day slots, the last one two wide) and `2 + weeks` rows — the title, the weekday
  * header, then one row per week the month touches, so up to 8. Overflow clips like everything else.
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
      // prev/next-month navigation naturally produces month 0 and 13, and every other widget here clips rather than
      // throwing out of `render`; `YearMonth.of` would raise `DateTimeException`
      val yearMonth = YearMonth.of(
        math.max(Year.MIN_VALUE, math.min(Year.MAX_VALUE, year)),
        math.max(1, math.min(12, month)),
      )
      drawTitle(area, buffer, yearMonth)
      drawWeekdayHeader(area, buffer)
      drawDays(area, buffer, yearMonth)

  private def drawTitle(area: Rect, buffer: Buffer, yearMonth: YearMonth): Unit =
    val title  = s"${yearMonth.getMonth.getDisplayName(JTextStyle.FULL, Locale.ENGLISH)} ${yearMonth.getYear}"
    val fitted = CharWidth.substringByWidth(title, area.width)
    val startX = Alignment.Center.originAt(area.x, math.min(area.width, GridWidth), CharWidth.of(fitted))
    buffer.setString(startX, area.y, fitted, headerStyle)

  private def drawWeekdayHeader(area: Rect, buffer: Buffer): Unit =
    val header = WeekDays.map(_.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH).take(2)).mkString(" ")
    buffer.setString(area.x, area.y + 1, CharWidth.substringByWidth(header, area.width), headerStyle)

  private def drawDays(area: Rect, buffer: Buffer, yearMonth: YearMonth): Unit =
    val firstColumn = columnOf(yearMonth.atDay(1))
    (1 to yearMonth.lengthOfMonth).foreach { day =>
      val slot     = firstColumn + day - 1
      val x        = area.x + (slot % 7) * 3
      val y        = area.y + 2 + slot / 7
      val dayStyle = if selected.contains(day) then style.patch(selectedStyle) else style
      // a grid cell is two columns wide: drop the ones the area cannot hold rather than write past its edges
      if x + 2 <= area.right && y < area.bottom then buffer.setString(x, y, f"$day%2d", dayStyle)
    }

  /** Monday-first column index of a date's weekday. */
  private def columnOf(date: LocalDate): Int =
    date.getDayOfWeek.getValue - DayOfWeek.MONDAY.getValue

  private val WeekDays: Seq[DayOfWeek] = DayOfWeek.values.toSeq
  private val GridWidth                = 20
