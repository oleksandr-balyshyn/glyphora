package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Cell, Modifiers, Rect, Style}
import io.worxbend.tui.testsupport.BufferAssertions.{line, rendered, renderedInto, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class GaugeSpec extends AnyFunSuite:

  test("the default label is the percentage, centered"):
    val buffer = rendered(Gauge(0.5), 10, 1)
    assert(trimmedLines(buffer) == Seq("   50%"))

  test("the filled region carries the filled style up to the ratio"):
    val buffer = rendered(Gauge(0.5), 10, 1)
    assert(buffer.get(0, 0).style.modifiers.hasAny(Modifiers.Reverse))
    assert(buffer.get(4, 0).style.modifiers.hasAny(Modifiers.Reverse))
    assert(!buffer.get(5, 0).style.modifiers.hasAny(Modifiers.Reverse))

  test("ratio is clamped to the unit interval"):
    assert(trimmedLines(rendered(Gauge(2.5), 10, 1)) == Seq("   100%"))
    assert(trimmedLines(rendered(Gauge(-1.0), 10, 1)) == Seq("    0%"))

  test("a custom label replaces the percentage"):
    val buffer = rendered(Gauge(0.3, label = ProgressLabel.Text("3/10")), 10, 1)
    assert(trimmedLines(buffer) == Seq("   3/10"))

  test("a hidden label leaves the bar uninterrupted"):
    assert(trimmedLines(rendered(Gauge(0.5, label = ProgressLabel.Hidden), 10, 1)) == Seq(""))

  test("text and percentage together read as one caption"):
    val buffer = rendered(Gauge(0.5, label = ProgressLabel.TextAndPercentage("sync")), 12, 1)
    assert(trimmedLines(buffer) == Seq("  sync 50%"))

  test("Gauge.of computes the ratio from counts"):
    assert(Gauge.of(3, 10).ratio == 0.3)
    assert(Gauge.of(1, 0).ratio == 0.0)

  test("the label lands on the middle row of a taller gauge"):
    val buffer = rendered(Gauge(0.0), 6, 3)
    assert(trimmedLines(buffer) == Seq("", "  0%", ""))

  test("a wide label fills the bar without spilling past the last column"):
    // 日本 is four columns wide, so in a five-column gauge it starts at column 0 and ends on column 3, leaving 4 blank
    val buffer = renderedInto(Gauge(0.0, label = ProgressLabel.Text("日本")), Rect(0, 0, 5, 1), 7, 1)
    assert(line(buffer, 0) == "日本   ")
    assert(buffer.get(5, 0) == Cell.Empty)
    assert(buffer.get(6, 0) == Cell.Empty)

  test("a label whose measured width undercounts its cells is still cut off at the area's edge"):
    // a leading combining mark measures zero columns but is drawn in a whole cell, so a label of four printable
    // clusters plus one mark needs five cells in a four-column gauge. The last cluster would land on the column past
    // the edge — the neighbouring widget's — so it is dropped rather than written there.
    val buffer = renderedInto(Gauge(0.0, label = ProgressLabel.Text("\u0301abcd")), Rect(0, 0, 4, 1), 6, 1)
    assert(line(buffer, 0) == " abc  ")
    assert(buffer.get(4, 0) == Cell.Empty)

  test("an over-wide label starts at the left edge instead of left of it"):
    // the label is clipped to the area's width first, so the centring difference is never negative and the text cannot
    // begin at a negative column
    val buffer = rendered(Gauge(0.0, label = ProgressLabel.Text("far too long for this")), 4, 1)
    assert(line(buffer, 0) == "far ")

  test("a preset draws the bar as glyphs instead of blank cells"):
    val buffer = rendered(Gauge(0.5, label = ProgressLabel.Hidden, preset = Some(ProgressPreset.Ascii)), 10, 1)
    assert(line(buffer, 0) == "#####-----")

  test("a sub-cell preset paints the boundary cell with a partial block"):
    // 0.37 of 10 cells is 3.7: three full blocks, then seven tenths of a cell, which rounds down to five eighths
    val buffer = rendered(Gauge(0.37, label = ProgressLabel.Hidden, preset = Some(ProgressPreset.Blocks)), 10, 1)
    assert(line(buffer, 0) == "███▋      ")

  test("a sub-cell preset shows progress a whole-cell bar cannot"):
    val cell = (ratio: Double) =>
      line(rendered(Gauge(ratio, label = ProgressLabel.Hidden, preset = Some(ProgressPreset.Blocks)), 1, 1), 0)
    // on a one-cell bar the rounded gauge only has "empty" and "full"; the eighths give six more steps in between
    assert(cell(0.2) == "▏")
    assert(cell(0.5) == "▌")
    assert(cell(0.9) == "▉")
    assert(cell(1.0) == "█")

  test("a preset bar is drawn on every row of the area"):
    val buffer = rendered(Gauge(1.0, label = ProgressLabel.Hidden, preset = Some(ProgressPreset.Ascii)), 4, 3)
    assert(trimmedLines(buffer) == Seq("####", "####", "####"))

  test("the filled glyphs carry the filled style and the track does not"):
    val gauge  = Gauge(0.5, label = ProgressLabel.Hidden, filledStyle = Style.Default.bold, preset = Some(ProgressPreset.Ascii))
    val buffer = rendered(gauge, 10, 1)
    assert(buffer.get(4, 0).style.modifiers.hasAny(Modifiers.Bold))
    assert(!buffer.get(5, 0).style.modifiers.hasAny(Modifiers.Bold))

  test("a preset drops Reverse from the fill style, because a reversed full block is invisible"):
    val buffer = rendered(Gauge(1.0, label = ProgressLabel.Hidden, preset = Some(ProgressPreset.Blocks)), 4, 1)
    assert(!buffer.get(0, 0).style.modifiers.hasAny(Modifiers.Reverse))

  test("without a preset the bar is still blank cells rounded to whole columns"):
    val buffer = rendered(Gauge(0.37, label = ProgressLabel.Hidden), 10, 1)
    assert(line(buffer, 0) == "          ")
    // 0.37 of 10 rounds to 4 filled columns
    assert(buffer.get(3, 0).style.modifiers.hasAny(Modifiers.Reverse))
    assert(!buffer.get(4, 0).style.modifiers.hasAny(Modifiers.Reverse))

  test("a preset bar clipped to no columns draws nothing and does not throw"):
    val buffer = renderedInto(Gauge(0.5, preset = Some(ProgressPreset.Blocks)), Rect(0, 0, 0, 1), 4, 1)
    assert(trimmedLines(buffer) == Seq(""))
