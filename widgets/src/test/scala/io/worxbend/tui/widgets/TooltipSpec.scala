package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect}
import io.worxbend.tui.testsupport.BufferAssertions.trimmedLines

import org.scalatest.funsuite.AnyFunSuite

final class TooltipSpec extends AnyFunSuite:

  test("a tooltip draws a rounded border around its text"):
    val tip    = Tooltip("save file")
    val buffer = Buffer(Rect(0, 0, tip.width, tip.height))
    tip.render(buffer.area, buffer)
    val lines  = trimmedLines(buffer)
    assert(lines.head.startsWith("╭") && lines.head.endsWith("╮"))
    assert(lines(1).contains("save file"))
    assert(lines.last.startsWith("╰") && lines.last.endsWith("╯"))

  test("width and height fit the widest line and the line count plus borders"):
    val tip = Tooltip("aa\nbbbb")
    assert(tip.height == 4) // 2 text rows + 2 borders
    assert(tip.width == 4 + 4) // widest line (4) + padding/borders (4)

  test("multi-line tooltips render each line on its own row"):
    val tip    = Tooltip("line1\nline2")
    val buffer = Buffer(Rect(0, 0, tip.width, tip.height))
    tip.render(buffer.area, buffer)
    val lines  = trimmedLines(buffer)
    assert(lines(1).contains("line1"))
    assert(lines(2).contains("line2"))
