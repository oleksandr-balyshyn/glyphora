package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Color, Rect, Style}
import io.worxbend.tui.testsupport.BufferAssertions.rendered

import org.scalatest.funsuite.AnyFunSuite

/** The generated coastline table, and the shape that paints it. */
final class WorldMapSpec extends AnyFunSuite:

  private def globe(shape: Shape, width: Int, height: Int): Buffer =
    rendered(Canvas((-180.0, 180.0), (-90.0, 90.0), Seq(shape), resolution = CanvasResolution.Braille), width, height)

  private def painted(buffer: Buffer, area: Rect): Int =
    val cells = for
      row    <- area.y until area.bottom
      column <- area.x until area.right
      if buffer.get(column, row).symbol.trim.nonEmpty
    yield 1
    cells.sum

  test("both outlines are whole coordinate pairs inside the world's bounds"):
    Seq(MapResolution.Low, MapResolution.High).foreach { resolution =>
      val points = resolution.points
      assert(points.length % 2 == 0, resolution.toString)
      assert(points.length > 2000, resolution.toString)
      points.indices.foreach { index =>
        val value = points(index)
        val limit = if index % 2 == 0 then 180.0 else 90.0
        assert(value >= -limit && value <= limit, s"$resolution point $index was $value")
      }
    }

  test("the high-detail outline carries more points than the low-detail one"):
    assert(MapResolution.High.points.length > MapResolution.Low.points.length)

  test("every decoded coordinate lands on the quantization grid it was packed onto"):
    // Decoding with the wrong divisor or the wrong bias would still produce plausible-looking numbers in range; what it
    // could not do is leave every one of them on a step of the 15-bit grid the generator quantized to.
    val lonStep = 360.0 / 32767
    val latStep = 180.0 / 32767
    val points  = MapResolution.Low.points
    points.indices.foreach { index =>
      val step    = if index % 2 == 0 then lonStep else latStep
      val origin  = if index % 2 == 0 then -180.0 else -90.0
      val steps   = (points(index) - origin) / step
      val offGrid = math.abs(steps - math.round(steps))
      assert(offGrid < 1e-6, s"point $index (${points(index)}) is $offGrid steps off the grid")
    }

  test("the map is not mirrored: land sits where land is and open ocean stays blank"):
    val buffer  = globe(Shape.WorldMap(MapResolution.High), 80, 24)
    // central Europe, around 10E 50N: x fraction 0.53 of 80 columns, y fraction 0.22 from the top
    val europe  = painted(buffer, Rect(40, 4, 6, 3))
    // the mid-Pacific around 150W on the equator has no coastline within thousands of kilometres
    val pacific = painted(buffer, Rect(6, 11, 6, 3))
    assert(europe > 0, "expected coastline over central Europe")
    assert(pacific == 0, "expected open ocean in the mid-Pacific")

  test("a style reaches the painted cells"):
    val buffer = globe(Shape.WorldMap(MapResolution.Low, Style.fg(Color.Green)), 40, 12)
    val styled = for
      row    <- 0 until 12
      column <- 0 until 40
      if buffer.get(column, row).symbol.trim.nonEmpty
    yield buffer.get(column, row).style.fg
    assert(styled.nonEmpty)
    assert(styled.forall(_.contains(Color.Green)))

  test("narrowed bounds clip to the window rather than squeezing the whole world into it"):
    val europe = Canvas((-11.0, 32.0), (35.0, 72.0), Seq(Shape.WorldMap(MapResolution.High)))
    val world  = Canvas((-180.0, 180.0), (-90.0, 90.0), Seq(Shape.WorldMap(MapResolution.High)))
    val area   = Rect(0, 0, 40, 12)
    assert(painted(rendered(europe, 40, 12), area) < painted(rendered(world, 40, 12), area))

  test("degenerate areas draw nothing and do not throw"):
    Seq((0, 4), (4, 0), (1, 1)).foreach { (width, height) =>
      val buffer = globe(Shape.WorldMap(), width, height)
      assert(buffer.area.width == width && buffer.area.height == height)
    }
