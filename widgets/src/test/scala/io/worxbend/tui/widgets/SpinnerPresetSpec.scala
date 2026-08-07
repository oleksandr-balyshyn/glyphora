package io.worxbend.tui.widgets

import io.worxbend.tui.core.CharWidth
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.{Duration, DurationInt}

final class SpinnerPresetSpec extends AnyFunSuite:

  test("every built-in preset has frames, a sane speed, and a unique name"):
    assert(SpinnerPreset.All.nonEmpty)
    SpinnerPreset.All.foreach: preset =>
      assert(preset.frames.nonEmpty, s"${preset.name} has no frames")
      assert(preset.frameDuration > Duration.Zero, s"${preset.name} would divide by zero")
      assert(preset.name.trim == preset.name && preset.name.nonEmpty, s"'${preset.name}' is not a usable name")
    val names = SpinnerPreset.All.map(_.name)
    assert(names.distinct.size == names.size, s"duplicate preset names: ${names.diff(names.distinct).distinct}")

  test("every preset is reachable by name, and an unknown name is None"):
    SpinnerPreset.All.foreach: preset =>
      assert(SpinnerPreset.byName(preset.name).contains(preset), s"${preset.name} is not resolvable")
    assert(SpinnerPreset.byName("no-such-spinner").isEmpty)

  test("the catalogues partition the full set"):
    val grouped =
      SpinnerPreset.AsciiPresets ++ SpinnerPreset.BraillePresets ++ SpinnerPreset.BlockPresets ++
        SpinnerPreset.EmojiPresets
    assert(grouped == SpinnerPreset.All, "All must be exactly the four catalogues, so none is orphaned")

  /** A frame set whose frames differ in width would shove the label left and right every tick. Padding to the widest
    * frame is why this is a type rather than a bare `Seq[String]` — a caller passing raw frames cannot do it.
    */
  test("frames are padded to a common width so a label never jitters"):
    SpinnerPreset.All.foreach: preset =>
      val widths = (0 until preset.frames.size)
        .map(index => CharWidth.of(preset.frameAt(preset.frameDuration * index.toLong)))
        .distinct
      assert(widths == Seq(preset.width), s"${preset.name} renders frames of widths $widths")

  test("the ascii presets stay inside ascii, so they are safe on any terminal"):
    SpinnerPreset.AsciiPresets.foreach: preset =>
      preset.frames.foreach: frame =>
        assert(frame.forall(_ < 128), s"${preset.name} frame '$frame' is not ascii")

  /** Emoji frames take two columns. Callers laying out a fixed-width label column need that to be true of the whole
    * catalogue, not most of it.
    */
  test("every emoji preset is two columns wide"):
    SpinnerPreset.EmojiPresets.foreach: preset =>
      assert(preset.width == 2, s"${preset.name} is ${preset.width} columns, not 2")

  /** Speed is wall-clock, so the same preset looks the same in an app ticking at 50ms and one ticking at 200ms — the
    * reason `frameDuration` replaced a tick count.
    */
  test("each frame holds for its frame duration"):
    val slow = SpinnerPreset("slow", Vector("a", "b"), frameDuration = 100.millis)
    assert(
      Seq(0, 40, 99, 100, 150, 199, 200).map(ms => slow.frameAt(ms.millis)) ==
        Seq("a", "a", "a", "b", "b", "b", "a")
    )
    assert(slow.cycleDuration == 200.millis)

  test("frames cycle forwards and wrap, including at a negative elapsed time"):
    val preset = SpinnerPreset("abc", Vector("a", "b", "c"), frameDuration = 100.millis)
    assert(Seq(0, 100, 200, 300).map(ms => preset.frameAt(ms.millis)) == Seq("a", "b", "c", "a"))
    assert(preset.frameAt((-100).millis) == "c", "a clock that ran backwards must not throw")

  test("reversed, slowedBy and atFps derive new presets without touching the original"):
    val base     = SpinnerPreset("abc", Vector("a", "b", "c"), frameDuration = 100.millis)
    val reversed = base.reversed
    assert(Seq(0, 100, 200).map(ms => reversed.frameAt(ms.millis)) == Seq("c", "b", "a"))
    assert(base.frames == Vector("a", "b", "c"), "the original must be untouched")
    assert(base.slowedBy(2).frameDuration == 200.millis)
    assert(base.slowedBy(2).slowedBy(3).frameDuration == 600.millis, "slowing compounds")
    assert(base.atFps(10).frameDuration == 100.millis)
    assert(base.atFps(4).frameDuration == 250.millis)

  /** These are static declarations, so a malformed one is a programmer error: failing at construction beats a spinner
    * that silently renders nothing or divides by zero mid-frame.
    */
  test("a malformed preset is rejected at construction"):
    assertThrows[IllegalArgumentException](SpinnerPreset("empty", Vector.empty))
    assertThrows[IllegalArgumentException](SpinnerPreset("frozen", Vector("a"), frameDuration = Duration.Zero))
    assertThrows[IllegalArgumentException](SpinnerPreset("a", Vector("a")).slowedBy(0))
    assertThrows[IllegalArgumentException](SpinnerPreset("a", Vector("a")).atFps(0))

  test("the spinner renders the preset's frame followed by the label"):
    assert(trimmedLines(rendered(Spinner(0.millis, "loading"), 12, 1)) == Seq("⠋ loading"))
    assert(trimmedLines(rendered(Spinner(SpinnerPreset.Dots.frameDuration, "loading"), 12, 1)) == Seq("⠙ loading"))
    assert(trimmedLines(rendered(Spinner(0.millis, "x", SpinnerPreset.Line), 8, 1)) == Seq("| x"))
    val twoFrames = SpinnerPreset.Line.frameDuration * 2L
    assert(trimmedLines(rendered(Spinner(twoFrames, "x", SpinnerPreset.Line), 8, 1)) == Seq("- x"))

  /** The label is placed after the *padded* frame, so a preset with ragged frames still lines up. */
  test("a ragged preset still places the label at a fixed column"):
    val ragged = SpinnerPreset("ragged", Vector("...", "."), frameDuration = 100.millis)
    val wide   = trimmedLines(rendered(Spinner(0.millis, "go", ragged), 12, 1)).head
    val narrow = trimmedLines(rendered(Spinner(100.millis, "go", ragged), 12, 1)).head
    assert(wide == "... go")
    assert(narrow.endsWith("go") && narrow.indexOf("go") == wide.indexOf("go"))

  test("a spinner never spills past its area"):
    assert(trimmedLines(rendered(Spinner(0.millis, "loading the world"), 6, 1)).head.length <= 6)
    assert(trimmedLines(rendered(Spinner(0.millis, "x", SpinnerPreset.Moon), 1, 1)).head.length <= 1)

  test("preferredWidth accounts for the frame, the gap and the label"):
    assert(Spinner(0.millis, "", SpinnerPreset.Line).preferredWidth == 1)
    assert(Spinner(0.millis, "abc", SpinnerPreset.Line).preferredWidth == 5)
    assert(Spinner(0.millis, "abc", SpinnerPreset.Moon).preferredWidth == 6, "emoji frames are two columns")
