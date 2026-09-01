package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Constraint, Line, Rect, Text}
import io.worxbend.tui.testsupport.Bench

/** Times composing one dashboard-like frame — the widget half of what a running application pays per tick, with no
  * terminal involved.
  *
  * Run it with:
  * {{{
  * ./mill widgets.test.runMain io.worxbend.tui.widgets.RenderLoopBench
  * }}}
  *
  * It asserts nothing and is not part of any suite; see [[io.worxbend.tui.testsupport.Bench]] for why a wall-clock
  * number is not a CI gate here. Its companion is `FramePipelineBench` in `terminal`, which times what happens to the
  * composed frame afterwards.
  *
  * Each size is measured twice, because the two numbers answer different questions:
  *
  *   - *fresh buffer* allocates a `Buffer` per frame, which is what a naive render loop costs;
  *   - *reused buffer* clears one buffer and draws into it again, which is what `RenderThread` actually does. The gap
  *     between the two is the allocation, so a change that claims to reduce allocation has somewhere to show it.
  */
object RenderLoopBench:

  private val sizes: Seq[(String, Rect)] =
    Seq(
      "40x12"  -> Rect(0, 0, 40, 12),
      "80x24"  -> Rect(0, 0, 80, 24),
      "200x50" -> Rect(0, 0, 200, 50),
      "255x64" -> Rect(0, 0, 255, 64),
    )

  def main(args: Array[String]): Unit =
    val _       = args
    val widget  = dashboard
    val results = sizes.flatMap { (label, area) =>
      val reused = Buffer(area)
      Seq(
        Bench.measure(s"$label fresh buffer", iterations = 200) { () =>
          val buffer = Buffer(area)
          widget.render(area, buffer)
          Bench.consume(buffer.area.width.toLong)
        },
        Bench.measure(s"$label reused buffer", iterations = 200) { () =>
          reused.reset()
          widget.render(area, reused)
          Bench.consume(reused.area.width.toLong)
        },
      )
    }
    Bench.report("Widget composition (one full frame)", results)
    println(s"checksum ${Bench.consumed}")

  /** A composition with the shapes a real dashboard has: a fixed header, two one-row meters, a small chart and a body
    * of wrapped prose that has to be reflowed on every frame.
    */
  private def dashboard: Column =
    Column(
      Seq(
        LayoutItem(Constraint.Length(1), Tabs(Seq("overview", "detail", "logs").map(Line.raw))),
        LayoutItem(Constraint.Length(1), Gauge(0.42)),
        LayoutItem(Constraint.Length(3), Sparkline((1L to 200L).map(n => n % 17))),
        LayoutItem(
          Constraint.Fill(1),
          Paragraph(Text.raw(("lorem ipsum dolor sit amet " * 40) + "\n"), overflow = Overflow.Wrap),
        ),
      )
    )
