package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Rect, Style}

import org.scalatest.funsuite.AnyFunSuite

final class FrameEncoderSpec extends AnyFunSuite:

  private val encoder = FrameEncoder(ColorDepth.TrueColor)

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
    assert(
      encoder.encode(previous, next) == AnsiSequences.moveTo(2, 1) + AnsiSequences.sgr(
        Style.Default,
        ColorDepth.TrueColor,
      ) + "ok"
    )

  test("a hyperlink left open by the last changed cell is closed before the frame ends"):
    val previous = frame(_ => ())
    val next     = frame(_.setString(0, 0, "x", Style.Default.withLink("https://example.invalid")))
    assert(encoder.encode(previous, next).endsWith(AnsiSequences.LinkClose))
