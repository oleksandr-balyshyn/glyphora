package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

/** Circles on a [[Canvas]]: how many samples one gets is a question about the dot grid, not about the world scale. */
final class CanvasCircleSpec extends AnyFunSuite:

  private val unit = (0.0, 1.0)

  test("a circle on normalized bounds is closed, not an octagon"):
    // radius 0.4 of a unit world used to get max(8, (0.4 * 32).toInt) = 12 samples across roughly 100 dots of arc.
    val canvas  =
      Canvas(unit, unit, Seq(Shape.CircleShape(0.5, 0.5, 0.4)), resolution = CanvasResolution.Braille)
    val buffer  = rendered(canvas, 20, 10)
    // Every row the circle spans has at least two lit cells: the left arc and the right arc.
    val spanned = (1 until 9).count { row =>
      (0 until 20).count(column => buffer.get(column, row).symbol != " ") >= 2
    }
    assert(spanned == 8)

  test("the same circle is drawn identically whatever the world scale"):
    def linesFor(scale: Double): Seq[String] =
      val bounds = (0.0, scale)
      val canvas = Canvas(bounds, bounds, Seq(Shape.CircleShape(scale / 2, scale / 2, scale * 0.4)), marker = "o")
      trimmedLines(rendered(canvas, 21, 21))
    assert(linesFor(1.0) == linesFor(1000.0))
    assert(linesFor(1.0) == linesFor(0.002))

  test("a circle still stays within its radius and leaves the centre alone"):
    val canvas = Canvas((0.0, 10.0), (0.0, 10.0), Seq(Shape.CircleShape(5.0, 5.0, 3.0)), marker = "o")
    val buffer = rendered(canvas, 11, 11)
    assert(buffer.get(5, 2).symbol == "o") // top of the circle: world (5, 8) maps to row 2
    assert(buffer.get(5, 5).symbol == " ") // centre untouched

  test("a non-finite circle draws nothing"):
    Seq(
      Shape.CircleShape(0.5, 0.5, Double.NaN),
      Shape.CircleShape(Double.PositiveInfinity, 0.5, 0.2),
      Shape.CircleShape(0.5, Double.NegativeInfinity, 0.2),
    ).foreach { circle =>
      assert(trimmedLines(rendered(Canvas(unit, unit, Seq(circle), marker = "o"), 11, 11)).forall(_.isEmpty))
    }

  test("an enormous radius renders in bounded time rather than stalling"):
    // The sample count is capped, so this returns; almost all of it is outside the bounds and therefore dropped.
    val canvas = Canvas(unit, unit, Seq(Shape.CircleShape(0.5, 0.5, 1e12)), marker = "o")
    assert(trimmedLines(rendered(canvas, 11, 11)).forall(_.isEmpty))

  test("a zero radius marks the centre once and nothing else"):
    val canvas = Canvas(unit, unit, Seq(Shape.CircleShape(0.5, 0.5, 0.0)), marker = "o")
    assert(trimmedLines(rendered(canvas, 5, 5)).count(_.nonEmpty) == 1)

  test("a circle on a canvas with no room renders without throwing"):
    val canvas = Canvas(unit, unit, Seq(Shape.CircleShape(0.5, 0.5, 0.4)), marker = "o")
    assert(trimmedLines(rendered(canvas, 0, 0)).forall(_.isEmpty))
