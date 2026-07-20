package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect}
import io.worxbend.tui.testsupport.BufferAssertions.trimmedLines

import org.scalatest.funsuite.AnyFunSuite

final class MenuSpec extends AnyFunSuite:

  private val items = Seq(
    MenuItem("Open", shortcut = Some("^O")),
    MenuItem("Save", shortcut = Some("^S")),
    MenuItem.Separator,
    MenuItem("Quit", enabled = false),
  )

  private def renderedWith(state: MenuState, width: Int = 14, height: Int = 6): Buffer =
    val buffer = Buffer(Rect(0, 0, width, height))
    Menu(items).render(buffer.area, buffer, state)
    buffer

  test("the menu draws a border, labels, right-aligned shortcuts, and a separator rule"):
    val lines = trimmedLines(renderedWith(MenuState()))
    assert(lines.head.startsWith("╭") && lines.head.endsWith("╮"))
    assert(lines(1).contains("Open") && lines(1).contains("^O"))
    assert(lines(2).contains("Save") && lines(2).contains("^S"))
    assert(lines(3).contains("─────")) // separator row
    assert(lines(4).contains("Quit"))

  test("selectNext skips separators and disabled entries, wrapping back to the top"):
    val state = MenuState(selected = 0)
    state.selectNext(items)
    assert(state.selected == 1) // Save
    state.selectNext(items)
    assert(state.selected == 0) // skips separator(2) and disabled Quit(3), wraps to Open

  test("selectPrevious wraps and also skips non-selectable entries"):
    val state = MenuState(selected = 0)
    state.selectPrevious(items)
    assert(state.selected == 1) // wraps up past disabled Quit and separator to Save

  test("rendering normalizes a highlight that sits on a non-selectable entry onto the first selectable one"):
    val state = MenuState(selected = 2) // a separator
    renderedWith(state)
    assert(state.selected == 0)

  test("width and height report the popup's natural size"):
    val menu = Menu(items)
    assert(menu.height == items.size + 2)
    assert(menu.width >= "Save".length + "^S".length) // widest content plus borders/padding
