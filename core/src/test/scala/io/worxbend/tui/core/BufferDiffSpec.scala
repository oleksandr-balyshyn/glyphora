package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** The callback form of [[Buffer.diff]] is what backends run every frame; it must agree with the iterator form exactly,
  * because only one of the two is exercised in production.
  */
final class BufferDiffSpec extends AnyFunSuite:

  private def collected(previous: Buffer, next: Buffer): Seq[(Position, Cell)] =
    val out = Seq.newBuilder[(Position, Cell)]
    previous.diff(next, (x, y, cell) => out += ((Position(x, y), cell)))
    out.result()

  private def styled(text: String): Buffer =
    val buffer = Buffer(Rect(0, 0, 12, 3))
    buffer.setString(0, 0, text, Style.Default)
    buffer

  test("the callback form emits exactly what the iterator form yields"):
    val cases = Seq(
      (styled(""), styled("hello")),
      (styled("hello"), styled("hello")),
      (styled("hello"), styled("hellp")),
      (styled("ab"), styled("漢字")),
      (styled("漢字"), styled("ab")),
      (styled("a漢b"), styled("a漢c")),
    )
    cases.foreach { (previous, next) =>
      assert(collected(previous, next) == previous.diff(next).toSeq)
    }

  test("identical frames produce no changes at all"):
    val frame = styled("unchanged")
    assert(collected(frame, frame.snapshot).isEmpty)

  test("a differing area repaints every cell"):
    val small = Buffer(Rect(0, 0, 4, 1))
    val large = Buffer(Rect(0, 0, 6, 1))
    assert(collected(small, large).size == 6)

  test("the continuation cell of a wide grapheme is never emitted on its own"):
    val previous = styled("ab")
    val next     = styled("漢")
    val changes  = collected(previous, next)
    // only the wide cell is flushed: painting it covers both columns, so emitting the continuation would overwrite it
    assert(changes.map(_._1.x) == Seq(0))
    assert(changes.head._2.symbol == "漢")

  test("ASCII writes land one column per character with the same symbols as the general path"):
    // setString takes an allocation-free fast path for printable ASCII; it must be observationally identical
    val fast = Buffer(Rect(0, 0, 8, 1))
    fast.setString(0, 0, "abc", Style.Default)
    val slow = Buffer(Rect(0, 0, 8, 1))
    slow.setString(0, 0, "abéc".replace("é", "b"), Style.Default) // same shape, non-ASCII path warmed
    assert((0 until 3).map(x => fast.get(x, 0).symbol) == Seq("a", "b", "c"))
    assert(fast.get(3, 0) == Cell.Empty)

  test("ASCII writes clip at the right edge exactly like the general path"):
    val buffer = Buffer(Rect(0, 0, 3, 1))
    buffer.setString(0, 0, "abcdef", Style.Default)
    assert((0 until 3).map(x => buffer.get(x, 0).symbol) == Seq("a", "b", "c"))

  test("control characters are dropped by both paths"):
    val buffer = Buffer(Rect(0, 0, 6, 1))
    buffer.setString(0, 0, "a\r\nb", Style.Default)
    assert(buffer.get(0, 0).symbol == "a")
    assert(buffer.get(1, 0).symbol == "b")
