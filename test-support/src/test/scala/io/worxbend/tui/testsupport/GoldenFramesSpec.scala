package io.worxbend.tui.testsupport

import io.worxbend.tui.core.{Buffer, Rect, Style}

import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.{Files, Path}

/** Round-trips the real write path's bytes through a temp directory and back into the real compare path, pinning that a
  * fixture always matches the frame it was generated from — including frames whose bottom rows are never painted.
  */
final class GoldenFramesSpec extends AnyFunSuite:

  test("a frame whose last row is blank matches a fixture generated from it"):
    val source = frame(12, 3, "alpha", "beta")
    withFixture("blank-bottom") { directory =>
      GoldenFrames.writeFixture(directory, "blank-bottom", source)
      val expected = Files.readString(GoldenFrames.fixtureFile(directory, "blank-bottom"))
      assert(expected.endsWith("\n"), "the unpainted bottom row must trim to \"\", or this scenario is not the bug")
      GoldenFrames.assertMatchesText("blank-bottom", source, expected)
    }

  test("a frame with several trailing blank rows matches a fixture generated from it"):
    val source = frame(12, 6, "alpha", "beta")
    withFixture("many-blank") { directory =>
      GoldenFrames.writeFixture(directory, "many-blank", source)
      val expected = Files.readString(GoldenFrames.fixtureFile(directory, "many-blank"))
      assert(expected.endsWith("alpha\nbeta\n\n\n\n"))
      GoldenFrames.assertMatchesText("many-blank", source, expected)
    }

  test("a genuinely different frame still fails, with both sides of the message normalised alike"):
    val source = frame(12, 3, "alpha", "beta")
    withFixture("differs") { directory =>
      GoldenFrames.writeFixture(directory, "differs", source)
      val expected = Files.readString(GoldenFrames.fixtureFile(directory, "differs"))
      val error    =
        intercept[AssertionError](GoldenFrames.assertMatchesText("differs", frame(12, 3, "alpha", "gamma"), expected))
      assert(error.getMessage.startsWith("frame differs from golden/differs.txt"))
      assert(error.getMessage.contains("--- expected ---\nalpha\nbeta\n--- actual ---"))
      assert(error.getMessage.endsWith("--- actual ---\nalpha\ngamma"))
    }

  test("a frame with a painted bottom row is written verbatim, so recorded fixtures need no regeneration"):
    val source = frame(12, 2, "alpha", "beta")
    withFixture("painted-bottom") { directory =>
      GoldenFrames.writeFixture(directory, "painted-bottom", source)
      val written = Files.readString(GoldenFrames.fixtureFile(directory, "painted-bottom"))
      assert(written == BufferAssertions.text(source))
      assert(!written.endsWith("\n"))
      GoldenFrames.assertMatchesText("painted-bottom", source, written)
    }

  test("interior blank rows stay significant"):
    val source = frame(12, 4, "alpha", "", "gamma")
    withFixture("interior-blank") { directory =>
      GoldenFrames.writeFixture(directory, "interior-blank", source)
      val expected = Files.readString(GoldenFrames.fixtureFile(directory, "interior-blank"))
      GoldenFrames.assertMatchesText("interior-blank", source, expected)
      intercept[AssertionError](
        GoldenFrames.assertMatchesText("interior-blank", frame(12, 4, "alpha", "gamma"), expected)
      )
    }

  test("a fixture an editor has given trailing newlines still matches"):
    val source = frame(12, 2, "alpha", "beta")
    GoldenFrames.assertMatchesText("hand-edited", source, BufferAssertions.text(source) + "\n")
    GoldenFrames.assertMatchesText("hand-edited", source, BufferAssertions.text(source) + "\n\n")

  private def frame(width: Int, height: Int, rows: String*): Buffer =
    val buffer = Buffer(Rect(0, 0, width, height))
    rows.zipWithIndex.foreach((row, y) => buffer.setString(0, y, row, Style.Default))
    buffer

  private def withFixture(name: String)(body: Path => Unit): Unit =
    val directory = Files.createTempDirectory("glyphora-golden")
    try body(directory)
    finally
      Files.deleteIfExists(GoldenFrames.fixtureFile(directory, name))
      Files.deleteIfExists(directory.resolve("golden"))
      Files.deleteIfExists(directory)
