package io.worxbend.tui.widgets

import io.worxbend.tui.core.{GlyphSupport, Rect}
import io.worxbend.tui.testsupport.BufferAssertions.{line as bufferLine, rendered}

import org.scalatest.funsuite.AnyFunSuite

/** What every glyph catalogue promises when the terminal cannot draw what the author asked for: something readable, in
  * the same number of columns.
  */
final class GlyphDegradationSpec extends AnyFunSuite:

  // ------------------------------------------------------------------ borders

  test("a box-drawing border survives only where box drawing does"):
    assert(BorderType.Thick.degraded(GlyphSupport.Full) == BorderType.Thick)
    assert(BorderType.Thick.degraded(GlyphSupport.BoxDrawing) == BorderType.Thick)
    assert(BorderType.Thick.degraded(GlyphSupport.Ascii) == BorderType.Ascii)

  test("the two sets that need no Unicode at all are never swapped out"):
    GlyphSupport.values.foreach { support =>
      assert(BorderType.Ascii.degraded(support) == BorderType.Ascii)
      assert(BorderType.Blank.degraded(support) == BorderType.Blank)
    }

  test("an ASCII border draws the frame it promises"):
    val frame = rendered(Block(borderType = BorderType.Ascii), 5, 3)
    assert(bufferLine(frame, 0) == "+---+")
    assert(bufferLine(frame, 1) == "|   |")
    assert(bufferLine(frame, 2) == "+---+")

  /** The geometry invariant the [[BorderType]] Scaladoc states: every glyph in every set is one column wide, so
    * degrading a border must never move the content that sits inside it.
    */
  test("degrading a border leaves the interior rectangle exactly where it was"):
    val area = Rect(0, 0, 12, 6)
    assert(Block(borderType = BorderType.Ascii).inner(area) == Block(borderType = BorderType.Plain).inner(area))

  test("an ASCII border clips instead of throwing in areas too small to hold it"):
    assert(rendered(Block(borderType = BorderType.Ascii), 1, 1).area.width == 1)
    assert(rendered(Block(borderType = BorderType.Ascii), 0, 0).area.width == 0)

  /** A two-column grapheme inside a one-column-glyph frame is the case that used to shift a border sideways: the title
    * has to be measured on display columns, not on `String.length`.
    */
  test("a CJK title inside an ASCII border still truncates on display columns"):
    val titled = Block(Seq(BlockTitle.top(io.worxbend.tui.core.Line.raw("日本語です"))), borderType = BorderType.Ascii)
    val frame  = rendered(titled, 6, 3)
    // `line` returns one entry per non-continuation cell, so it is display columns that must add up to six here,
    // not UTF-16 code units: the title is two double-width graphemes plus the two corner glyphs.
    assert(io.worxbend.tui.core.CharWidth.of(bufferLine(frame, 0)) == 6)
    assert(bufferLine(frame, 0) == "+日本+")
    assert(bufferLine(frame, 2) == "+----+")

  // ------------------------------------------------------------------ spinners

  test("a braille spinner degrades to the ASCII line spinner below Full"):
    assert(SpinnerPreset.Dots.degraded(GlyphSupport.Full) == SpinnerPreset.Dots)
    assert(SpinnerPreset.Dots.degraded(GlyphSupport.BoxDrawing) == SpinnerPreset.Line)
    assert(SpinnerPreset.Dots.degraded(GlyphSupport.Ascii) == SpinnerPreset.Line)

  test("a preset already made of ASCII is kept at every rung, whatever else changes about it"):
    SpinnerPreset.AsciiPresets.foreach { preset =>
      GlyphSupport.values.foreach(support => assert(preset.degraded(support) == preset, s"${preset.name}/$support"))
    }

  test("degrading a spinner yields frames that really are ASCII"):
    val degraded = SpinnerPreset.Dots.degraded(GlyphSupport.Ascii)
    assert(degraded.frames.forall(frame => frame.forall(c => c >= ' ' && c <= '~')))

  // ------------------------------------------------------------------ progress bars

  test("a block-element progress vocabulary degrades to the ASCII one below Full"):
    assert(ProgressPreset.Blocks.degraded(GlyphSupport.Full) == ProgressPreset.Blocks)
    assert(ProgressPreset.Blocks.degraded(GlyphSupport.Ascii) == ProgressPreset.Ascii)
    assert(ProgressPreset.Shaded.degraded(GlyphSupport.BoxDrawing) == ProgressPreset.Ascii)

  test("the ASCII progress vocabularies survive every rung, head glyph included"):
    GlyphSupport.values.foreach { support =>
      assert(ProgressPreset.Ascii.degraded(support) == ProgressPreset.Ascii)
      assert(ProgressPreset.Arrow.degraded(support) == ProgressPreset.Arrow)
    }

  test("degrading a progress vocabulary yields glyphs that really are ASCII"):
    val degraded = ProgressPreset.Dots.degraded(GlyphSupport.Ascii)
    val glyphs   = degraded.glyphs(0.5, 8)
    assert(glyphs.forall(glyph => glyph.forall(c => c >= ' ' && c <= '~')))
    assert(glyphs.length == 8)
