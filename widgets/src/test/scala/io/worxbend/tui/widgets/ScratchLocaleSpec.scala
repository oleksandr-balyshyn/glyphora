package io.worxbend.tui.widgets

import java.util.Locale
import org.scalatest.funsuite.AnyFunSuite

final class ScratchLocaleSpec extends AnyFunSuite:
  test("turkish locale filter") {
    val previous = Locale.getDefault
    try
      Locale.setDefault(Locale.forLanguageTag("tr"))
      val table = DataTable(headers = Seq("ID", "Name"), rows = Seq(Seq("ID", "widget"), Seq("42", "other")))
      val state = DataTableState()
      state.filter = "i"
      val got = table.filteredRows(state)
      info(s"""needle 'i' -> ${got.size} rows; "ID".toLowerCase = ${"ID".toLowerCase}""")
      state.filter = "ı"
      info(s"needle 'dotless i' -> ${table.filteredRows(state).size} rows")
      assert(got.size == 1)
    finally Locale.setDefault(previous)
  }
