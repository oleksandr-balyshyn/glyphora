package io.worxbend.tui.dsl

import io.worxbend.tui.testsupport.BufferAssertions.{line as bufferLine, rendered}
import io.worxbend.tui.widgets as w

import org.scalatest.funsuite.AnyFunSuite

/** A `panel` used to be all four border sides or nothing, with its top caption pinned to the left and its bottom one
  * to the right, both in a single style. These tests pin the sides, the alignments, and the extra captions.
  */
final class PanelChromeSpec extends AnyFunSuite:

  private def frame(element: Element, width: Int, height: Int): Seq[String] =
    val buffer = rendered(element.widget, width, height)
    (0 until height).map(row => bufferLine(buffer, row))

  test("a panel still draws all four sides by default"):
    assert(frame(panel(text("x")), 4, 3) == Seq("┌──┐", "│x │", "└──┘"))

  test("borders draws only the sides asked for, with no dangling corners"):
    assert(frame(panel(text("x")).borders(Borders.Top), 4, 3) == Seq("────", "x   ", "    "))
    assert(frame(panel(text("x")).borders(Borders.Left), 4, 3) == Seq("│x  ", "│   ", "│   "))

  test("two adjacent drawn sides do meet in a corner"):
    assert(frame(panel(text("x")).borders(Borders.Top | Borders.Left), 4, 3) == Seq("┌───", "│x  ", "│   "))

  test("borderless keeps the padding and the children but paints no frame"):
    assert(frame(panel(text("x")).borderless, 4, 2) == Seq("x   ", "    "))

  test("the measured height counts only the border sides actually drawn"):
    val threeLines = text("a\nb\nc")
    assert(panel(threeLines).intrinsicHeight(10) == Some(5))
    assert(panel(threeLines).borders(Borders.Top).intrinsicHeight(10) == Some(4))
    assert(panel(threeLines).borderless.intrinsicHeight(10) == Some(3))

  test("the measured width available to a child drops with the vertical sides"):
    // The child wraps, so the rows it reports reveal how many columns the panel left it.
    val prose = text("abcdefgh").wrapped
    assert(panel(prose).intrinsicHeight(6) == Some(4))     // 4 columns inside, 2 wrapped rows, 2 border rows
    assert(panel(prose).borderless.intrinsicHeight(6) == Some(2))

  test("titleAligned and titleBottomAligned move the captions along their borders"):
    val centred = panel("ab")(text("x")).titleAligned(w.Alignment.Center)
    assert(bufferLine(rendered(centred.widget, 8, 3), 0) == "┌──ab──┐")

  test("the bottom caption starts at the right and can be moved left"):
    val element = panel("top")(text("x")).titleBottom("ok")
    assert(bufferLine(rendered(element.widget, 8, 3), 2).endsWith("ok┘"))
    val moved   = element.titleBottomAligned(w.Alignment.Left)
    assert(bufferLine(rendered(moved.widget, 8, 3), 2).startsWith("└ok"))

  test("titles adds further captions carrying styled lines"):
    val element = panel("build")(text("x")).titles(w.BlockTitle.top(Line.styled("failed", Style.Default.bold)))
    assert(bufferLine(rendered(element.widget, 16, 3), 0).contains("build failed"))

  test("a caption wider than the border is clipped rather than widening the box"):
    val element = panel("a very long caption indeed")(text("x"))
    val painted = rendered(element.widget, 8, 3)
    assert(bufferLine(painted, 0).length == 8)

  test("a CJK caption is measured in display columns, not characters"):
    // Each ideograph is two columns wide, so "設定" occupies four of the eight border cells.
    val element = panel("設定")(text("x")).titleAligned(w.Alignment.Center)
    assert(bufferLine(rendered(element.widget, 8, 3), 0) == "┌─設定─┐")

  test("an area with no room for a frame does not throw"):
    assert(rendered(panel("t")(text("x")).borders(Borders.Top).widget, 1, 1).area.width == 1)
