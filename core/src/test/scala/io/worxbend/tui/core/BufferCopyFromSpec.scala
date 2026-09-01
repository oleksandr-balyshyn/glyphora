package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** Covers [[Buffer.copyFrom]]: the allocation-free counterpart of `snapshot`, used by a backend to keep a private
  * baseline of the frame it last flushed without allocating a new grid every frame.
  *
  * The interesting part is not that symbols arrive — it is that the *continuation flags* arrive with them. A flag marks
  * the second column of a two-column grapheme, and `diff` refuses to emit a flagged column because the terminal paints
  * it from the cell to its left. A copy that moved symbols but not flags would produce a baseline that disagrees with
  * the frame it was copied from about which columns exist, and the next diff would flush the wrong cells.
  */
final class BufferCopyFromSpec extends AnyFunSuite:

  private def buffer(write: Buffer => Unit): Buffer =
    val target = Buffer(Rect(0, 0, 6, 2))
    write(target)
    target

  test("every cell of the source is reproduced in the target"):
    val source = buffer(_.setString(0, 0, "hi", Style.Default.bold))
    val target = buffer(_.setString(0, 0, "xxxxxx", Style.Default))
    target.copyFrom(source)
    assert(target.get(0, 0) == source.get(0, 0))
    assert(target.get(1, 0) == source.get(1, 0))
    assert(target.get(5, 1) == Cell.Empty)

  test("cells the source does not cover are cleared, not left behind from the target's own contents"):
    // the failure this rules out is a copy that only writes where the source has content: the target would then keep
    // stale cells from an older frame and the next diff would think they were still on screen
    val source = buffer(_ => ())
    val target = buffer(_.setString(0, 0, "stale", Style.Default))
    target.copyFrom(source)
    assert((0 until 5).forall(x => target.get(x, 0) == Cell.Empty))

  test("a wide grapheme's continuation flag survives the copy"):
    val source = buffer(_.setString(0, 0, "漢", Style.Default))
    val target = buffer(_ => ())
    target.copyFrom(source)
    assert(target.get(0, 0).symbol == "漢")
    assert(target.isContinuation(1, 0))

  test("a copied buffer diffs against a blank frame exactly as its source does"):
    // the end-to-end statement of the flag case: a baseline copied this way must drive the same diff as the frame it
    // was copied from, or a backend using it flushes cells the terminal already shows and skips cells it does not
    val source                                     = buffer { target =>
      target.setString(0, 0, "a漢b", Style.Default)
      target.setString(0, 1, "é", Style.Default)
    }
    val target                                     = buffer(_ => ())
    target.copyFrom(source)
    val blank                                      = Buffer(Rect(0, 0, 6, 2))
    def cells(from: Buffer): Seq[(Position, Cell)] = blank.diff(from).toSeq
    assert(cells(target) == cells(source))

  test("copying a buffer onto itself changes nothing"):
    val target = buffer(_.setString(0, 0, "keep", Style.Default))
    target.copyFrom(target)
    assert(target.get(0, 0).symbol == "k")
    assert(target.get(3, 0).symbol == "p")

  test("writing to the source afterwards does not reach the target"):
    val source = buffer(_.setString(0, 0, "old", Style.Default))
    val target = buffer(_ => ())
    target.copyFrom(source)
    source.setString(0, 0, "new", Style.Default)
    assert(target.get(0, 0).symbol == "o")

  test("copying between different areas is rejected as the programmer error it is"):
    val source = Buffer(Rect(0, 0, 6, 2))
    val target = Buffer(Rect(0, 0, 6, 3))
    assertThrows[IllegalArgumentException](target.copyFrom(source))

  test("two buffers of equal size at different origins are still different areas"):
    // a resize is not the only way the areas can part company: an inline viewport composes into a rect with a non-zero
    // origin, and a grid copied across origins would claim to describe cells it never held
    val source = Buffer(Rect(0, 0, 4, 2))
    val target = Buffer(Rect(0, 5, 4, 2))
    assertThrows[IllegalArgumentException](target.copyFrom(source))

  test("an empty buffer copies without complaint"):
    val source = Buffer(Rect(0, 0, 0, 0))
    val target = Buffer(Rect(0, 0, 0, 0))
    target.copyFrom(source)
    assert(target.area.isEmpty)
