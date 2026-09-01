package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Cell, Color, Rect, Style}
import io.worxbend.tui.testsupport.Bench

/** Times the two functions every flushed frame pays for: `Buffer.diff`, which finds the changed cells, and
  * `FrameEncoder.encode`, which turns them into one batched escape string.
  *
  * Run it with:
  * {{{
  * ./mill terminal.test.runMain io.worxbend.tui.terminal.FramePipelineBench
  * }}}
  *
  * It asserts nothing and is not part of any suite — see [[io.worxbend.tui.testsupport.Bench]] for why a wall-clock
  * number is not a CI gate here. It lives in `terminal`'s tests because `FrameEncoder` is `private[terminal]`, so this
  * is the only place both halves of the flush path can be measured in one file.
  */
object FramePipelineBench:

  /** The frame sizes worth separating: a small pane, an ordinary window, a large one, and a full 4K-ish terminal. Cost
    * per cell is not constant — the encoder emits a cursor move per changed run, so the shape of the change matters as
    * much as its size.
    */
  private val sizes: Seq[(String, Rect)] =
    Seq(
      "16x16"  -> Rect(0, 0, 16, 16),
      "64x32"  -> Rect(0, 0, 64, 32),
      "200x50" -> Rect(0, 0, 200, 50),
      "255x64" -> Rect(0, 0, 255, 64),
    )

  /** The three shapes a real frame takes between two flushes: an idle tick where nothing moved, a cursor blink or a
    * clock tick where one cell moved, and the first frame after a resize or a screen change where everything moved.
    */
  private enum Change:
    case Idle, OneCell, Full

  def main(args: Array[String]): Unit =
    val _        = args
    val encoders = Seq("truecolor" -> FrameEncoder(ColorDepth.TrueColor), "ansi16" -> FrameEncoder(ColorDepth.Ansi16))
    val diffs    = for
      (label, area) <- sizes
      change        <- Change.values.toSeq
    yield
      val (previous, next) = framePair(area, change)
      Bench.measure(s"diff $label ${name(change)}", iterations = 2000) { () =>
        previous.diff(next, (x, y, cell) => Bench.consume(x.toLong + y + cell.symbol.length))
      }
    val encodes  = for
      (label, area)    <- sizes
      change           <- Change.values.toSeq
      (depth, encoder) <- encoders
    yield
      val (previous, next) = framePair(area, change)
      Bench.measure(s"encode $label ${name(change)} $depth", iterations = 2000) { () =>
        Bench.consume(encoder.encode(previous, next).length.toLong)
      }
    Bench.report("Buffer.diff", diffs)
    println()
    Bench.report("FrameEncoder.encode", encodes)

  /** Two frames of `area` differing the way `change` says. The pair is built once, outside every timed region, so what
    * is measured is the diff and the encode rather than the cost of painting a buffer.
    *
    * Each row carries its own colour so the encoder's per-run style handling is exercised the way a real dashboard
    * exercises it, rather than by one uniform style it can memoise away.
    */
  private def framePair(area: Rect, change: Change): (Buffer, Buffer) =
    val previous = filled(area, offset = 0)
    val next     = change match
      case Change.Idle    => filled(area, offset = 0)
      case Change.Full    => filled(area, offset = 1)
      case Change.OneCell =>
        val buffer = filled(area, offset = 0)
        buffer.set(area.x + area.width / 2, area.y + area.height / 2, Cell("@", Style.fg(Color.Red)))
        buffer
    (previous, next)

  private def filled(area: Rect, offset: Int): Buffer =
    val buffer = Buffer(area)
    var y      = 0
    while y < area.height do
      val style = Style.fg(Color.Indexed(((y + offset) % 216 + 16).toByte))
      var x     = 0
      while x < area.width do
        buffer.set(area.x + x, area.y + y, Cell(glyphs((x + y + offset) % glyphs.length), style))
        x += 1
      y += 1
    buffer

  private val glyphs: Vector[String] = Vector("a", "b", "c", "─", "│", "█", " ")

  private def name(change: Change): String = change.toString.toLowerCase
