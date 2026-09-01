package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** Covers tinting a rectangle — laying a background over a region that already has content drawn in it.
  *
  * [[Buffer.setStyle]] *replaces* the style of every cell it touches, which is right for a highlight that owns the
  * whole look of the region. Tinting is the other half of the same job: the background changes and whatever the cells
  * already say about foreground colour and modifiers has to survive. That is [[Buffer.mapStyle]] with [[Style.patch]],
  * and these tests pin that combination because it is what a panel background relies on.
  */
final class BufferSetStyleSpec extends AnyFunSuite:

  private val blue: Style = Style.Default.withBg(Color.Blue)

  private def buffer(width: Int, height: Int): Buffer = Buffer(Rect(0, 0, width, height))

  private def tint(buf: Buffer, region: Rect, style: Style): Unit = buf.mapStyle(region)(_.patch(style))

  test("every cell of the region keeps its symbol and gains the tint"):
    val buf = buffer(3, 2)
    buf.setString(0, 0, "abc", Style.Default)
    tint(buf, Rect(0, 0, 3, 2), blue)
    assert(buf.get(0, 0) == Cell("a", blue))
    assert(buf.get(2, 0) == Cell("c", blue))
    // a cell nobody wrote to is a blank, and a blank with a background is exactly what a filled panel looks like
    assert(buf.get(1, 1) == Cell(" ", blue))

  test("an existing foreground survives a background tint"):
    val buf  = buffer(2, 1)
    buf.set(0, 0, Cell("x", Style.Default.withFg(Color.Red).bold))
    tint(buf, Rect(0, 0, 2, 1), blue)
    val cell = buf.get(0, 0)
    assert(cell.symbol == "x")
    assert(cell.style.fg.contains(Color.Red))
    assert(cell.style.bg.contains(Color.Blue))
    assert(cell.style.modifiers.hasAll(Modifiers.Bold))

  test("cells outside the region are left alone"):
    val buf = buffer(3, 1)
    buf.setString(0, 0, "abc", Style.Default)
    tint(buf, Rect(0, 0, 1, 1), blue)
    assert(buf.get(0, 0).style.bg.contains(Color.Blue))
    assert(buf.get(1, 0).style.bg.isEmpty)

  test("a region reaching outside the buffer is clipped, not an error"):
    val buf = buffer(2, 2)
    tint(buf, Rect(-5, -5, 100, 100), blue)
    assert(buf.get(0, 0).style.bg.contains(Color.Blue))
    assert(buf.get(1, 1).style.bg.contains(Color.Blue))

  test("an empty or non-overlapping region changes nothing"):
    val buf = buffer(2, 2)
    tint(buf, Rect(0, 0, 0, 0), blue)
    tint(buf, Rect(50, 50, 4, 4), blue)
    assert(buf.diff(buffer(2, 2)).isEmpty)

  test("a two-column grapheme keeps its symbol and its continuation stays a continuation"):
    val buf     = buffer(4, 1)
    // "漢" is a CJK ideograph: two terminal columns wide, so column 1 is its continuation filler. The terminal paints
    // both columns from the left cell's style, so tinting that one cell is what colours the whole grapheme.
    buf.set(0, 0, Cell("漢", Style.Default))
    tint(buf, Rect(0, 0, 4, 1), blue)
    assert(buf.get(0, 0) == Cell("漢", blue))
    // the continuation is still a continuation: the diff never emits it on its own
    val changes = buffer(4, 1).diff(buf).toSeq.map(_._1.x)
    assert(!changes.contains(1))

  test("a combining mark cluster keeps its exact symbol through a tint"):
    val buf = buffer(2, 1)
    // "e" followed by U+0301 COMBINING ACUTE ACCENT — one grapheme cluster stored in one cell
    buf.setString(0, 0, "é", Style.Default)
    tint(buf, Rect(0, 0, 2, 1), blue)
    assert(buf.get(0, 0) == Cell("é", blue))
