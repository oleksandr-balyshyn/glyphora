package io.worxbend.tui.widgets

import io.worxbend.tui.core.CharWidth
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

/** The named single-dot markers, and the promise that each of them actually fits in one cell. */
final class MarkerSpec extends AnyFunSuite:

  private val every = Seq(Marker.Dot, Marker.Circle, Marker.Block, Marker.Ascii)

  test("every named marker is exactly one column wide"):
    // A wider glyph would be silently swapped for the fallback, which would make naming it pointless.
    every.foreach(marker => assert(CharWidth.of(marker) == 1, s"'$marker'"))
    every.foreach(marker => assert(SubCell.safeMarker(marker) == marker, s"'$marker'"))

  test("the canvas default is the named dot, not a private literal"):
    val canvas = Canvas((0.0, 1.0), (0.0, 1.0), Seq(Shape.Points(Seq((0.0, 0.0)))))
    assert(canvas.marker == Marker.Dot)
    assert(trimmedLines(rendered(canvas, 3, 3)) == Seq("", "", Marker.Dot))

  test("the fallback for an over-wide marker is the same named dot"):
    assert(SubCell.FallbackMarker == Marker.Dot)
    val canvas = Canvas((0.0, 1.0), (0.0, 1.0), Seq(Shape.Points(Seq((0.0, 0.0)))), marker = "🙂")
    assert(trimmedLines(rendered(canvas, 3, 3)) == Seq("", "", Marker.Dot))

  test("a named marker can be handed to a canvas and comes out as itself"):
    every.foreach { marker =>
      val canvas = Canvas((0.0, 1.0), (0.0, 1.0), Seq(Shape.Points(Seq((0.0, 0.0)))), marker = marker)
      assert(trimmedLines(rendered(canvas, 3, 3)).last == marker, s"'$marker'")
    }

  test("the named markers are distinct from one another"):
    assert(every.distinct.size == every.size)
