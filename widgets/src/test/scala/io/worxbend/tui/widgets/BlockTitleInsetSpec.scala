package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Line, Rect, Style}
import io.worxbend.tui.testsupport.BufferAssertions.trimmedLines

import org.scalatest.funsuite.AnyFunSuite

/** Covers the row a [[Block]] reserves for a title that has no border row to sit on. */
final class BlockTitleInsetSpec extends AnyFunSuite:

  private val area: Rect = Rect(0, 0, 10, 4)

  test("a title on a bordered side still costs no content row"):
    val block = Block(Seq(BlockTitle.top(Line.raw("Hi"))))
    assert(block.inner(area) == Rect(1, 1, 8, 2))
    assert(block.inner(area) == Block().inner(area))

  test("a top title with no top border reserves the top row"):
    val block = Block(Seq(BlockTitle.top(Line.raw("Hi"))), borders = Borders.None)
    assert(block.inner(area) == Rect(0, 1, 10, 3))

  test("a bottom title with no bottom border reserves the bottom row"):
    val block = Block(Seq(BlockTitle.bottom(Line.raw("Hi"))), borders = Borders.None)
    assert(block.inner(area) == Rect(0, 0, 10, 3))

  test("titles on both borderless sides reserve one row each"):
    val block = Block(Seq(BlockTitle.top(Line.raw("a")), BlockTitle.bottom(Line.raw("b"))), borders = Borders.None)
    assert(block.inner(area) == Rect(0, 1, 10, 2))

  test("a borderless side with no title reserves nothing"):
    val block = Block(Seq(BlockTitle.top(Line.raw("Hi"))), borders = Borders.Bottom)
    assert(block.inner(area) == Rect(0, 1, 10, 2))

  test("the reserved row is where the title is actually drawn"):
    val buffer = Buffer(area)
    val block  = Block(Seq(BlockTitle.top(Line.raw("Hi"))), borders = Borders.None)
    val inner  = block.inner(area)
    // fill the content area first, then the block: the two must not land on the same row
    buffer.setString(inner.x, inner.y, "content", Style.Default)
    block.render(area, buffer)
    assert(trimmedLines(buffer) == Seq("Hi", "content", "", ""))

  test("padding is added on top of the reserved row, not instead of it"):
    val block = Block(Seq(BlockTitle.top(Line.raw("Hi"))), borders = Borders.None, padding = Padding.uniform(1))
    assert(block.inner(area) == Rect(1, 2, 8, 1))

  test("an area too short for the reserved row yields an empty inner"):
    val block = Block(Seq(BlockTitle.top(Line.raw("Hi"))), borders = Borders.None)
    assert(block.inner(Rect(0, 0, 10, 1)).isEmpty)
