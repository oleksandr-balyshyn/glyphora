package io.worxbend.tui.testsupport

import io.worxbend.tui.core.Buffer

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.io.Source
import scala.util.Using

/** Full-frame snapshot assertions: a rendered buffer is compared against a text fixture in the module's test resources
  * (`/golden/<name>.txt`).
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

  /** Compares `buffer` against the `golden/<name>.txt` fixture on the test classpath, or — when
    * `GLYPHORA_GOLDEN_UPDATE` names a test-resources directory — writes the frame there instead of comparing.
    */
  def assertMatches(name: String, buffer: Buffer): Unit =
    sys.env.get(UpdateEnvVar) match
      case Some(directory) => writeFixture(Path.of(directory), name, buffer)
      case None            =>
        val stream = getClass.getResourceAsStream(s"/golden/$name.txt")
        if stream == null then // scalafix:ok DisableSyntax; getResourceAsStream returns null when the fixture is absent
          throw AssertionError(
            s"missing golden fixture golden/$name.txt — run once with GLYPHORA_GOLDEN_UPDATE=<test-resources-dir>"
          )
        val expected = Using.resource(Source.fromInputStream(stream, "UTF-8"))(_.mkString)
        assertMatchesText(name, buffer, expected)

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
      throw AssertionError(
        s"frame differs from golden/$name.txt\n--- expected ---\n$recorded\n--- actual ---\n$actual"
      )

  private def normalise(text: String): String = text.replaceAll("\\R+$", "")
