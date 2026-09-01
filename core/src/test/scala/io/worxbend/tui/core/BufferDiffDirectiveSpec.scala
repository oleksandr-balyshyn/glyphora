package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** The two per-position instructions a frame can give the flush: never paint this ([[DiffDirective.Skip]], for columns
  * an image protocol owns) and always paint this ([[DiffDirective.AlwaysUpdate]], for columns something else may have
  * painted over).
  *
  * Every assertion here is about which positions [[Buffer.diff]] and [[Buffer.emitAll]] hand to a backend, so the tests
  * collect emissions rather than comparing frames.
  */
final class BufferDiffDirectiveSpec extends AnyFunSuite:

  private def emitted(previous: Buffer, next: Buffer): Seq[(Position, Cell)] =
    val out = Seq.newBuilder[(Position, Cell)]
    previous.diff(next, (x, y, cell) => out += ((Position(x, y), cell)))
    out.result()

  private def positionsOf(previous: Buffer, next: Buffer): Seq[Position] =
    emitted(previous, next).map(_._1)

  private def repainted(buffer: Buffer): Seq[Position] =
    val out = Seq.newBuilder[Position]
    buffer.emitAll((x, y, _) => out += Position(x, y))
    out.result()

  private def frame(text: String): Buffer =
    val buffer = Buffer(Rect(0, 0, 6, 2))
    buffer.setString(0, 0, text, Style.Default)
    buffer

  test("a fresh buffer gives every position the default directive"):
    val buffer = Buffer(Rect(0, 0, 3, 2))
    assert(buffer.diffDirective(0, 0) == DiffDirective.Default)
    assert(buffer.diffDirective(2, 1) == DiffDirective.Default)

  test("a skipped position is not emitted even though its content changed"):
    val previous = frame("aaaaaa")
    val next     = frame("abcdef")
    next.setDiffDirective(Rect(2, 0, 1, 1), DiffDirective.Skip)
    val changed  = positionsOf(previous, next)
    assert(!changed.contains(Position(2, 0)))
    // the neighbours of a reserved column are ordinary cells and must still be flushed
    assert(changed.contains(Position(1, 0)) && changed.contains(Position(3, 0)))

  test("skipping covers exactly the rectangle asked for"):
    val previous = frame("aaaaaa")
    val next     = frame("bbbbbb")
    next.setDiffDirective(Rect(1, 0, 3, 1), DiffDirective.Skip)
    assert(positionsOf(previous, next) == Seq(Position(0, 0), Position(4, 0), Position(5, 0)))

  test("a skipped position is suppressed by the full repaint too"):
    // a resize is exactly when a blind repaint would erase a picture this renderer cannot redraw
    val buffer = frame("abcdef")
    buffer.setDiffDirective(Rect(0, 1, 6, 1), DiffDirective.Skip)
    assert(repainted(buffer).forall(_.y == 0))

  test("giving a skipped region back repaints it even when the content matches"):
    // while the region was skipped, nothing about it was ever flushed, so the previous frame's memory of those cells
    // describes something the terminal was never shown
    val previous = frame("abcdef")
    previous.setDiffDirective(Rect(2, 0, 2, 1), DiffDirective.Skip)
    val next     = frame("abcdef")
    assert(positionsOf(previous, next) == Seq(Position(2, 0), Position(3, 0)))

  test("a region skipped in both frames stays unflushed"):
    val previous = frame("abcdef")
    previous.setDiffDirective(Rect(2, 0, 2, 1), DiffDirective.Skip)
    val next     = frame("abcdef")
    next.setDiffDirective(Rect(2, 0, 2, 1), DiffDirective.Skip)
    assert(positionsOf(previous, next).isEmpty)

  test("skipping suppresses the flush but not the write"):
    val buffer = Buffer(Rect(0, 0, 4, 1))
    buffer.setDiffDirective(Rect(0, 0, 4, 1), DiffDirective.Skip)
    buffer.setString(0, 0, "text", Style.Default)
    assert(buffer.get(0, 0) == Cell("t", Style.Default))
    assert(buffer.diffDirective(0, 0) == DiffDirective.Skip)

  test("an always-update region is emitted although nothing in it changed"):
    val previous = frame("abcdef")
    val next     = frame("abcdef")
    next.setDiffDirective(Rect(1, 0, 3, 2), DiffDirective.AlwaysUpdate)
    val changes  = emitted(previous, next)
    assert(
      changes.map(_._1) == Seq(
        Position(1, 0),
        Position(2, 0),
        Position(3, 0),
        Position(1, 1),
        Position(2, 1),
        Position(3, 1),
      )
    )
    // the cells handed over are the current content, not a blank or a stale value
    assert(changes.head._2 == Cell("b", Style.Default))

  test("an always-update region emits alongside a genuine change elsewhere, each position once"):
    val previous = frame("abcdef")
    val next     = frame("abcdeZ")
    next.setDiffDirective(Rect(0, 0, 1, 1), DiffDirective.AlwaysUpdate)
    assert(positionsOf(previous, next) == Seq(Position(0, 0), Position(5, 0)))

  test("directives clip to the buffer's area instead of throwing"):
    val buffer = Buffer(Rect(0, 0, 3, 1))
    buffer.setDiffDirective(Rect(-4, -4, 20, 20), DiffDirective.Skip)
    assert(buffer.diffDirective(0, 0) == DiffDirective.Skip)
    assert(buffer.diffDirective(9, 9) == DiffDirective.Default)
    buffer.setDiffDirective(Rect(0, 0, 0, 0), DiffDirective.AlwaysUpdate)
    assert(buffer.diffDirective(0, 0) == DiffDirective.Skip)

  test("reset clears every directive"):
    val previous = frame("abcdef")
    val next     = frame("abcdef")
    next.setDiffDirective(Rect(0, 0, 6, 1), DiffDirective.AlwaysUpdate)
    next.reset()
    next.setString(0, 0, "abcdef", Style.Default)
    assert(next.diffDirective(0, 0) == DiffDirective.Default)
    assert(positionsOf(previous, next).isEmpty)

  test("snapshot and copyFrom carry the directives"):
    val buffer   = frame("abcdef")
    buffer.setDiffDirective(Rect(0, 0, 2, 1), DiffDirective.Skip)
    assert(buffer.snapshot.diffDirective(1, 0) == DiffDirective.Skip)
    val recycled = Buffer(buffer.area)
    recycled.copyFrom(buffer)
    assert(recycled.diffDirective(1, 0) == DiffDirective.Skip)
    // a snapshot of a skipped frame is the baseline the release test above depends on
    assert(positionsOf(buffer.snapshot, frame("abcdef")) == Seq(Position(0, 0), Position(1, 0)))

  test("blit carries a fragment's directives into the frame"):
    val fragment = Buffer(Rect(0, 0, 2, 1))
    fragment.setString(0, 0, "xy", Style.Default)
    fragment.setDiffDirective(Rect(0, 0, 2, 1), DiffDirective.Skip)
    val target   = Buffer(Rect(0, 0, 6, 2))
    target.blit(fragment, Position(3, 1))
    assert(target.diffDirective(3, 1) == DiffDirective.Skip)
    assert(target.diffDirective(4, 1) == DiffDirective.Skip)
    assert(target.diffDirective(2, 1) == DiffDirective.Default)

  test("always-update never resurrects the reserved column of a two-column grapheme"):
    // painting 漢 paints both of its columns; a blank emitted into the second one would clip the glyph in half
    val previous = frame("ab")
    val next     = Buffer(Rect(0, 0, 6, 2))
    next.setString(0, 0, "漢", Style.Default)
    next.setDiffDirective(Rect(0, 0, 6, 1), DiffDirective.AlwaysUpdate)
    val changed  = positionsOf(previous, next)
    assert(changed.contains(Position(0, 0)))
    assert(!changed.contains(Position(1, 0)))

  test("skip and always-update are alternatives, not flags that accumulate"):
    val previous = frame("abcdef")
    val next     = frame("abcdef")
    next.setDiffDirective(Rect(0, 0, 6, 1), DiffDirective.AlwaysUpdate)
    next.setDiffDirective(Rect(0, 0, 6, 1), DiffDirective.Skip)
    assert(positionsOf(previous, next).isEmpty)
    next.setDiffDirective(Rect(0, 0, 6, 1), DiffDirective.Default)
    assert(positionsOf(previous, next).isEmpty)

  test("a skipped emoji column suppresses the trailing-cell workaround as well"):
    val previous = frame("ab")
    val next     = Buffer(Rect(0, 0, 6, 2))
    next.setString(0, 0, "⚠️", Style.Default)
    next.setDiffDirective(Rect(0, 0, 2, 1), DiffDirective.Skip)
    val out      = Seq.newBuilder[Position]
    previous.diff(next, (x, y, _) => out += Position(x, y), clearEmojiTrailingCell = true)
    assert(out.result().forall(_.x >= 2))
