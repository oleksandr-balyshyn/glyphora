package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** The opt-in workaround for terminals that draw an emoji presentation sequence in one column.
  *
  * "❤️" — a heart followed by U+FE0F, the selector asking for the colourful emoji form — measures two columns here, so
  * the buffer reserves the column to its right and the diff never emits it. A terminal that draws the sequence in a
  * single column then never repaints that reserved column, and whatever an earlier frame left there stays on screen.
  * With the flag set, a blank is emitted into it.
  */
final class BufferEmojiTrailingCellSpec extends AnyFunSuite:

  private val Heart = "❤️"

  private def collected(previous: Buffer, next: Buffer, clear: Boolean): Seq[(Position, Cell)] =
    val out = Seq.newBuilder[(Position, Cell)]
    previous.diff(next, (x, y, cell) => out += ((Position(x, y), cell)), clearEmojiTrailingCell = clear)
    out.result()

  private def frame(write: Buffer => Unit): Buffer =
    val buffer = Buffer(Rect(0, 0, 4, 1))
    write(buffer)
    buffer

  test("the reserved column of a VS16 cluster is emitted as a blank when the workaround is on"):
    val previous = frame(_.setString(0, 0, "xy", Style.Default))
    val next     = frame(_.setString(0, 0, Heart, Style.Default))
    val changes  = collected(previous, next, clear = true)
    assert(changes.map(_._1.x) == Seq(0, 1))
    assert(changes.map(_._2.symbol) == Seq(Heart, " "))

  test("the reserved column stays unpainted when the workaround is off, which is the default"):
    val previous = frame(_.setString(0, 0, "xy", Style.Default))
    val next     = frame(_.setString(0, 0, Heart, Style.Default))
    assert(collected(previous, next, clear = false).map(_._1.x) == Seq(0))
    val out      = Seq.newBuilder[Int]
    previous.diff(next, (x, _, _) => out += x)
    assert(out.result() == Seq(0))

  test("the blank carries the emoji's own style, so a background fill stays continuous"):
    val previous = frame(_ => ())
    val next     = frame(_.setString(0, 0, Heart, Style.Default.withBg(Color.Blue)))
    val changes  = collected(previous, next, clear = true)
    assert(changes.map(_._2.style.bg) == Seq(Some(Color.Blue), Some(Color.Blue)))

  test("a two-column cluster with no presentation selector keeps its reserved column unpainted"):
    // "漢" is wide because the character is wide, not because a selector asked for an emoji form. No terminal draws it
    // in one column, so there is nothing to work around and the extra blank would only risk clipping the glyph.
    val previous = frame(_ => ())
    val next     = frame(_.setString(0, 0, "漢", Style.Default))
    assert(collected(previous, next, clear = true).map(_._1.x) == Seq(0))

  test("an unchanged emoji emits nothing at all, workaround or not"):
    val previous = frame(_.setString(0, 0, Heart, Style.Default))
    val next     = previous.snapshot
    assert(collected(previous, next, clear = true).isEmpty)

  test("the blank is never written past the row's last column"):
    // a VS16 cluster aimed at the final column is stored as a blank, since there is no column left to reserve; the
    // workaround must not invent an emission in the row that follows
    val previous = Buffer(Rect(0, 0, 2, 2))
    val next     = Buffer(Rect(0, 0, 2, 2))
    next.setString(1, 0, Heart, Style.Default)
    assert(collected(previous, next, clear = true).forall(_._1.y == 0))

  test("hasEmojiPresentationSelector answers for the cluster, not for its width"):
    assert(CharWidth.hasEmojiPresentationSelector(Heart))
    assert(!CharWidth.hasEmojiPresentationSelector("漢"))
    assert(!CharWidth.hasEmojiPresentationSelector("❤︎")) // VS15 asks for the one-column text form
    assert(!CharWidth.hasEmojiPresentationSelector(""))
