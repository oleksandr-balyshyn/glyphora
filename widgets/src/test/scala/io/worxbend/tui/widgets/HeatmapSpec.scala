package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class HeatmapSpec extends AnyFunSuite:

  test("a heatmap maps values onto the shade ramp"):
    val buffer = rendered(Heatmap(Seq(Seq(0.0, 0.5, 1.0))), 3, 1)
    assert(buffer.get(0, 0).symbol == " ")
    assert(buffer.get(1, 0).symbol == "▒")
    assert(buffer.get(2, 0).symbol == "█")

  test("heatmap rows clip to the area"):
    val buffer = Buffer(Rect(0, 0, 1, 2))
    Heatmap(Seq(Seq(1.0), Seq(1.0), Seq(1.0))).render(buffer.area, buffer)
    assert(trimmedLines(buffer) == Seq("█", "█"))
