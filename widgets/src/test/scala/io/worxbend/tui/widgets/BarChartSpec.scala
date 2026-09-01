package io.worxbend.tui.widgets

import io.worxbend.tui.core.Direction
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class BarChartSpec extends AnyFunSuite:

  test("bars scale against the maximum with labels underneath"):
    val chart  = BarChart(Seq(("a", 8), ("b", 4)), barWidth = 1, barGap = 1, max = Some(8))
    val buffer = rendered(chart, 3, 3)
    assert(trimmedLines(buffer) == Seq("█", "█ █", "a b"))

  test("partial values top out with a fractional block"):
    val chart  = BarChart(Seq(("", 3)), barWidth = 1, max = Some(8), barGap = 0)
    val buffer = rendered(chart, 1, 1)
    assert(trimmedLines(buffer) == Seq("▃"))

  test("bars that do not fit the width are clipped"):
    val chart  = BarChart(Seq(("a", 8), ("b", 8), ("c", 8)), barWidth = 2, barGap = 1, max = Some(8))
    val buffer = rendered(chart, 5, 2)
    assert(trimmedLines(buffer).head == "██ ██")

  test("empty data renders nothing"):
    val buffer = rendered(BarChart(Seq.empty), 5, 3)
    assert(trimmedLines(buffer).forall(_.isEmpty))

  test("a horizontal chart draws bars rightwards with labels in a left gutter"):
    val chart  = BarChart(
      Seq(("api", 8), ("db", 4)),
      barGap = 0,
      max = Some(8),
      direction = Direction.Horizontal,
    )
    val buffer = rendered(chart, 8, 2)
    // the gutter is "api" plus a blank column; the remaining four columns carry the bars
    assert(trimmedLines(buffer) == Seq("api ████", " db ██"))

  test("a horizontal bar can be several rows thick"):
    val chart  = BarChart(Seq(("", 8)), barGap = 0, max = Some(8), direction = Direction.Horizontal, barHeight = 2)
    val buffer = rendered(chart, 4, 2)
    assert(trimmedLines(buffer) == Seq("████", "████"))

  test("a horizontal bar tops out with a fractional block at its leading edge"):
    val chart = BarChart(Seq(("", 1)), barGap = 0, max = Some(8), direction = Direction.Horizontal)
    // one eighth of a single column is the narrowest left-growing glyph
    assert(trimmedLines(rendered(chart, 1, 1)) == Seq("▏"))

  test("a horizontal bar that does not fit the height is dropped, not clipped"):
    val chart  = BarChart(
      Seq(("", 8), ("", 8), ("", 8)),
      barGap = 0,
      max = Some(8),
      direction = Direction.Horizontal,
      barHeight = 2,
    )
    val buffer = rendered(chart, 2, 3)
    // the third bar would need rows 4 and 5 of a three-row area, so it is not drawn at all
    assert(trimmedLines(buffer) == Seq("██", "██", ""))

  test("the label gutter never takes more than half the width"):
    val chart  = BarChart(
      Seq(("a very long category name", 8)),
      barGap = 0,
      max = Some(8),
      direction = Direction.Horizontal,
    )
    val buffer = rendered(chart, 10, 1)
    // five columns of gutter (four of label plus a blank) and five of bar, whatever the name's length; the
    // label keeps its beginning, which is the part that identifies the category
    assert(trimmedLines(buffer) == Seq("a ve █████"))

  test("a horizontal gutter measures a wide-character label in columns"):
    val chart  = BarChart(Seq(("負荷", 8)), barGap = 0, max = Some(8), direction = Direction.Horizontal)
    val buffer = rendered(chart, 10, 1)
    // 負荷 is two ideographs occupying four columns, so the gutter is five columns wide, not three
    assert(trimmedLines(buffer) == Seq("負荷 █████"))

  test("a horizontal chart with no labels gives the whole width to the bars"):
    val chart = BarChart(Seq(("", 8)), barGap = 0, max = Some(8), direction = Direction.Horizontal)
    assert(trimmedLines(rendered(chart, 4, 1)) == Seq("████"))

  test("empty data renders nothing in either direction"):
    Direction.values.foreach { direction =>
      val buffer = rendered(BarChart(Seq.empty, direction = direction), 5, 3)
      assert(trimmedLines(buffer).forall(_.isEmpty))
    }
