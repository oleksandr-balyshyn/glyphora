package io.worxbend.tui.widgets

import org.scalatest.funsuite.AnyFunSuite

final class ScratchMenuPerfSpec extends AnyFunSuite:
  private def build(n: Int): List[MenuEntry] =
    List.tabulate(n)(i => if i == n - 1 then MenuEntry.Item("go") else MenuEntry.Separator)

  private def timeMs(n: Int): Long =
    val items = build(n)
    val st    = new MenuState(Some(0), 0)
    val t0    = System.nanoTime()
    st.selectNext(items)
    (System.nanoTime() - t0) / 1000000L

  test("growth") {
    for n <- Seq(1000, 5000, 20000, 50000) do
      println(s"n=$n  list=${timeMs(n)}ms  vector=${
        val items = build(n).toVector
        val st = new MenuState(Some(0), 0)
        val t0 = System.nanoTime()
        st.selectNext(items)
        (System.nanoTime() - t0) / 1000000L
      }ms")
  }
