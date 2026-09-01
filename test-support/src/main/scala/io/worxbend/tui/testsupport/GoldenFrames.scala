package io.worxbend.tui.testsupport

import io.worxbend.tui.core.Buffer

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.regex.Pattern
import scala.io.Source
import scala.util.Using

/** Full-frame snapshot assertions: a rendered buffer is compared against a text fixture in the module's test resources
  * (`/golden/<name>.txt`).
  *
  * **What a fixture records is layout and glyphs, and nothing else.** The frame is serialised through
  * [[BufferAssertions.text]], which concatenates each cell's symbol, so colours, modifiers and hyperlinks are dropped:
  * a frame that lost every style still matches its fixture byte for byte. Use a golden fixture to pin *where things
  * landed*, and assert styling separately against the buffer's cells. Widening the fixture format to carry a style
  * plane was considered and rejected — it would make every fixture unreadable in review, which is the one thing a text
  * snapshot is for.
  *
  * To (re)generate fixtures, run the tests with `GLYPHORA_GOLDEN_UPDATE=<resources-dir>` — each assertion then writes
  * its actual frame there instead of comparing, and the diff shows up in review like any code change.
  *
  * Comparison ignores trailing line terminators on both sides, so trailing blank rows are not significant: a fixture
  * generated under `GLYPHORA_GOLDEN_UPDATE` always matches the frame it was generated from, and a frame that stops
  * short of the fixture's bottom blank rows still matches. Interior blank rows stay significant.
  */
object GoldenFrames:

  private val UpdateEnvVar = "GLYPHORA_GOLDEN_UPDATE"

  /** Line terminators at the end of a frame, compiled once rather than per comparison. */
  private val TrailingNewlines: Pattern = Pattern.compile("\\R+$")

  /** Compares `buffer` against the `golden/<name>.txt` fixture on the test classpath, or — when
    * `GLYPHORA_GOLDEN_UPDATE` names a test-resources directory — writes the frame there instead of comparing.
    */
  def assertMatches(name: String, buffer: Buffer): Unit =
    sys.env.get(UpdateEnvVar) match
      case Some(directory) =>
        // Recording asserts nothing. Say so on stderr, so a run that only rewrote fixtures (a CI job that leaked the
        // environment variable, say) is distinguishable from a run whose golden tests actually compared frames.
        System.err.println(s"golden: recorded $name (no comparison — $UpdateEnvVar is set)")
        writeFixture(Path.of(directory), name, buffer)
      case None            =>
        val stream = getClass.getResourceAsStream(s"/golden/$name.txt")
        if stream == null then // scalafix:ok DisableSyntax; getResourceAsStream returns null when the fixture is absent
          throw CallSite.attribute(
            AssertionError(
              s"missing golden fixture golden/$name.txt — run once with GLYPHORA_GOLDEN_UPDATE=<test-resources-dir>"
            )
          )
        val expected = Using.resource(Source.fromInputStream(stream, "UTF-8"))(_.mkString)
        assertMatchesText(name, buffer, expected)

  /** Snapshots the app's last rendered frame — the whole-app counterpart of the widget-level overload.
    *
    * Exactly `assertMatches(name, pilot.lastFrame)`, written once here instead of in every app-level suite. `lastFrame`
    * fails the test when nothing has been drawn yet, so a snapshot of an app that never painted cannot quietly match an
    * empty fixture.
    */
  def assertMatches(name: String, pilot: Pilot): Unit =
    assertMatches(name, pilot.lastFrame)

  /** The on-disk location of `name`'s fixture under a test-resources `directory`. */
  private[testsupport] def fixtureFile(directory: Path, name: String): Path =
    directory.resolve("golden").resolve(s"$name.txt")

  /** Writes `buffer`'s frame text to `name`'s fixture under `directory`, creating parent directories. The bytes are the
    * frame text verbatim — no terminator is appended, so previously recorded fixtures stay byte-identical.
    */
  private[testsupport] def writeFixture(directory: Path, name: String, buffer: Buffer): Unit =
    val target = fixtureFile(directory, name)
    Files.createDirectories(target.getParent)
    Files.write(target, BufferAssertions.text(buffer).getBytes(StandardCharsets.UTF_8))

  /** Compares `buffer` against already-loaded fixture text. Trailing line terminators are stripped from both sides, so
    * the comparison — and the failure message — see the same normalisation.
    */
  private[testsupport] def assertMatchesText(name: String, buffer: Buffer, expected: String): Unit =
    val actual   = normalise(BufferAssertions.text(buffer))
    val recorded = normalise(expected)
    if actual != recorded then
      throw CallSite.attribute(
        AssertionError(s"frame differs from golden/$name.txt\n--- expected ---\n$recorded\n--- actual ---\n$actual")
      )

  /** Drops trailing line terminators, which is what makes trailing blank rows insignificant while interior blank rows
    * still count. This is the frame-level half of the normalisation; the row-level half — stripping trailing spaces
    * from each row — has already happened in `BufferAssertions.trimmedLines`, which is why a frame that ends in blank
    * rows arrives here as text ending in bare newlines.
    */
  private def normalise(text: String): String = TrailingNewlines.matcher(text).replaceAll("")
