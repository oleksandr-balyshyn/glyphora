package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Color, Rect, Style}

import org.scalatest.funsuite.AnyFunSuite

/** The encoder is the last stage before bytes reach the terminal, and the only stage `HeadlessBackend` does not
  * exercise: a headless test snapshots the composed [[Buffer]] and never asks how that buffer would be written out. So
  * every rule the encoder carries across cells — cursor position, SGR state, the open hyperlink — is pinned here or
  * nowhere.
  */
final class FrameEncoderSpec extends AnyFunSuite:

  private val encoder = FrameEncoder(ColorDepth.TrueColor)

  private def sgr(style: Style): String = AnsiSequences.sgr(style, ColorDepth.TrueColor)

  private def frame(write: Buffer => Unit): Buffer =
    val buffer = Buffer(Rect(0, 0, 10, 2))
    write(buffer)
    buffer

  test("an unchanged frame encodes to nothing at all"):
    val previous = frame(_.setString(0, 0, "hello", Style.Default))
    val next     = frame(_.setString(0, 0, "hello", Style.Default))
    assert(encoder.encode(previous, next) == "")

  test("a run of changed cells is positioned once and then written straight through"):
    val previous = frame(_ => ())
    val next     = frame(_.setString(2, 1, "ok", Style.Default))
    // one move to the start of the run, then both symbols: the cursor advances on its own within a row
    assert(encoder.encode(previous, next) == AnsiSequences.moveTo(2, 1) + sgr(Style.Default) + "ok")

  test("a hyperlink left open by the last changed cell is closed before the frame ends"):
    val previous = frame(_ => ())
    val next     = frame(_.setString(0, 0, "x", Style.Default.withLink("https://example.invalid")))
    assert(encoder.encode(previous, next).endsWith(AnsiSequences.LinkClose))

  // ---------------------------------------------------------------- SGR carry-over

  test("two styles in one row emit their own SGR sequences, in order"):
    // an encoder that dropped the cell's style and always wrote `sgr(Style.Default)` would still produce a frame of the
    // right shape with the right glyphs in the right places — and a colourless, unstyled terminal.
    val warning  = Style.Default.withFg(Color.Rgb(220, 160, 40)).bold
    val muted    = Style.Default.withFg(Color.Rgb(120, 120, 120))
    val previous = frame(_ => ())
    val next     = frame { buffer =>
      buffer.setString(0, 0, "ab", warning)
      buffer.setString(2, 0, "cd", muted)
    }
    assert(encoder.encode(previous, next) == AnsiSequences.moveTo(0, 0) + sgr(warning) + "ab" + sgr(muted) + "cd")

  test("a run sharing one style emits that SGR once, even when every cell holds its own equal Style"):
    // what `Effect.fadeIn` and every themed widget produce: a fresh but equal `Style` per cell. The encoder compares
    // structurally as well as by reference, so the sequence is written at the head of the run and not repeated.
    val previous = frame(_ => ())
    val next     = frame { buffer =>
      "hello".zipWithIndex.foreach { (character, x) =>
        buffer.setString(x, 0, character.toString, Style.Default.withFg(Color.Rgb(10, 20, 30)))
      }
    }
    val shared   = sgr(Style.Default.withFg(Color.Rgb(10, 20, 30)))
    assert(encoder.encode(previous, next) == AnsiSequences.moveTo(0, 0) + shared + "hello")

  // ---------------------------------------------------------------- the cursor model over wide graphemes

  test("the cursor is not repositioned across a wide grapheme, because the terminal advances two columns for it"):
    // `漢` occupies columns 1 and 2, so after writing it the terminal's cursor sits at column 3 — exactly where `b` is.
    // An encoder that assumed every glyph advances one column would think the cursor was at 2 and insert a move, and on
    // a real terminal every cell after the first wide glyph in a changed run would land one column left of where the
    // frame says it goes, overwriting the glyph's right half.
    val previous = frame(_ => ())
    val next     = frame(_.setString(0, 0, "a漢b", Style.Default))
    assert(encoder.encode(previous, next) == AnsiSequences.moveTo(0, 0) + sgr(Style.Default) + "a漢b")

  test("a changed cell past where a wide grapheme leaves the cursor is repositioned"):
    // the complement of the case above, so it cannot be satisfied by an encoder that never repositions within a row:
    // `漢` leaves the cursor at column 2, `b` is at column 3, and column 2 is unchanged so nothing was written there.
    val previous = frame(_ => ())
    val next     = frame { buffer =>
      buffer.setString(0, 0, "漢", Style.Default)
      buffer.setString(3, 0, "b", Style.Default)
    }
    assert(
      encoder.encode(previous, next) ==
        AnsiSequences.moveTo(0, 0) + sgr(Style.Default) + "漢" + AnsiSequences.moveTo(3, 0) + "b"
    )

  test("a frame of a different shape repaints in full instead of diffing"):
    // what a resize looks like from here: the previous frame describes a grid the terminal no longer has, so there is
    // nothing to compare against. `Buffer.diff` refuses such a pair outright, and the encoder is the caller that has to
    // know the difference between "no usable predecessor" and "somebody passed the wrong buffer".
    val previous = Buffer(Rect(0, 0, 2, 1))
    val next     = Buffer(Rect(0, 0, 4, 1))
    next.setString(0, 0, "grew", Style.Default)
    assert(encoder.encode(previous, next) == AnsiSequences.moveTo(0, 0) + sgr(Style.Default) + "grew")

  test("a repaint after a resize writes the blank cells too, so nothing of the old frame survives"):
    val previous = Buffer(Rect(0, 0, 6, 1))
    val next     = Buffer(Rect(0, 0, 3, 1))
    next.setString(0, 0, "a", Style.Default)
    // three cells written, not one: the two blanks are what clears whatever the old, wider frame had drawn there
    assert(encoder.encode(previous, next) == AnsiSequences.moveTo(0, 0) + sgr(Style.Default) + "a  ")
