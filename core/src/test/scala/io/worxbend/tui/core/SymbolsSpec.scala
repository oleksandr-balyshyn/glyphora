package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** Pins the shared glyph vocabulary.
  *
  * These are constants, so the interesting assertions are not "does `Light` equal `░`" on its own but the relations
  * between them: that the ramps are ordered, that the two eighth ladders really are different directions, and that
  * every glyph is exactly one terminal column wide. That last one is the property a widget silently depends on — a
  * two-column glyph in a ramp would push every cell after it out of place.
  */
final class SymbolsSpec extends AnyFunSuite:

  test("the shade ramp runs from empty to full, in that order"):
    assert(Symbols.Shade.Ramp == Vector(" ", "░", "▒", "▓", "█"))
    assert(Symbols.Shade.Ramp.head == Symbols.Shade.Empty)
    assert(Symbols.Shade.Ramp.last == Symbols.Shade.Full)
    assert(Symbols.Shade.Ramp.distinct.size == Symbols.Shade.Ramp.size)

  test("the shade ramp's full block is the same glyph as the block family's"):
    // one character, two names, because it is both the top of the shade ramp and a block element in its own right
    assert(Symbols.Shade.Full == Symbols.Block.Full)

  test("the vertical ladder grows upward and the horizontal one rightward"):
    // the two are the same eight fractions drawn in different directions; using one where the other belongs fills
    // the wrong way, which is exactly the mistake a shared name is meant to prevent
    assert(Symbols.Block.VerticalEighths.head == "▁")   // one eighth, at the bottom of the cell
    assert(Symbols.Block.HorizontalEighths.head == "▏") // one eighth, at the left edge
    assert(Symbols.Block.VerticalEighths != Symbols.Block.HorizontalEighths)

  test("each eighth ladder is its partials plus a full block"):
    assert(Symbols.Block.VerticalEighths == Symbols.Block.VerticalPartials :+ Symbols.Block.Full)
    assert(Symbols.Block.HorizontalEighths == Symbols.Block.HorizontalPartials :+ Symbols.Block.Full)
    assert(Symbols.Block.VerticalPartials.size == 7)
    assert(Symbols.Block.HorizontalPartials.size == 7)
    assert(Symbols.Block.VerticalEighths.size == 8)

  test("neither ladder repeats a glyph"):
    assert(Symbols.Block.VerticalEighths.distinct.size == 8)
    assert(Symbols.Block.HorizontalEighths.distinct.size == 8)

  test("the half blocks are four distinct glyphs"):
    val halves = Seq(Symbols.Block.UpperHalf, Symbols.Block.LowerHalf, Symbols.Block.LeftHalf, Symbols.Block.RightHalf)
    assert(halves == Seq("▀", "▄", "▌", "▐"))
    assert(halves.distinct.size == 4)

  test("every glyph occupies exactly one terminal column"):
    // measured through CharWidth, the same way the widgets that draw them measure text; a two-column glyph anywhere
    // in a ramp would shift every cell drawn after it
    val everyGlyph =
      Symbols.Shade.Ramp ++
        Symbols.Block.VerticalEighths ++
        Symbols.Block.HorizontalEighths ++
        Seq(Symbols.Block.UpperHalf, Symbols.Block.LowerHalf, Symbols.Block.LeftHalf, Symbols.Block.RightHalf)
    for glyph <- everyGlyph do assert(CharWidth.of(glyph) == 1, s"$glyph is not one column wide")
