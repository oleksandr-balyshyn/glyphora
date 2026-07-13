package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Constraint, Line, Rect, Text}

/** Simple timed render-loop benchmark (PLAN.md §12: measure before optimizing anything).
  *
  * Run with `./mill widgets.test.runMain io.worxbend.tui.widgets.RenderLoopBench`. Renders a dashboard-like composition
  * into a 200x50 buffer and reports frames/second — the number that would justify (or rule out) a `Style` interning
  * scheme (SPEC.md §9.2).
  */
object RenderLoopBench:

  def main(args: Array[String]): Unit =
    val area           = Rect(0, 0, 200, 50)
    val widget         = Column(
      Seq(
        LayoutItem(Constraint.Length(1), Tabs(Seq("overview", "detail", "logs").map(Line.raw))),
        LayoutItem(Constraint.Length(1), Gauge(0.42)),
        LayoutItem(Constraint.Length(3), Sparkline((1L to 200L).map(n => n % 17))),
        LayoutItem(Constraint.Fill(1), Paragraph(Text.raw(("lorem ipsum dolor sit amet " * 40) + "\n"), wrap = true)),
      )
    )
    val frames         = 2000
    // warmup
    renderFrames(widget, area, 200)
    val start          = System.nanoTime()
    renderFrames(widget, area, frames)
    val elapsedSeconds = (System.nanoTime() - start) / 1e9
    println(f"$frames frames in $elapsedSeconds%.2f s = ${frames / elapsedSeconds}%.0f fps (200x50 buffer)")

  private def renderFrames(widget: Column, area: Rect, count: Int): Unit =
    var i = 0
    while i < count do
      val buffer = Buffer(area)
      widget.render(area, buffer)
      i += 1
