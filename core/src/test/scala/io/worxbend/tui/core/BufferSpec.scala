package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class BufferSpec extends AnyFunSuite:

  private def buffer(width: Int, height: Int): Buffer = Buffer(Rect(0, 0, width, height))

  test("a new buffer is all empty cells"):
    val buf = buffer(2, 2)
    assert(buf.get(0, 0) == Cell.Empty)
    assert(buf.get(1, 1) == Cell.Empty)

  test("set then get round-trips inside the area"):
    val buf  = buffer(3, 3)
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

  test("overwriting a wide grapheme in place releases the column it reserved"):
    val buf = buffer(4, 1)
    buf.setString(0, 0, "你", Style.Default)
    buf.set(0, 0, Cell("a", Style.Default))
    buf.set(1, 0, Cell("b", Style.Default))
    // had the stale continuation on column 1 survived, the write at 1 would have blanked the 'a' next to it
    assert(buf.get(0, 0).symbol == "a")
    assert(buf.get(1, 0).symbol == "b")

  test("writing the empty cell onto a continuation leaves the wide grapheme intact"):
    // `set(x, wide)` followed by `set(x + 1, Cell.Empty)` is how setString and seven widgets spell the filler; the
    // second write must not be read as a claim on the column, or every wide glyph erases itself
    val buf = buffer(4, 1)
    buf.setString(0, 0, "你", Style.Default)
    buf.set(1, 0, Cell.Empty)
    assert(buf.get(0, 0).symbol == "你")
    assert(buf.get(1, 0) == Cell.Empty)

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

  test("set blanks a wide grapheme aimed at the last column of a row"):
    // there is no column left to reserve as its continuation, so storing the glyph itself would make the terminal
    // draw two columns where the buffer owns one: the row wraps, and the next frame's diff — believing the column
    // already holds the glyph — never repairs it. The style survives so a background fill reaches the edge.
    val buf = buffer(3, 1)
    buf.set(2, 0, Cell("好", Style.Default.withBg(Color.Blue)))
    assert(buf.get(2, 0) == Cell(" ", Style.Default.withBg(Color.Blue)))

  test("blit lands a wide grapheme at the destination's right edge as a blank"):
    // the guard belongs to the write, not to any one caller: `blit` goes through `set` like everything else
    val source = buffer(2, 1)
    source.setString(0, 0, "好", Style.Default)
    val target = buffer(3, 1)
    target.blit(source, Position(2, 0))
    assert(target.get(2, 0) == Cell.Empty)
    // and so nothing reaches a backend that would draw across a column this buffer does not own
    assert(buffer(3, 1).diff(target).isEmpty)

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
    val next     = buffer(3, 2)
    assert(previous.diff(next).isEmpty)

  test("diff emits exactly the changed cells with their new content"):
    val previous = buffer(3, 1)
    val next     = buffer(3, 1)
    next.setString(0, 0, "ab", Style.Default)
    val changes  = previous.diff(next).toSeq
    assert(changes == Seq(Position(0, 0) -> Cell("a", Style.Default), Position(1, 0) -> Cell("b", Style.Default)))

  test("diff never emits the continuation cell of a wide character"):
    val previous         = buffer(3, 1)
    previous.setString(1, 0, "x", Style.Default)
    val next             = buffer(3, 1)
    next.setString(0, 0, "你", Style.Default)
    val changedPositions = previous.diff(next).map(_._1).toSeq
    assert(changedPositions == Seq(Position(0, 0)))

  test("diff emits every cell when the areas differ"):
    val previous = buffer(2, 1)
    val next     = buffer(3, 1)
    next.setString(0, 0, "abc", Style.Default)
    assert(previous.diff(next).size == 3)

  test("blit blanks wide graphemes cut in half at the window edges"):
    val source = buffer(6, 1)
    source.setString(0, 0, "a你b你", Style.Default) // cells: a 你 · b 你 ·
    val target = buffer(4, 1)
    // window starts at x=2 (the continuation of the first 你) and ends at x=5 (splitting the second 你)
    target.blit(source, Position(0, 0), Rect(2, 0, 3, 1))
    assert(target.get(0, 0) == Cell.Empty) // torn left half dropped
    assert(target.get(1, 0).symbol == "b")
    assert(target.get(2, 0) == Cell.Empty) // wide char whose continuation was cut off dropped

  test("blit trims a region that starts outside the source instead of shifting its content"):
    // an overlay laid out at a negative origin asks for cells the source does not have; clipping must drop those
    // columns, not slide the surviving ones left onto the landing point meant for the missing ones
    val source = buffer(3, 1)
    source.setString(0, 0, "abc", Style.Default)
    val target = buffer(5, 1)
    target.blit(source, Position(0, 0), Rect(-2, 0, 5, 1))
    assert(target.get(0, 0) == Cell.Empty) // the two columns left of the source stay blank
    assert(target.get(1, 0) == Cell.Empty)
    assert(target.get(2, 0).symbol == "a")
    assert(target.get(3, 0).symbol == "b")
    assert(target.get(4, 0).symbol == "c")

  test("a variation selector does not consume a cell of its own"):
    // a lone VS16 reported two columns, so setString claimed a cell plus a continuation for a glyph the terminal
    // never draws and pushed the rest of the row two columns right
    val buf = buffer(4, 1)
    buf.setString(0, 0, 0xfe0f.toChar.toString + "abc", Style.Default)
    assert(buf.get(0, 0).symbol == "a")
    assert(buf.get(1, 0).symbol == "b")
    assert(buf.get(2, 0).symbol == "c")

  test("a CJK ideograph followed by VS15 still claims its continuation cell"):
    // treating 你 + VS15 as one column left no continuation cell: everything right of it rendered one column off
    // for the rest of the row, and the diff engine confirmed the overwrite instead of suppressing it
    val buf = buffer(4, 1)
    buf.setString(0, 0, "你" + 0xfe0e.toChar.toString + "xy", Style.Default)
    assert(buf.get(1, 0) == Cell.Empty) // the continuation cell of the wide glyph
    assert(buf.get(2, 0).symbol == "x")
    assert(buf.get(3, 0).symbol == "y")

  test("a stray ZWJ does not let the following text overflow the buffer"):
    // joining unconditionally made "a ZWJ 你" one column wide, so the characters after it were written on top of
    // the ideograph instead of after it
    val buf = buffer(6, 1)
    buf.setString(0, 0, "a" + 0x200d.toChar.toString + "你XY", Style.Default)
    assert(buf.get(0, 0).symbol.startsWith("a"))
    assert(buf.get(1, 0).symbol == "你")
    assert(buf.get(2, 0) == Cell.Empty) // continuation of 你, not overwritten by X
    assert(buf.get(3, 0).symbol == "X")
    assert(buf.get(4, 0).symbol == "Y")

  test("fill writes the cell across the region and leaves its neighbours untouched"):
    val buf = buffer(4, 3)
    buf.fill(Rect(1, 1, 2, 1), Cell("#", Style.Default.bold))
    assert(buf.get(1, 1) == Cell("#", Style.Default.bold))
    assert(buf.get(2, 1) == Cell("#", Style.Default.bold))
    assert(buf.get(0, 1) == Cell.Empty)
    assert(buf.get(3, 1) == Cell.Empty)
    assert(buf.get(1, 0) == Cell.Empty)
    assert(buf.get(1, 2) == Cell.Empty)

  test("fill clips a region that reaches past the area instead of failing"):
    val buf = buffer(2, 2)
    buf.fill(Rect(-3, -3, 20, 20), Cell("#", Style.Default))
    assert(buf.get(0, 0).symbol == "#")
    assert(buf.get(1, 1).symbol == "#")

  test("fill of an empty region writes nothing"):
    val buf = buffer(2, 2)
    buf.fill(Rect(0, 0, 0, 2), Cell("#", Style.Default))
    buf.fill(Rect(0, 0, 2, 0), Cell("#", Style.Default))
    buf.fill(Rect(9, 9, 2, 2), Cell("#", Style.Default))
    assert(buf.get(0, 0) == Cell.Empty)
    assert(buf.get(1, 1) == Cell.Empty)

  test("fill with a two-column symbol advances by two and reserves each continuation"):
    // stepping one column at a time would paint over the continuation cell the previous write just claimed, which
    // leaves the buffer claiming a wide glyph that no longer owns the column beside it
    val buf = buffer(5, 1)
    buf.fill(Rect(0, 0, 5, 1), Cell("你", Style.Default))
    assert(buf.get(0, 0).symbol == "你")
    assert(buf.get(1, 0) == Cell.Empty)
    assert(buf.get(2, 0).symbol == "你")
    assert(buf.get(3, 0) == Cell.Empty)
    // the odd column at the right edge has no room for a second half, so `set` stores a blank in the same style
    assert(buf.get(4, 0) == Cell(" ", Style.Default))

  test("fill keeps a two-column symbol inside an odd-width region"):
    // the region ends at column 5, but the buffer is wider, so nothing stops a wide glyph starting at column 4 from
    // painting column 5 as well. The column past the region belongs to whoever drew there, not to the fill.
    val buf = buffer(8, 1)
    buf.set(5, 0, Cell("X", Style.Default))
    buf.fill(Rect(0, 0, 5, 1), Cell("你", Style.Default.bold))
    assert(buf.get(0, 0).symbol == "你")
    assert(buf.get(2, 0).symbol == "你")
    // column 4 is the odd one out: a blank carrying the fill's style, never half a glyph
    assert(buf.get(4, 0) == Cell(" ", Style.Default.bold))
    // and the neighbour outside the region is untouched
    assert(buf.get(5, 0) == Cell("X", Style.Default))

  test("Buffer.filled is a new buffer already filled"):
    val cell = Cell("·", Style.Default.dim)
    val buf  = Buffer.filled(Rect(0, 0, 2, 2), cell)
    assert(buf.get(0, 0) == cell)
    assert(buf.get(1, 1) == cell)

  test("setStyle restyles a region without touching its symbols"):
    val buf = buffer(4, 2)
    buf.setString(0, 0, "abcd", Style.Default)
    buf.setStyle(Rect(1, 0, 2, 1), Style.Default.bold)
    assert(buf.get(1, 0) == Cell("b", Style.Default.bold))
    assert(buf.get(2, 0) == Cell("c", Style.Default.bold))
    assert(buf.get(0, 0) == Cell("a", Style.Default))
    assert(buf.get(3, 0) == Cell("d", Style.Default))

  test("setStyle also restyles blank cells, so a background reaches the whole rectangle"):
    val buf = buffer(3, 1)
    buf.setString(0, 0, "a", Style.Default)
    buf.setStyle(Rect(0, 0, 3, 1), Style.Default.withBg(Color.Blue))
    assert(buf.get(1, 0) == Cell(Cell.Empty.symbol, Style.Default.withBg(Color.Blue)))
    assert(buf.get(2, 0).style == Style.Default.withBg(Color.Blue))

  test("setStyle clips to the area and a fully outside region is a no-op"):
    val buf = buffer(2, 1)
    buf.setString(0, 0, "ab", Style.Default)
    buf.setStyle(Rect(1, 0, 10, 1), Style.Default.bold)
    assert(buf.get(0, 0).style == Style.Default)
    assert(buf.get(1, 0).style == Style.Default.bold)
    buf.setStyle(Rect(50, 50, 3, 3), Style.Default.italic)
    assert(buf.get(0, 0).style == Style.Default)

  test("setStyle leaves the continuation cell of a wide grapheme empty"):
    // a styled continuation stops comparing equal to Cell.Empty, which is the test `set` uses to tell a caller's own
    // filler apart from real content landing on a reserved column — restyling it would break that invariant
    val buf = buffer(4, 1)
    buf.setString(0, 0, "你好", Style.Default)
    buf.setStyle(Rect(0, 0, 4, 1), Style.Default.bold)
    assert(buf.get(0, 0) == Cell("你", Style.Default.bold))
    assert(buf.get(1, 0) == Cell.Empty)
    assert(buf.get(2, 0) == Cell("好", Style.Default.bold))
    assert(buf.get(3, 0) == Cell.Empty)
    // and the pair still survives a caller writing its own filler over the reserved column
    buf.set(1, 0, Cell.Empty)
    assert(buf.get(0, 0).symbol == "你")

  test("mapStyle derives each new style from the current one and writes nothing when it is unchanged"):
    val buf    = buffer(3, 1)
    buf.setString(0, 0, "a", Style.Default.withFg(Color.Red))
    buf.setString(1, 0, "b", Style.Default.withFg(Color.Green))
    buf.mapStyle(Rect(0, 0, 3, 1))(_.bold)
    assert(buf.get(0, 0).style == Style.Default.withFg(Color.Red).bold)
    assert(buf.get(1, 0).style == Style.Default.withFg(Color.Green).bold)
    assert(buf.get(2, 0).style == Style.Default.bold)
    val before = buf.snapshot
    buf.mapStyle(Rect(0, 0, 3, 1))(identity)
    assert(buf.diff(before).isEmpty)

  test("mapStyle re-runs the transform only when the style changes"):
    // consecutive cells almost always share one style; the cached last input is what keeps a full-frame pass from
    // building ten thousand identical Style values
    val buf   = buffer(4, 1)
    buf.setString(0, 0, "aa", Style.Default.withFg(Color.Red))
    buf.setString(2, 0, "bb", Style.Default.withFg(Color.Blue))
    var calls = 0
    buf.mapStyle(Rect(0, 0, 4, 1)) { style =>
      calls += 1
      style.bold
    }
    assert(calls == 2)
    assert(buf.get(1, 0).style == Style.Default.withFg(Color.Red).bold)
    assert(buf.get(3, 0).style == Style.Default.withFg(Color.Blue).bold)
