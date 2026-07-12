package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class BufferSpec extends AnyFunSuite:

  private def buffer(width: Int, height: Int): Buffer = Buffer(Rect(0, 0, width, height))

  test("a new buffer is all empty cells"):
    val buf = buffer(2, 2)
    assert(buf.get(0, 0) == Cell.Empty)
    assert(buf.get(1, 1) == Cell.Empty)

  test("set then get round-trips inside the area"):
    val buf = buffer(3, 3)
    val cell = Cell("x", Style.Default.bold)
    buf.set(1, 2, cell)
    assert(buf.get(1, 2) == cell)

  test("writes outside the area are silently clipped"):
    val buf = buffer(2, 2)
    buf.set(5, 5, Cell("x", Style.Default))
    buf.set(-1, 0, Cell("x", Style.Default))
    assert(buf.diff(buffer(2, 2)).isEmpty)

  test("reads outside the area return the empty cell"):
    assert(buffer(2, 2).get(9, 9) == Cell.Empty)

  test("buffer coordinates are absolute, not area-relative"):
    val buf = Buffer(Rect(10, 5, 3, 3))
    buf.set(11, 6, Cell("x", Style.Default))
    assert(buf.get(11, 6).symbol == "x")
    assert(buf.get(1, 1) == Cell.Empty) // outside the offset area

  test("setString writes one narrow character per cell"):
    val buf = buffer(5, 1)
    buf.setString(0, 0, "abc", Style.Default)
    assert(buf.get(0, 0).symbol == "a")
    assert(buf.get(1, 0).symbol == "b")
    assert(buf.get(2, 0).symbol == "c")

  test("setString stores a wide character in its left cell with an empty continuation"):
    val buf = buffer(4, 1)
    buf.setString(0, 0, "你a", Style.Default)
    assert(buf.get(0, 0).symbol == "你")
    assert(buf.get(1, 0) == Cell.Empty)
    assert(buf.get(2, 0).symbol == "a")

  test("setString drops a wide character that would only half-fit at the right edge"):
    val buf = buffer(3, 1)
    buf.setString(2, 0, "你", Style.Default)
    assert(buf.get(2, 0) == Cell.Empty)

  test("setString clips text past the right edge"):
    val buf = buffer(3, 1)
    buf.setString(0, 0, "abcdef", Style.Default)
    assert(buf.get(2, 0).symbol == "c")

  test("setString keeps a combining mark in its base character's cell"):
    val buf = buffer(3, 1)
    buf.setString(0, 0, "éx", Style.Default)
    assert(buf.get(0, 0).symbol == "é")
    assert(buf.get(1, 0).symbol == "x")

  test("setString skips a leading combining mark with no base"):
    val buf = buffer(3, 1)
    buf.setString(0, 0, "́a", Style.Default)
    assert(buf.get(0, 0).symbol == "a")

  test("reset restores every cell to empty"):
    val buf = buffer(2, 1)
    buf.setString(0, 0, "ab", Style.Default)
    buf.reset()
    assert(buf.get(0, 0) == Cell.Empty)
    assert(buf.get(1, 0) == Cell.Empty)

  test("blit copies a source buffer at an offset with clipping"):
    val source = buffer(3, 1)
    source.setString(0, 0, "abc", Style.Default)
    val target = buffer(4, 2)
    target.blit(source, Position(2, 1))
    assert(target.get(2, 1).symbol == "a")
    assert(target.get(3, 1).symbol == "b") // 'c' clipped at the right edge
    assert(target.get(0, 0) == Cell.Empty)

  test("blit with a region copies only that window"):
    val source = buffer(4, 2)
    source.setString(0, 0, "abcd", Style.Default)
    source.setString(0, 1, "efgh", Style.Default)
    val target = buffer(4, 2)
    target.blit(source, Position(0, 0), Rect(1, 1, 2, 1))
    assert(target.get(0, 0).symbol == "f")
    assert(target.get(1, 0).symbol == "g")
    assert(target.get(2, 0) == Cell.Empty)

  test("diff of identical buffers is empty"):
    val previous = buffer(3, 2)
    val next = buffer(3, 2)
    assert(previous.diff(next).isEmpty)

  test("diff emits exactly the changed cells with their new content"):
    val previous = buffer(3, 1)
    val next = buffer(3, 1)
    next.setString(0, 0, "ab", Style.Default)
    val changes = previous.diff(next).toSeq
    assert(changes == Seq(Position(0, 0) -> Cell("a", Style.Default), Position(1, 0) -> Cell("b", Style.Default)))

  test("diff never emits the continuation cell of a wide character"):
    val previous = buffer(3, 1)
    previous.setString(1, 0, "x", Style.Default)
    val next = buffer(3, 1)
    next.setString(0, 0, "你", Style.Default)
    val changedPositions = previous.diff(next).map(_._1).toSeq
    assert(changedPositions == Seq(Position(0, 0)))

  test("diff emits every cell when the areas differ"):
    val previous = buffer(2, 1)
    val next = buffer(3, 1)
    next.setString(0, 0, "abc", Style.Default)
    assert(previous.diff(next).size == 3)
