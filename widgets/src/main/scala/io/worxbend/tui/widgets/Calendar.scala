package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, Style, Widget}

import java.time.format.TextStyle as JTextStyle
import java.time.{DayOfWeek, LocalDate, Year, YearMonth}
import java.util.Locale

/** A month grid: an optional title row, an optional weekday header, and the day numbers, with an optional highlighted
  * day.
  *
  * `showTitle` and `showWeekdays` do more than blank a row — the row is not reserved at all, so a grid with neither
  * header starts its first week on the very first row of the area and needs two rows fewer. That is what makes a bare
  * month grid usable in a narrow sidebar.
  *
  * `firstDayOfWeek` decides which weekday the leftmost column is, so a US-facing application asks for
  * `DayOfWeek.SUNDAY` and gets a Sunday-first grid with the day numbers moved to match. `locale` decides the language
  * of the month name and the weekday abbreviations. It defaults to `Locale.ENGLISH` rather than to
  * `Locale.getDefault`, deliberately: a widget whose output depends on the machine it runs on cannot be tested by
  * comparing frames, and a caller who wants the machine's locale can pass `Locale.getDefault` and mean it.
  *
  * `dayStyles` gives individual dates their own appearance — a date with an appointment, a public holiday, today,
  * every day of a streak. It is keyed by `java.time.LocalDate` rather than by day-of-month so a caller can hold one map
  * across several months and hand the same value to each grid. A date's style is layered over the calendar's `style`,
  * and `highlightStyle` is layered over that in turn, so the cursor stays visible wherever it lands: a marked day that
  * is also the selected day reads as selected first.
  *
  * Needs 20 columns (seven three-column day slots, the last one two wide) and one row per header shown plus one row
  * per week the month touches — so up to 8 rows with both headers, and up to 6 with neither. Overflow clips like
  * everything else.
  */
final case class Calendar(
    year: Int,
    month: Int,
    selected: Option[Int] = None,
    showTitle: Boolean = true,
    showWeekdays: Boolean = true,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    locale: Locale = Locale.ENGLISH,
    style: Style = Style.Default,
    headerStyle: Style = Style.Default.bold,
    highlightStyle: Style = Style.Default.reverse,
    dayStyles: Map[LocalDate, Style] = Map.empty,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      // prev/next-month navigation naturally produces month 0 and 13, and every other widget here clips rather than
      // throwing out of `render`; `YearMonth.of` would raise `DateTimeException`
      val yearMonth = YearMonth.of(
        math.max(Year.MIN_VALUE, math.min(Year.MAX_VALUE, year)),
        math.max(1, math.min(12, month)),
      )
      if showTitle then drawTitle(area, buffer, yearMonth)
      if showWeekdays then drawWeekdayHeader(area, buffer)
      drawDays(area, buffer, yearMonth)

  private def drawTitle(area: Rect, buffer: Buffer, yearMonth: YearMonth): Unit =
    val title  = s"${yearMonth.getMonth.getDisplayName(JTextStyle.FULL, locale)} ${yearMonth.getYear}"
    val fitted = CharWidth.substringByWidth(title, area.width)
    val startX = Alignment.Center.originAt(area.x, math.min(area.width, GridWidth), CharWidth.of(fitted))
    buffer.setString(startX, area.y, fitted, headerStyle)

  private def drawWeekdayHeader(area: Rect, buffer: Buffer): Unit =
    val header = weekDays.map(weekdayLabel).mkString(" ")
    val y      = if showTitle then area.y + 1 else area.y
    buffer.setString(area.x, y, CharWidth.substringByWidth(header, area.width), headerStyle)

  /** One weekday abbreviation, cut and padded to exactly the two columns a day slot is wide.
    *
    * The cut goes through `CharWidth` rather than through the string's character count, because a locale whose
    * abbreviations are CJK characters (Japanese `月`, `火`, ...) puts two *columns* in one character, and taking two
    * characters there would push every following column one place to the right.
    */
  private def weekdayLabel(day: DayOfWeek): String =
    val cut = CharWidth.substringByWidth(day.getDisplayName(JTextStyle.SHORT, locale), DayColumnWidth)
    cut + " " * math.max(0, DayColumnWidth - CharWidth.of(cut))

  private def drawDays(area: Rect, buffer: Buffer, yearMonth: YearMonth): Unit =
    val firstColumn = columnOf(yearMonth.atDay(1))
    (1 to yearMonth.lengthOfMonth).foreach { day =>
      val slot     = firstColumn + day - 1
      val x        = area.x + (slot % 7) * 3
      val y        = gridTop(area) + slot / 7
      val date     = yearMonth.atDay(day)
      // three layers, outermost last: the calendar's own style, then whatever this date was given, then the cursor
      val marked   = dayStyles.get(date).fold(style)(style.patch)
      val dayStyle = if selected.contains(day) then marked.patch(highlightStyle) else marked
      // a grid cell is two columns wide: drop the ones the area cannot hold rather than write past its edges
      if x + 2 <= area.right && y < area.bottom then buffer.setString(x, y, f"$day%2d", dayStyle)
    }

  /** The first row the day grid may use: whichever headers are shown come first, and a header that is switched off
    * gives its row back to the grid rather than leaving it blank.
    */
  private def gridTop(area: Rect): Int =
    area.y + (if showTitle then 1 else 0) + (if showWeekdays then 1 else 0)

  /** Column index of a date's weekday, counting from `firstDayOfWeek`. `floorMod` rather than `%` because the
    * subtraction is negative for every weekday that falls before the configured start of the week.
    */
  private def columnOf(date: LocalDate): Int =
    math.floorMod(date.getDayOfWeek.getValue - firstDayOfWeek.getValue, 7)

  /** The seven weekdays in the order this calendar's columns run. */
  private def weekDays: Seq[DayOfWeek] = (0 until 7).map(offset => firstDayOfWeek.plus(offset.toLong))

  private val DayColumnWidth = 2
  private val GridWidth      = 20
