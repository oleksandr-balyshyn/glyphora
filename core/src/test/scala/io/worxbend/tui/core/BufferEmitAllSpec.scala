package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** The full-repaint path, and the contract [[Buffer.diff]] now states instead of degrading into it.
  *
  * `diff` used to answer "these two buffers have different shapes" by emitting every cell of the new one. That is right
  * for the case it was written for — the first frame after a resize — and wrong for every other way two shapes can
  * disagree, which it turned into a silent full repaint on every frame rather than an error. The two are separate now:
  * [[Buffer.emitAll]] repaints, [[Buffer.diff]] refuses.
  */
final class BufferEmitAllSpec extends AnyFunSuite:

  private def positions(paint: ((Int, Int, Cell) => Unit) => Unit): Seq[Position] =
    val out = Seq.newBuilder[Position]
    paint((x, y, _) => out += Position(x, y))
    out.result()

  test("emitAll visits every cell of the buffer in row-major order"):
    val buffer = Buffer(Rect(0, 0, 3, 2))
    assert(
      positions(buffer.emitAll) == Seq(
        Position(0, 0),
        Position(1, 0),
        Position(2, 0),
        Position(0, 1),
        Position(1, 1),
        Position(2, 1),
      )
    )

  test("emitAll skips the second column of a two-column grapheme, as diff does"):
    val buffer = Buffer(Rect(0, 0, 3, 1))
    buffer.setString(0, 0, "漢", Style.Default)
    // (1, 0) is the right half of the glyph: painting the filler would draw a blank over the half already painted
    assert(positions(buffer.emitAll) == Seq(Position(0, 0), Position(2, 0)))

  test("emitAll reports the coordinates of a buffer that does not start at the origin"):
    val buffer = Buffer(Rect(4, 2, 2, 1))
    assert(positions(buffer.emitAll) == Seq(Position(4, 2), Position(5, 2)))

  test("emitAll on an empty area paints nothing"):
    assert(positions(Buffer(Rect(0, 0, 0, 0)).emitAll).isEmpty)

  test("emitAll hands over the cells themselves, not blanks"):
    val buffer = Buffer(Rect(0, 0, 2, 1))
    buffer.setString(0, 0, "ok", Style.Default.bold)
    val cells  = Seq.newBuilder[Cell]
    buffer.emitAll((_, _, cell) => cells += cell)
    assert(cells.result().map(_.symbol) == Seq("o", "k"))

  test("diffing buffers with different origins is a programmer error"):
    val failure = intercept[IllegalArgumentException] {
      Buffer(Rect(0, 0, 3, 3)).diff(Buffer(Rect(1, 0, 3, 3)), (_, _, _) => ())
    }
    // the message has to name both shapes: the whole point of failing is that the caller can see which pair is wrong
    assert(failure.getMessage.contains("Rect(0,0,3,3)") || failure.getMessage.contains("Rect(0, 0, 3, 3)"))
    assert(failure.getMessage.contains("emitAll"))

  test("diffing buffers of different widths is a programmer error"):
    intercept[IllegalArgumentException] {
      Buffer(Rect(0, 0, 3, 2)).diff(Buffer(Rect(0, 0, 4, 2)), (_, _, _) => ())
    }
    ()

  test("a next frame one row shorter is diffed over the rows the two frames share"):
    val previous = Buffer(Rect(0, 0, 3, 3))
    previous.setString(0, 0, "abc", Style.Default)
    previous.setString(0, 1, "def", Style.Default)
    val next     = Buffer(Rect(0, 0, 3, 2))
    next.setString(0, 0, "abc", Style.Default)
    next.setString(0, 1, "dxf", Style.Default)
    val changed  = Seq.newBuilder[Position]
    previous.diff(next, (x, y, _) => changed += Position(x, y))
    assert(changed.result() == Seq(Position(1, 1)))

  test("a taller next frame diffs only the rows it shares, leaving the extra row to the caller"):
    val previous = Buffer(Rect(0, 0, 2, 1))
    val next     = Buffer(Rect(0, 0, 2, 2))
    next.setString(0, 1, "hi", Style.Default)
    val changed  = Seq.newBuilder[Position]
    previous.diff(next, (x, y, _) => changed += Position(x, y))
    assert(changed.result().isEmpty)
