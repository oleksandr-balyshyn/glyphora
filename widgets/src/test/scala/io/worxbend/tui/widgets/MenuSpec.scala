package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class MenuSpec extends AnyFunSuite:

  private val items = Seq(
    MenuEntry.Item("Open", shortcut = Some("^O")),
    MenuEntry.Item("Save", shortcut = Some("^S")),
    MenuEntry.Separator,
    MenuEntry.Item("Quit", enabled = false),
  )

  private val menu = Menu(items)

  test("the menu draws a border, labels, right-aligned shortcuts, and a separator rule"):
    val lines = trimmedLines(rendered(menu, MenuState(), 14, 6))
    assert(lines.head.startsWith("╭") && lines.head.endsWith("╮"))
    assert(lines(1).contains("Open") && lines(1).contains("^O"))
    assert(lines(2).contains("Save") && lines(2).contains("^S"))
    assert(lines(3).contains("─────")) // separator row
    assert(lines(4).contains("Quit"))

  test("selectNext skips separators and disabled entries, wrapping back to the top"):
    val state = MenuState(selected = Some(0))
    state.selectNext(items)
    assert(state.selected.contains(1)) // Save
    state.selectNext(items)
    assert(state.selected.contains(0)) // skips separator(2) and disabled Quit(3), wraps to Open

  test("selectPrevious wraps and also skips non-selectable entries"):
    val state = MenuState(selected = Some(0))
    state.selectPrevious(items)
    assert(state.selected.contains(1)) // wraps up past disabled Quit and separator to Save

  test("moving the highlight in a menu with nothing selectable leaves it unset"):
    val state = MenuState()
    val inert = Seq(MenuEntry.Separator, MenuEntry.Item("Quit", enabled = false))
    state.selectNext(inert)
    state.selectPrevious(inert)
    assert(state.selected.isEmpty)

  test("rendering normalizes a highlight that sits on a non-selectable entry onto the first selectable one"):
    val state = MenuState(selected = Some(2)) // a separator
    val _     = rendered(menu, state, 14, 6)
    assert(state.selected.contains(0))

  test("the measured size reports the popup's natural box"):
    assert(menu.heightAt(0).contains(items.size + 2))
    assert(menu.widthAt(0).exists(_ >= "Save".length + "^S".length)) // widest content plus borders/padding

  test("a shortcut hint landing on a wide label's continuation column still renders"):
    // the label truncates to " 設定パネ" (9 columns) so ネ sits at inner.x + 7 with its continuation at inner.x + 8,
    // and the hint "q " is written at inner.right - 2, i.e. exactly that continuation column. The buffer used to hold
    // the `q` while every reader of the frame — the diff engine included — stepped straight over it.
    val buffer = rendered(Menu(Seq(MenuEntry.Item("設定パネル", shortcut = Some("q")))), MenuState(), 12, 3)
    assert(trimmedLines(buffer)(1).contains("q"))
