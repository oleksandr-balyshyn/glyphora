package io.worxbend.tui.widgets

import io.worxbend.tui.core.Modifiers
import io.worxbend.tui.testsupport.BufferAssertions.{line, rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class SliderSpec extends AnyFunSuite:

  test("the track runs between brackets with the knob positioned by value"):
    assert(trimmedLines(rendered(Slider(50, SliderRange.of(0, 100)), 11, 1)) == Seq("├────●────┤"))

  test("the minimum puts the knob on the first track column and the maximum on the last"):
    assert(trimmedLines(rendered(Slider(0, SliderRange.of(0, 100)), 11, 1)) == Seq("├●────────┤"))
    assert(trimmedLines(rendered(Slider(100, SliderRange.of(0, 100)), 11, 1)) == Seq("├────────●┤"))

  test("a value outside the range is clamped rather than drawn off the track"):
    assert(trimmedLines(rendered(Slider(-50, SliderRange.of(0, 100)), 11, 1)) == Seq("├●────────┤"))
    assert(trimmedLines(rendered(Slider(500, SliderRange.of(0, 100)), 11, 1)) == Seq("├────────●┤"))

  test("an empty range puts the knob at the start instead of dividing by zero"):
    // `max - min` is 0 here; the `math.max(1, ...)` guard is what stops the position arithmetic throwing mid-render
    assert(trimmedLines(rendered(Slider(7, SliderRange.of(7, 7)), 11, 1)) == Seq("├●────────┤"))

  test("SliderRange orders the bounds it is given and refuses a step that cannot move"):
    // a caller computing bounds from data cannot know which end came out larger, so the range takes either order
    assert(SliderRange.of(100, 0) == SliderRange.of(0, 100))
    // a zero step would make the DSL slider swallow Left/Right and change nothing; a negative one would reverse them
    assert(intercept[IllegalArgumentException](SliderRange.of(0, 100, 0)).getMessage.contains("at least 1"))
    assert(intercept[IllegalArgumentException](SliderRange.of(0, 100, -5)).getMessage.contains("at least 1"))

  test("the default range is a percentage in steps of five"):
    assert(SliderRange.Percent == SliderRange.of(0, 100, 5))

  test("nothing is drawn below the three columns a slider needs"):
    assert(line(rendered(Slider(50), 0, 1), 0) == "")
    assert(line(rendered(Slider(50), 1, 1), 0) == " ")
    assert(line(rendered(Slider(50), 2, 1), 0) == "  ")

  test("at exactly three columns the one-column track leaves the knob against the left bracket"):
    // `trackWidth - 1` is 0, so every value maps to the same position: the single track column, which at this width is
    // both the first and the last one
    assert(trimmedLines(rendered(Slider(0, SliderRange.of(0, 100)), 3, 1)) == Seq("├●┤"))
    assert(trimmedLines(rendered(Slider(100, SliderRange.of(0, 100)), 3, 1)) == Seq("├●┤"))

  test("the knob carries the knob style and the rest of the row the base style"):
    val buffer = rendered(Slider(0, SliderRange.of(0, 100)), 11, 1)
    assert(buffer.get(1, 0).style.modifiers.hasAny(Modifiers.Bold))  // the knob
    assert(!buffer.get(2, 0).style.modifiers.hasAny(Modifiers.Bold)) // the track beside it
    assert(!buffer.get(0, 0).style.modifiers.hasAny(Modifiers.Bold)) // the bracket
