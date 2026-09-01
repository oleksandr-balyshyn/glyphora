package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Line, Modifiers}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class TabsSpec extends AnyFunSuite:

  private val tabs = Tabs(Seq("one", "two", "three").map(Line.raw))

  test("titles render on one row separated by the divider"):
    val buffer = rendered(tabs, 20, 1)
    assert(trimmedLines(buffer) == Seq("one │ two │ three"))

  test("the selected title carries the highlight style"):
    val buffer = rendered(tabs.copy(selected = 1), 20, 1)
    // 'two' starts after "one │ " (6 columns)
    assert(buffer.get(6, 0).style.modifiers.hasAny(Modifiers.Reverse))
    assert(!buffer.get(0, 0).style.modifiers.hasAny(Modifiers.Reverse))

  test("overflowing titles are clipped at the area edge"):
    val buffer = rendered(tabs, 8, 1)
    assert(trimmedLines(buffer) == Seq("one │ tw"))

  test("padding is drawn inside each tab, including the first and the last"):
    val buffer = rendered(Tabs.padded(Seq("one", "two").map(Line.raw)), 20, 1)
    assert(trimmedLines(buffer) == Seq(" one │ two"))

  test("the highlight covers the selected tab's padding, not only its title"):
    val buffer = rendered(Tabs.padded(Seq("one", "two").map(Line.raw), selected = 1), 20, 1)
    // " one │ two ": the divider is at column 5, so the second tab's left pad is column 6 and its right pad column 10
    assert(!buffer.get(5, 0).style.modifiers.hasAny(Modifiers.Reverse))
    assert(buffer.get(6, 0).style.modifiers.hasAny(Modifiers.Reverse))
    assert(buffer.get(10, 0).style.modifiers.hasAny(Modifiers.Reverse))
    assert(!buffer.get(11, 0).style.modifiers.hasAny(Modifiers.Reverse))

  test("padding is clipped like anything else at the area edge"):
    // " one │ tw" would need 9 columns; 8 stop the second title one cluster short and drop its right pad
    assert(trimmedLines(rendered(Tabs.padded(Seq("one", "two").map(Line.raw)), 8, 1)) == Seq(" one │ t"))

  test("the measured width counts titles, padding and dividers"):
    // " one " + "│" + " two " = 5 + 1 + 5
    assert(Tabs.padded(Seq("one", "two").map(Line.raw)).widthAt(1) == Some(11))
    // the unpadded default: "one" + " │ " + "two" + " │ " + "three"
    assert(tabs.widthAt(1) == Some(17))

  test("a wide title is measured by its display width, not its character count"):
    // each of the two CJK clusters occupies two columns
    assert(Tabs(Seq(Line.raw("日本"))).widthAt(1) == Some(4))

  test("a single tab is measured with no divider, and no tabs at all measure as nothing"):
    assert(Tabs(Seq(Line.raw("solo"))).widthAt(1) == Some(4))
    assert(Tabs(Seq.empty).widthAt(1) == Some(0))

  test("Tabs.NoSelection highlights nothing"):
    val buffer = rendered(tabs.copy(selected = Tabs.NoSelection), 20, 1)
    assert(trimmedLines(buffer) == Seq("one │ two │ three"))
    assert((0 until 17).forall(x => !buffer.get(x, 0).style.modifiers.hasAny(Modifiers.Reverse)))

  test("an index past the last tab highlights nothing either"):
    val buffer = rendered(tabs.copy(selected = 99), 20, 1)
    assert((0 until 17).forall(x => !buffer.get(x, 0).style.modifiers.hasAny(Modifiers.Reverse)))
