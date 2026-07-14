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
  */
object GoldenFrames:

  def assertMatches(name: String, buffer: Buffer): Unit =
    val actual = BufferAssertions.trimmedLines(buffer).mkString("\n")
    sys.env.get("GLYPHORA_GOLDEN_UPDATE") match
      case Some(directory) =>
        val target = Path.of(directory, "golden", s"$name.txt")
        Files.createDirectories(target.getParent)
        Files.write(target, actual.getBytes(StandardCharsets.UTF_8))
      case None            =>
        val stream   = getClass.getResourceAsStream(s"/golden/$name.txt")
        if stream == null then
          throw AssertionError(
            s"missing golden fixture golden/$name.txt — run once with GLYPHORA_GOLDEN_UPDATE=<test-resources-dir>"
          )
        val expected = Using.resource(Source.fromInputStream(stream, "UTF-8"))(_.mkString)
        if actual != expected.stripLineEnd then
          throw AssertionError(
            s"frame differs from golden/$name.txt\n--- expected ---\n$expected\n--- actual ---\n$actual"
          )
