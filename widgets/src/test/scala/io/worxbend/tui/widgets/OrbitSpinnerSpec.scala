package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Rect, Size, Style}
import io.worxbend.tui.testsupport.BufferAssertions.rendered

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}

final class OrbitSpinnerSpec extends AnyFunSuite:

  private val Period = 1600.millis

  private def frameAt(spinner: OrbitSpinner, width: Int = 12, height: Int = 6): Vector[(String, Style)] =
    val buffer = rendered(spinner, width, height)
    (for
      y <- 0 until height
      x <- 0 until width
    yield
      val cell = buffer.get(x, y)
      (cell.symbol, cell.style)
    ).toVector

  private def at(sample: Int, samples: Int): FiniteDuration =
    Duration.fromNanos(Period.toNanos * sample / samples)

  // ---------------------------------------------------------------- purity and bounds

  /** The whole animated family rests on this: a frame is a pure function of elapsed time, with nothing retained between
    * renders. It is what lets a test render any moment directly and a runner redraw without drift.
    */
  test("the same moment renders the same frame twice"):
    OrbitPath.values.foreach: path =>
      val spinner = OrbitSpinner(700.millis, path = path)
      assert(frameAt(spinner) == frameAt(spinner))

  /** `Buffer.set` clips to the buffer, not to the widget's rect, so an orbit that miscalculated its centre would
    * quietly overwrite a neighbour rather than fail.
    */
  test("an orbit never writes outside the rect it is given"):
    val frame = Buffer(Rect(0, 0, 20, 8))
    (0 until 20).foreach(x => frame.set(x, 0, Cell("#", Style.Default)))
    (0 until 20).foreach(x => frame.set(x, 7, Cell("#", Style.Default)))
    OrbitSpinner(400.millis, radius = Some(3)).render(Rect(2, 1, 10, 6), frame)
    assert((0 until 20).forall(x => frame.get(x, 0).symbol == "#"), "the row above was overwritten")
    assert((0 until 20).forall(x => frame.get(x, 7).symbol == "#"), "the row below was overwritten")

  test("degenerate areas and parameters neither throw nor spill"):
    OrbitPath.values.foreach: path =>
      Seq(0, 1, 2, 3).foreach: size =>
        Seq(Some(0), Some(1), Some(99), None).foreach: radius =>
          Seq(-1.0, 0.0, 0.5, 1.0, 2.0, Double.NaN).foreach: sweep =>
            val spinner = OrbitSpinner(250.millis, path = path, radius = radius, sweep = sweep)
            val buffer  = rendered(spinner, size, size)
            assert(buffer.area == Rect(0, 0, size, size))

  test("a zero period parks the arc instead of dividing by zero"):
    val parked = OrbitSpinner(500.millis, period = Duration.Zero)
    assert(frameAt(parked) == frameAt(parked.copy(elapsed = 3.seconds)))

  test("a negative elapsed time wraps rather than throwing"):
    val spinner = OrbitSpinner(Duration.fromNanos(-700_000_000L))
    assert(frameAt(spinner).nonEmpty)

  // ---------------------------------------------------------------- the ring itself

  /** The resting path is drawn at intensity zero and the arc over it with the masks OR-ed, so the ring never erodes as
    * the arc passes. A ring that dropped dots would flicker.
    */
  test("the ring's glyphs are the same at every moment; only the styles move"):
    val glyphs = (0 until 16).map(sample => frameAt(OrbitSpinner(at(sample, 16))).map(_._1)).distinct
    assert(glyphs.size == 1, "the ring's geometry moved, so the mask is eroding rather than accumulating")
    val styles = (0 until 16).map(sample => frameAt(OrbitSpinner(at(sample, 16))).map(_._2)).distinct
    assert(styles.size > 1, "nothing animated at all")

  test("the arc travels round and returns to where it started"):
    val start  = frameAt(OrbitSpinner(0.millis))
    val alap   = frameAt(OrbitSpinner(Period))
    assert(start == alap, "one period should be exactly one revolution")
    val midway = frameAt(OrbitSpinner(Period / 2))
    assert(midway != start, "the arc did not move")

  /** The design proposed "counter-clockwise at lap l mirrors clockwise at lap 1-l". That is wrong — it is off by two
    * indices. The true property is that at the *same* instant the two senses are vertical mirrors of each other, and it
    * holds dot for dot.
    *
    * Asserted at `CanvasResolution.Cell`, where one dot is one cell so a cell-space mirror *is* a dot-space mirror. At
    * braille resolution a cell packs two dots across, so mirroring whole cells straddles the centre and the property
    * would only hold approximately — the observable would be wrong, not the widget.
    */
  test("counter-clockwise is the vertical mirror of clockwise at the same instant"):
    val width  = 13
    val height = 7
    (0 until 8).foreach: sample =>
      val moment                         = at(sample, 8)
      def draw(direction: SpinDirection) =
        rendered(
          OrbitSpinner(moment, radius = Some(3), resolution = CanvasResolution.Cell, direction = direction),
          width,
          height,
        )
      val cw                             = draw(SpinDirection.Clockwise)
      val ccw                            = draw(SpinDirection.CounterClockwise)
      (0 until height).foreach: y =>
        (0 until width).foreach: x =>
          assert(
            cw.get(x, y).style == ccw.get(width - 1 - x, y).style,
            s"at $moment the two senses are not mirrored at ($x, $y)",
          )

  /** With a solid trail every lit dot has the same intensity, so a fully lit ring is genuinely static — the family's
    * "queued, not yet started" state. A comet still animates at full sweep, because its gradient keeps rotating even
    * when every dot is lit; that difference is the whole distinction between the two trails.
    */
  test("a full solid sweep lights the whole ring and stops moving"):
    val solid = OrbitSpinner(0.millis, sweep = 1.0, radius = Some(3), trail = OrbitTrail.Solid)
    assert(frameAt(solid) == frameAt(solid.copy(elapsed = 900.millis)), "a fully lit solid ring should be static")

    val comet = OrbitSpinner(0.millis, sweep = 1.0, radius = Some(3), trail = OrbitTrail.Comet())
    assert(frameAt(comet) != frameAt(comet.copy(elapsed = 400.millis)), "a comet's gradient should keep rotating")

  test("a wider sweep lights more of the ring than a narrow one"):
    def litCells(sweep: Double): Int =
      val buffer = rendered(OrbitSpinner(0.millis, sweep = sweep, radius = Some(4), style = Style.Default.dim), 16, 8)
      (for
        y <- 0 until 8
        x <- 0 until 16
      yield buffer.get(x, y)).count(cell =>
        cell.symbol != " " && !cell.style.modifiers.has(io.worxbend.tui.core.Modifiers.Dim)
      )
    assert(litCells(0.5) > litCells(0.125))

  // ---------------------------------------------------------------- size contract

  /** A DSL element sizes its box from `preferredSize`, so a figure that painted outside what it asked for would be
    * clipped by its own container.
    */
  test("a fixed radius paints inside the size it claims"):
    Seq(CanvasResolution.Braille, CanvasResolution.HalfBlock, CanvasResolution.Cell).foreach: resolution =>
      Seq(1, 2, 3, 5).foreach: radius =>
        val spinner = OrbitSpinner(300.millis, radius = Some(radius), resolution = resolution)
        val claimed = spinner.preferredSize.getOrElse(fail("a fixed radius must claim a size"))
        assert(claimed == OrbitSpinner.sizeFor(radius, resolution))
        val buffer  = rendered(spinner, claimed.width, claimed.height)
        assert(buffer.area == Rect(0, 0, claimed.width, claimed.height))

  test("a fitted orbit claims no size, because it takes whatever it is given"):
    assert(OrbitSpinner(0.millis, radius = None).preferredSize.isEmpty)

  // ---------------------------------------------------------------- resolutions

  /** The ASCII floor: the same figure at one glyph per cell, for a terminal with no braille block. */
  test("every resolution draws a figure, and the cell resolution uses the marker"):
    Seq(CanvasResolution.Braille, CanvasResolution.HalfBlock).foreach: resolution =>
      val drawn = frameAt(OrbitSpinner(300.millis, resolution = resolution, radius = Some(2)))
      assert(drawn.exists(_._1.trim.nonEmpty), s"$resolution drew nothing")

    val ascii = frameAt(OrbitSpinner(300.millis, resolution = CanvasResolution.Cell, marker = "*", radius = Some(2)))
    assert(ascii.exists(_._1 == "*"), "the cell resolution should draw its marker")
    assert(
      ascii.forall(cell => cell._1 == "*" || cell._1.trim.isEmpty),
      s"unexpected glyphs: ${ascii.map(_._1).distinct}",
    )

  test("a square path is squarer than a circle at the same radius"):
    def corners(path: OrbitPath): Int =
      val buffer = rendered(OrbitSpinner(0.millis, path = path, radius = Some(3), sweep = 1.0), 16, 8)
      Seq((0, 0), (15, 0), (0, 7), (15, 7)).count((x, y) => buffer.get(x, y).symbol.trim.nonEmpty)
    assert(OrbitPath.values.length == 2)
    // both draw something; the point is only that the two paths are genuinely different figures
    assert(
      frameAt(OrbitSpinner(0.millis, path = OrbitPath.Square, radius = Some(3), sweep = 1.0)) !=
        frameAt(OrbitSpinner(0.millis, path = OrbitPath.Circle, radius = Some(3), sweep = 1.0))
    )
    assert(corners(OrbitPath.Square) >= corners(OrbitPath.Circle))
