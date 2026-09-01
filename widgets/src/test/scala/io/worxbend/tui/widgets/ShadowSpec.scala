package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Color, Modifiers, Rect, Style}
import io.worxbend.tui.testsupport.BufferAssertions.trimmedLines

import org.scalatest.funsuite.AnyFunSuite

/** Covers [[Shadow]] on its own: which cells the band covers, and what each fill does to them. */
final class ShadowSpec extends AnyFunSuite:

  private val bounds: Rect = Rect(0, 0, 6, 4)
  private val box: Rect    = Rect(0, 0, 4, 2)

  /** A buffer with every cell filled with `x`, so a shadow's effect on what was underneath is visible. */
  private def filled(): Buffer =
    val buffer = Buffer(bounds)
    var y      = 0
    while y < bounds.height do
      buffer.setString(0, y, "x" * bounds.width, Style.Default)
      y += 1
    buffer

  test("the band is the box displaced by the offset, minus the box itself"):
    val buffer = filled()
    Shadow(1, 1, Style.Default, ShadowFill.MediumShade).render(box, bounds, buffer)
    assert(
      trimmedLines(buffer) == Seq(
        "xxxxxx",
        "xxxx▒x",
        "x▒▒▒▒x",
        "xxxxxx",
      )
    )

  test("the shadow never writes inside the box"):
    val buffer = filled()
    Shadow(1, 1, Style.Default, ShadowFill.Solid).render(box, bounds, buffer)
    for
      y <- 0 until box.height
      x <- 0 until box.width
    do assert(buffer.get(x, y).symbol == "x", s"the box cell ($x, $y) was overwritten")

  test("a negative offset casts up and to the left"):
    val buffer = filled()
    Shadow(-1, -1, Style.Default, ShadowFill.MediumShade).render(Rect(2, 2, 3, 2), bounds, buffer)
    assert(
      trimmedLines(buffer) == Seq(
        "xxxxxx",
        "x▒▒▒xx",
        "x▒xxxx",
        "xxxxxx",
      )
    )

  test("a zero offset paints nothing"):
    val buffer = filled()
    Shadow(0, 0, Style.Default, ShadowFill.Solid).render(box, bounds, buffer)
    assert(buffer.diff(filled()).isEmpty)

  test("the band is clipped at the edge of the bounds instead of writing outside them"):
    val buffer = filled()
    // the box fills the bounds, so the whole band falls outside and nothing may be written
    Shadow(1, 1, Style.Default, ShadowFill.Solid).render(bounds, bounds, buffer)
    assert(buffer.diff(filled()).isEmpty)

  test("a box hanging over the edge shades only the part of the band that fits"):
    val buffer = filled()
    Shadow(2, 2, Style.Default, ShadowFill.Solid).render(Rect(3, 1, 3, 2), bounds, buffer)
    assert(
      trimmedLines(buffer) == Seq(
        "xxxxxx",
        "xxxxxx",
        "xxxxxx",
        "xxxxx█",
      )
    )

  test("the dim fill keeps the glyph underneath and only changes its style"):
    val buffer = filled()
    Shadow().render(box, bounds, buffer)
    val shaded = buffer.get(1, 2)
    assert(shaded.symbol == "x")
    assert(shaded.style.modifiers.hasAll(Modifiers.Dim))
    // a cell the band does not reach keeps its plain style
    assert(!buffer.get(0, 3).style.modifiers.hasAll(Modifiers.Dim))

  test("the dim fill dims a two-column glyph without splitting it"):
    val buffer = filled()
    // "漢" is a CJK ideograph and takes two terminal columns; the band must not leave half of it behind
    buffer.set(1, 2, Cell("漢", Style.Default))
    Shadow().render(box, bounds, buffer)
    assert(buffer.get(1, 2).symbol == "漢")
    assert(buffer.get(1, 2).style.modifiers.hasAll(Modifiers.Dim))

  test("each shade fill writes its own glyph"):
    def glyphAt(fill: ShadowFill): String =
      val buffer = filled()
      Shadow(1, 1, Style.Default, fill).render(box, bounds, buffer)
      buffer.get(1, 2).symbol
    assert(glyphAt(ShadowFill.LightShade) == "░")
    assert(glyphAt(ShadowFill.MediumShade) == "▒")
    assert(glyphAt(ShadowFill.DarkShade) == "▓")
    assert(glyphAt(ShadowFill.Solid) == "█")
    assert(glyphAt(ShadowFill.Symbol(".")) == ".")

  test("the shadow style is applied to the band"):
    val buffer = filled()
    Shadow(1, 1, Style.Default.withFg(Color.Red), ShadowFill.Solid).render(box, bounds, buffer)
    assert(buffer.get(1, 2).style.fg.contains(Color.Red))

  test("the caller's style wins over the dimming"):
    val buffer = filled()
    Shadow(1, 1, Style.Default.without(Modifiers.Dim), ShadowFill.Dim).render(box, bounds, buffer)
    assert(!buffer.get(1, 2).style.modifiers.hasAll(Modifiers.Dim))

  test("reservedColumns and reservedRows ignore the direction of the offset"):
    assert(Shadow(-2, -3).reservedColumns == 2)
    assert(Shadow(-2, -3).reservedRows == 3)
    assert(Shadow.Default.reservedColumns == 1)

  test("the named constructors build what they say"):
    assert(Shadow.dim(2, 3) == Shadow(2, 3, Style.Default, ShadowFill.Dim))
    assert(Shadow.shade(ShadowFill.DarkShade) == Shadow(1, 1, Style.Default, ShadowFill.DarkShade))
