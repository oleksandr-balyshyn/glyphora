package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class GlyphSupportSpec extends AnyFunSuite:

  test("permits is reflexive: every rung permits itself"):
    GlyphSupport.values.foreach(rung => assert(rung.permits(rung), s"$rung should permit itself"))

  test("permits is monotone: a higher rung permits everything a lower one does"):
    val expected = Map(
      (GlyphSupport.Ascii, GlyphSupport.BoxDrawing) -> false,
      (GlyphSupport.Ascii, GlyphSupport.Full)       -> false,
      (GlyphSupport.BoxDrawing, GlyphSupport.Ascii) -> true,
      (GlyphSupport.BoxDrawing, GlyphSupport.Full)  -> false,
      (GlyphSupport.Full, GlyphSupport.Ascii)       -> true,
      (GlyphSupport.Full, GlyphSupport.BoxDrawing)  -> true,
    )
    expected.foreach { case ((support, required), permitted) =>
      assert(support.permits(required) == permitted, s"$support.permits($required)")
    }

  /** The order of the enum cases *is* the meaning of the type, so a case inserted in the wrong place would silently
    * invert `permits`. Pinning the declaration order makes that a failing test rather than a rendering bug.
    */
  test("the rungs are declared floor to ceiling"):
    assert(GlyphSupport.values.toSeq == Seq(GlyphSupport.Ascii, GlyphSupport.BoxDrawing, GlyphSupport.Full))
