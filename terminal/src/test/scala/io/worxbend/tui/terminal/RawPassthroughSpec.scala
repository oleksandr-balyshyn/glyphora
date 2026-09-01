package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, DiffDirective, Rect, Size, Style}

import org.scalatest.funsuite.AnyFunSuite

/** Showing a picture a terminal draws for itself takes two halves, and this pins both: the frame stops flushing the
  * columns the picture covers ([[DiffDirective.Skip]]), and the payload that draws it reaches the terminal untouched
  * ([[Backend.writeRaw]]).
  */
final class RawPassthroughSpec extends AnyFunSuite:

  private val encoder = FrameEncoder(ColorDepth.TrueColor)

  private def frame(write: Buffer => Unit): Buffer =
    val buffer = Buffer(Rect(0, 0, 10, 2))
    write(buffer)
    buffer

  test("the encoder writes nothing for the columns a frame reserved"):
    val previous = frame(_ => ())
    val next     = frame { buffer =>
      buffer.setString(0, 0, "abcd", Style.Default)
      buffer.setDiffDirective(Rect(0, 0, 4, 1), DiffDirective.Skip)
    }
    assert(!encoder.encode(previous, next).contains("a"))

  test("the encoder repaints a region whose reservation was given back"):
    val previous = frame { buffer =>
      buffer.setString(0, 0, "abcd", Style.Default)
      buffer.setDiffDirective(Rect(0, 0, 4, 1), DiffDirective.Skip)
    }
    val next     = frame(_.setString(0, 0, "abcd", Style.Default))
    assert(encoder.encode(previous, next).contains("abcd"))

  test("a headless backend records every raw sequence in order"):
    val backend = HeadlessBackend(Size(10, 2))
    assert(backend.rawSequences.isEmpty)
    assert(backend.writeRaw("_Ga=T;payload\\").isRight)
    assert(backend.writeRaw("second").isRight)
    assert(backend.rawSequences == Seq("_Ga=T;payload\\", "second"))
    val _       = backend.close()
