package io.worxbend.tui.widgets

import java.util.Locale

import io.worxbend.tui.core.Constraint
import org.scalatest.funsuite.AnyFunSuite

/** Regression coverage for the Turkish-locale filter bug: `DataTable.filteredRows` used to lowercase with the
  * platform's default locale, so under `Locale.forLanguageTag("tr")` `"ID".toLowerCase` is `"ıd"` (dotless i) and a
  * filter needle of `"i"` matched nothing. The fix lowercases with [[Locale.ROOT]], the same convention
  * `core.KeyEvent.keyCodeFor` and `terminal.ColorDepth` already use for exactly this reason.
  */
final class DataTableLocaleSpec extends AnyFunSuite:

  private def withDefaultLocale[A](locale: Locale)(body: => A): A =
    val previous = Locale.getDefault
    try
      Locale.setDefault(locale)
      body
    finally Locale.setDefault(previous)

  private val table =
    DataTable.fromStrings(
      columns = Seq("ID", "Name"),
      rows = Seq(Seq("ID", "widget"), Seq("42", "other")),
      widths = Seq(Constraint.Length(4), Constraint.Length(8)),
    )

  test("filter needle 'i' matches the row containing 'ID' regardless of the default locale") {
    withDefaultLocale(Locale.forLanguageTag("tr")) {
      val state = DataTableState()
      state.setFilter("i")
      assert(table.filteredRows(state).size == 1)
    }
  }

  test("filter needle 'ı' (dotless i) matches nothing, under any default locale") {
    withDefaultLocale(Locale.forLanguageTag("tr")) {
      val state = DataTableState()
      state.setFilter("ı")
      assert(table.filteredRows(state).isEmpty)
    }
  }

  test("filtering is locale-independent: the Turkish and ROOT locales agree on the same needle") {
    val needle                          = "i"
    def matchCount(locale: Locale): Int = withDefaultLocale(locale) {
      val state = DataTableState()
      state.setFilter(needle)
      table.filteredRows(state).size
    }
    assert(matchCount(Locale.forLanguageTag("tr")) == matchCount(Locale.ROOT))
  }
