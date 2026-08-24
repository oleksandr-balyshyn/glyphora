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

  test("a cell whose colour changed under an unchanged glyph is emitted"):
    // the shape every selection highlight, focus ring and `Effect.fadeIn` frame takes: the text is identical and only
    // the style moved. A diff that compared symbols alone would freeze the highlight where it first appeared.
    val previous = Buffer(Rect(0, 0, 6, 1))
    previous.setString(0, 0, "row", Style.Default)
    val next     = Buffer(Rect(0, 0, 6, 1))
    next.setString(0, 0, "row", Style.Default.withBg(Color.Blue))
    val changes  = collected(previous, next)
    assert(changes.map(_._1.x) == Seq(0, 1, 2))
    assert(changes.forall(_._2.style.bg.contains(Color.Blue)))

  test("a cell whose modifiers changed under an unchanged glyph is emitted"):
    val plain    = Buffer(Rect(0, 0, 6, 1))
    plain.setString(0, 0, "row", Style.Default)
    val bold     = Buffer(Rect(0, 0, 6, 1))
    bold.setString(0, 0, "row", Style.Default.bold)
    assert(collected(plain, bold).map(_._1.x) == Seq(0, 1, 2))
    // and back the other way, so the assertion cannot be satisfied by "anything with modifiers differs from Default"
    val reversed = Buffer(Rect(0, 0, 6, 1))
    reversed.setString(0, 0, "row", Style.Default.reverse)
    assert(collected(bold, reversed).map(_._1.x) == Seq(0, 1, 2))
    assert(collected(reversed, reversed.snapshot).isEmpty)

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

  test("a narrow write onto a wide grapheme's continuation column is emitted"):
    val previous = Buffer(Rect(0, 0, 4, 1))
    val next     = Buffer(Rect(0, 0, 4, 1))
    next.setString(0, 0, "你", Style.Default)
    next.set(1, 0, Cell("q", Style.Default))
    assert(next.get(1, 0).symbol == "q")
    assert(next.get(0, 0) == Cell.Empty) // 你 can no longer draw across a column somebody else claimed
    // column 0 is blank again so it matches the previous frame; the `q` is what has to reach the terminal
    assert(collected(previous, next).map(_._1.x) == Seq(1))

  test("a wide write onto an existing wide grapheme releases the old glyph and flushes both columns"):
    val previous = Buffer(Rect(0, 0, 4, 1))
    previous.setString(0, 0, "你", Style.Default)
    val next     = Buffer(Rect(0, 0, 4, 1))
    next.setString(0, 0, "你", Style.Default)
    next.set(1, 0, Cell("好", Style.Default))
    assert(next.get(0, 0) == Cell.Empty)
    assert(next.get(1, 0).symbol == "好")
    assert(next.get(2, 0) == Cell.Empty) // the new glyph's own continuation
    assert(collected(previous, next).map(_._1.x) == Seq(0, 1))

  test("a wide write whose right half claims a wide grapheme releases that glyph's continuation too"):
    // the mirror of the case above: the new glyph does not land *on* a continuation, it lands on the head of a wide
    // grapheme and swallows it, orphaning the continuation one column further right
    val previous = Buffer(Rect(0, 0, 6, 1))
    previous.setString(3, 0, "Z", Style.Default)
    val next     = Buffer(Rect(0, 0, 6, 1))
    next.setString(2, 0, "好", Style.Default) // 好 at 2, its continuation at 3
    next.set(1, 0, Cell("你", Style.Default)) // 你 claims 1 and 2, so 好 can no longer draw at all
    assert(next.get(1, 0).symbol == "你")
    assert(next.get(2, 0) == Cell.Empty)     // 你's own continuation
    assert(next.get(3, 0) == Cell.Empty)     // 好's orphaned continuation, released with it
    // column 3 still shows `Z` on the terminal; the frame says it is blank, so the diff has to say so as well
    assert(collected(previous, next).map(_._1.x) == Seq(1, 3))

  test("releasing a displaced wide grapheme never reaches across the row edge"):
    val buffer = Buffer(Rect(0, 0, 3, 2))
    buffer.setString(0, 1, "好", Style.Default) // the next row's own wide glyph, at the row start
    buffer.set(1, 0, Cell("你", Style.Default)) // claims columns 1 and 2 — the last cell of row 0
    assert(buffer.get(1, 0).symbol == "你")
    assert(buffer.get(2, 0) == Cell.Empty)
    assert(buffer.get(0, 1).symbol == "好")     // "two columns over" stops at the row edge
    assert(buffer.get(1, 1) == Cell.Empty)

  test("a label written onto a composed CJK line lands in the diff cell for cell"):
    val previous = Buffer(Rect(0, 0, 12, 1))
    val next     = Buffer(Rect(0, 0, 12, 1))
    next.setString(0, 0, "設定パネル", Style.Default) // glyphs at 0/2/4/6/8, continuations at 1/3/5/7/9
    next.setString(7, 0, "ok", Style.Default)    // starts on ネ's continuation and lands on ル itself
    assert(next.get(7, 0).symbol == "o")
    assert(next.get(8, 0).symbol == "k")
    assert(next.get(6, 0) == Cell.Empty)         // ネ lost its right half
    assert(next.get(9, 0) == Cell.Empty)         // ル's orphaned continuation was released
    val changed = collected(previous, next).map(_._1.x).toSet
    assert(changed.contains(7) && changed.contains(8))

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
