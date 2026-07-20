package io.worxbend.tui.runtime

import io.worxbend.tui.core.{Buffer, Color, Rect, Style}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.{Duration, DurationInt}

final class EffectSpec extends AnyFunSuite:

  private def filledBuffer(width: Int = 6, height: Int = 2): Buffer =
    val buffer = Buffer(Rect(0, 0, width, height))
    (0 until height).foreach(y => buffer.setString(0, y, "x" * width, Style.Default.withFg(Color.White)))
    buffer

  private def visibleCells(buffer: Buffer): Int =
    (0 until buffer.area.height)
      .map(y => (0 until buffer.area.width).count(x => !buffer.get(x, y).isBlank))
      .sum

  test("easing curves hit both endpoints; the non-overshoot families are monotone"):
    // Back/Elastic/Bounce intentionally overshoot or bounce, so they are excluded from the monotonicity check.
    val overshoots = Set(
      Easing.BackIn,
      Easing.BackOut,
      Easing.BackInOut,
      Easing.ElasticIn,
      Easing.ElasticOut,
      Easing.ElasticInOut,
      Easing.BounceIn,
      Easing.BounceOut,
      Easing.BounceInOut,
    )
    Easing.values.foreach { easing =>
      assert(math.abs(easing(0.0)) < 1e-9, easing)
      assert(math.abs(easing(1.0) - 1.0) < 1e-9, easing)
      assert(easing(-1.0) == easing(0.0)) // inputs below 0 clamp to the start
      if !overshoots.contains(easing) then
        val samples = (0 to 10).map(i => easing(i / 10.0))
        assert(samples.zip(samples.tail).forall(_ <= _), s"$easing not monotone: $samples")
    }

  test("a tween interpolates between endpoints and reports completion"):
    val tween = Tween(0.0, 10.0, 1.second, Easing.Linear)
    assert(tween.at(Duration.Zero) == 0.0)
    assert(math.abs(tween.at(500.millis) - 5.0) < 1e-9)
    assert(tween.at(2.seconds) == 10.0)
    assert(!tween.isDone(500.millis))
    assert(tween.isDone(1.second))

  test("sweepIn reveals columns monotonically until everything shows"):
    val effect = Effect.sweepIn(1.second, Easing.Linear)
    val half   = filledBuffer()
    effect.process(500.millis, half, half.area)
    assert(visibleCells(half) == 6) // half of 12 cells
    val done = filledBuffer()
    effect.process(1.second, done, done.area)
    assert(visibleCells(done) == 12)
    assert(effect.isDone(1.second))

  test("coalesce reveals more cells as time passes and finishes complete"):
    val effect = Effect.coalesce(1.second, Easing.Linear)
    val counts = Seq(100.millis, 500.millis, 900.millis, 1.second).map { elapsed =>
      val buffer = filledBuffer(10, 4)
      effect.process(elapsed, buffer, buffer.area)
      visibleCells(buffer)
    }
    assert(counts.zip(counts.tail).forall(_ <= _), counts)
    assert(counts.last == 40)

  test("dissolve is coalesce reversed: everything visible at start, nothing at the end"):
    val effect = Effect.dissolve(1.second, Easing.Linear)
    val start  = filledBuffer(10, 4)
    effect.process(Duration.Zero, start, start.area)
    assert(visibleCells(start) == 40)
    val end    = filledBuffer(10, 4)
    effect.process(1.second, end, end.area)
    assert(visibleCells(end) == 0)

  test("fadeIn scales foreground colors up from black"):
    val effect = Effect.fadeIn(1.second, Easing.Linear)
    val dark   = filledBuffer()
    effect.process(Duration.Zero, dark, dark.area)
    assert(dark.get(0, 0).style.fg.contains(Color.Rgb(0, 0, 0)))
    val bright = filledBuffer()
    effect.process(1.second, bright, bright.area)
    assert(bright.get(0, 0).style.fg.contains(Color.White)) // completed fade leaves the original colors intact

  test("typewriter reveals cells in reading order"):
    val effect = Effect.typewriter(1.second)
    val buffer = filledBuffer(6, 2)
    effect.process(500.millis, buffer, buffer.area)
    assert((0 until 6).forall(x => !buffer.get(x, 0).isBlank)) // first row complete
    assert((0 until 6).forall(x => buffer.get(x, 1).isBlank)) // second row still hidden

  test("sequence runs children back to back with shifted elapsed time"):
    val effect = Effect.sequence(Effect.dissolve(1.second, Easing.Linear), Effect.sweepIn(1.second, Easing.Linear))
    assert(effect.duration == 2.seconds)
    val during = filledBuffer()
    effect.process(1500.millis, during, during.area) // 500ms into sweepIn
    assert(visibleCells(during) == 6)
    assert(effect.isDone(2.seconds))

  test("delay holds the effect at progress zero, then plays it"):
    val effect  = Effect.delay(1.second, Effect.sweepIn(1.second, Easing.Linear))
    val held    = filledBuffer()
    effect.process(500.millis, held, held.area)
    assert(visibleCells(held) == 0)
    val playing = filledBuffer()
    effect.process(1500.millis, playing, playing.area)
    assert(visibleCells(playing) == 6)

  test("pulse never finishes"):
    val effect = Effect.pulse(100.millis)
    assert(!effect.isDone(1.hour))

  test("indexed colors approximate into the rgb cube"):
    assert(Effect.approximateRgb(Color.Indexed(196)) == (255, 0, 0))
    assert(Effect.approximateRgb(Color.Indexed(232)) == (8, 8, 8))
    assert(Effect.approximateRgb(Color.Rgb(1, 2, 3)) == (1, 2, 3))
