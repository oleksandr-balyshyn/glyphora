package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect}
import org.scalatest.funsuite.AnyFunSuite

class ScratchPerfSpec extends AnyFunSuite:

  private def timeChart(n: Int, zigzag: Boolean): Long =
    val pts =
      if zigzag then (0 until n).map(i => if i % 2 == 0 then (0.0, 0.0) else (100.0, 100.0))
      else (0 until n).map(i => (i.toDouble * 100.0 / n, 50.0 + 40.0 * math.sin(i / 7.0)))
    val chart  = Chart(
      Seq(Dataset("z", pts, graphType = GraphType.Area)),
      (0.0, 100.0),
      (0.0, 100.0),
      resolution = CanvasResolution.Braille,
    )
    val area   = Rect(0, 0, 200, 50)
    val buffer = Buffer(area)
    val t0     = System.nanoTime()
    chart.render(area, buffer)
    (System.nanoTime() - t0) / 1000000

  test("scratch: area chart cost growth") {
    // warmup
    timeChart(200, false)
    for n <- Seq(5000, 10000, 25000, 50000) do
      info(s"sorted   n=$n -> ${timeChart(n, false)} ms")
    for n <- Seq(250, 500, 1000, 2000) do
      info(s"zigzag   n=$n -> ${timeChart(n, true)} ms")
  }
