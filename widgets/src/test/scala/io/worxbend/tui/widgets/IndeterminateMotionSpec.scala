package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.{DurationInt, DurationLong, FiniteDuration}

final class IndeterminateMotionSpec extends AnyFunSuite:

  /** The default traverse time; sampling across exactly one of these covers every position a motion reaches. */
  private val Period = 1600.millis

  private def row(bar: IndeterminateBar, width: Int = 12): String =
    (0 until width).map(x => rendered(bar, width, 1).get(x, 0).symbol).mkString

  /** `samples` evenly spaced moments across one full period. */
  private def at(sample: Int, samples: Int): FiniteDuration = (Period.toNanos * sample / samples).nanos

  private def frames(motion: IndeterminateMotion, samples: Int, width: Int = 12): Seq[String] =
    (0 until samples).map(sample => row(IndeterminateBar(at(sample, samples), motion = motion), width))

  test("the default motion is unchanged from before"):
    assert(row(IndeterminateBar(0.millis)) == "━━━─────────")
    assert(trimmedLines(rendered(IndeterminateBar(elapsed = 0.millis), 12, 1)).head.startsWith("━"))

  /** Whatever the motion, the bar owns exactly its row: no glyph outside the vocabulary, no short row. */
  test("every motion fills its row with its own glyphs"):
    IndeterminateMotion.values.foreach: motion =>
      (0 until 40).foreach: sample =>
        val moment = at(sample, 40)
        val drawn  = row(IndeterminateBar(moment, motion = motion, preset = ProgressPreset.Ascii))
        assert(drawn.length == 12, s"$motion at $moment drew ${drawn.length} cells")
        assert(drawn.forall(c => c == '#' || c == '-'), s"$motion at $moment drew '$drawn'")

  test("every motion animates rather than sitting still"):
    IndeterminateMotion.values.foreach: motion =>
      val distinct = frames(motion, 40).distinct
      assert(distinct.size > 1, s"$motion never changes")

  /** Bounce turns around at the edges, so its cycle revisits earlier frames; sweep does not turn, so a segment that
    * left the right edge reappears at the left.
    */
  test("bounce reverses at the edges and sweep wraps around"):
    val bounced = frames(IndeterminateMotion.Bounce, 40)
    assert(bounced.contains(bounced.head), "bounce must return to where it started")
    assert(bounced.exists(_.startsWith("━")), "bounce must reach the left edge")
    assert(bounced.exists(_.endsWith("━")), "bounce must reach the right edge")

    val swept = frames(IndeterminateMotion.Sweep, 40)
    assert(swept.exists(_.startsWith("━")), "sweep must reach the left edge")
    assert(swept.exists(_.endsWith("━")), "sweep must reach the right edge")
    assert(swept.exists(f => !f.contains("━")), "sweep must fully leave the row between passes")

  /** A comet is a head plus a fading tail, so with a sub-cell vocabulary it shows more than two distinct glyphs in a
    * single frame — that gradient is the whole difference from a plain sweep.
    */
  test("a comet draws a graded tail when the style has partials"):
    val withPartials = (0 until 30)
      .map(sample =>
        row(IndeterminateBar(at(sample, 30), motion = IndeterminateMotion.Comet, preset = ProgressPreset.Dots))
      )
      .map(_.distinct.length)
      .max
    assert(withPartials > 2, "a comet over a sub-cell style should grade its tail")

  test("a comet over a whole-cell style still renders, without a gradient"):
    val drawn =
      row(IndeterminateBar(at(4, 30), motion = IndeterminateMotion.Comet, preset = ProgressPreset.Ascii))
    assert(drawn.length == 12)
    assert(drawn.contains('#'))

  /** Pulse deliberately does not move along the row — that is what makes it quiet enough for a dense dashboard. Every
    * frame is therefore uniform, and the animation is the alternation between them.
    */
  test("pulse brightens the whole row in place rather than travelling"):
    val drawn = frames(IndeterminateMotion.Pulse, 16)
    drawn.foreach(frame => assert(frame.distinct.length == 1, s"pulse drew a moving segment: '$frame'"))
    assert(drawn.distinct.size == 2, "pulse alternates between lit and unlit")

  test("degenerate areas neither throw nor spill"):
    IndeterminateMotion.values.foreach: motion =>
      (0 until 8).foreach: sample =>
        Seq(1, 2, 3).foreach: width =>
          val drawn = row(IndeterminateBar(at(sample, 8), motion = motion), width)
          assert(drawn.length == width, s"$motion at width $width drew ${drawn.length}")

  /** Sampled across a whole cycle rather than at one phase: a sweeping segment is partly off-screen while it enters and
    * leaves, so only its widest moment shows the pinned width.
    */
  test("a segment width can be pinned so several bars animate in step"):
    val widest    = (0 until 40)
      .map(sample => row(IndeterminateBar(at(sample, 40), motion = IndeterminateMotion.Sweep, segmentWidth = Some(5))))
      .map(_.count(_ == '━'))
      .max
    assert(widest == 5)
    val oversized = row(IndeterminateBar(0.millis, motion = IndeterminateMotion.Sweep, segmentWidth = Some(99)))
    assert(oversized.length == 12, "a segment wider than the bar must clamp, not overflow")

  test("a skeleton band can be pinned and uses the glyphs it is given"):
    val drawn = (0 until 6).map(x => rendered(Skeleton(0.millis, bandWidth = Some(3)), 10, 1).get(x, 0).symbol).mkString
    assert(drawn.forall(c => c == '░' || c == '▒'))
    val ascii = (0 until 6)
      .map: x =>
        rendered(Skeleton(300.millis, baseSymbol = ".", bandSymbol = "#", bandWidth = Some(2)), 10, 1).get(x, 0).symbol
      .mkString
    assert(ascii.forall(c => c == '.' || c == '#'), s"skeleton drew '$ascii'")
