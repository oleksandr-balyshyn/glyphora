package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Rect, Size, Style}

import org.scalatest.funsuite.AnyFunSuite

/** Covers what the terminal layer contributes to an inline run: reserving rows on the primary screen, and encoding a
  * frame whose area does not start at the top-left of the screen.
  */
final class InlineViewportSpec extends AnyFunSuite:

  test("reserving rows is recorded and does not take the alternate screen"):
    val backend = HeadlessBackend(Size(20, 6))
    assert(backend.reserveInlineRows(3) == Right(()))
    assert(backend.reservedInlineRows == 3)
    assert(!backend.isAlternateScreen, "an inline run stays on the primary screen")

  test("a negative or zero reservation is none rather than a failure"):
    val backend = HeadlessBackend(Size(20, 6))
    assert(backend.reserveInlineRows(-2) == Right(()))
    assert(backend.reservedInlineRows == 0)

  test("a frame anchored below the top of the screen is written at its absolute row"):
    // The load-bearing assumption of the inline viewport: the composer hands the backend a buffer whose area starts at
    // a non-zero row, and the encoder must address the terminal in absolute coordinates rather than relative to the
    // buffer's own origin. If this ever became relative, an inline app would paint over the shell's output above it.
    val encoder  = FrameEncoder(ColorDepth.TrueColor)
    val area     = Rect(0, 20, 10, 2)
    val previous = Buffer(area)
    val next     = Buffer(area)
    next.setString(0, 20, "hi", Style.Default)
    val encoded  = encoder.encode(previous, next)
    assert(encoded.contains(AnsiSequences.moveTo(0, 20)))
    assert(!encoded.contains(AnsiSequences.moveTo(0, 0)))
