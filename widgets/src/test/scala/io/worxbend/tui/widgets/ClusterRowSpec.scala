package io.worxbend.tui.widgets

import org.scalatest.funsuite.AnyFunSuite

/** Pins the measurement and scroll rules [[TextInput]] and [[TextArea]] both delegate to. */
final class ClusterRowSpec extends AnyFunSuite:

  test("every cluster measures at least one column, so measurement matches what is drawn") {
    assert(ClusterRow.renderedWidth("a") == 1)
    assert(ClusterRow.renderedWidth("漢") == 2)
    assert(ClusterRow.renderedWidth("́") == 1) // a bare combining acute accent measures zero, drawn in one cell
  }

  test("a zero-width cluster is drawn as a blank rather than as nothing") {
    assert(ClusterRow.drawnSymbol("́") == " ")
    assert(ClusterRow.drawnSymbol("a") == "a")
  }

  test("the rightmost useful scroll leaves room for the end-of-row cursor") {
    val clusters = Vector("a", "b", "c", "d")
    // width 3 fits two clusters plus the one-column cursor, so the last useful offset is index 2
    assert(ClusterRow.rightmostUsefulScroll(clusters, 3) == 2)
    assert(ClusterRow.rightmostUsefulScroll(clusters, 99) == 0)
  }

  test("scrolling reserves the display width of the cluster under the cursor") {
    val wide = Vector("漢", "字", "表")
    // the cursor sits on a two-column cluster, so a three-column window cannot also show the cluster before it
    assert(ClusterRow.scrolledTo(wide, 0, 1, 3) == 1)
    assert(ClusterRow.scrolledTo(wide, 0, 1, 4) == 0)
  }

  test("an offset left over from wider text or longer content is pulled back") {
    assert(ClusterRow.scrolledTo(Vector("a", "b"), 5, 0, 10) == 0)
  }
