package io.worxbend.tui.dsl

import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Every element factory `dsl.scala` exports is part of the one import an application is promised, so a factory nobody
  * wrote about is a feature that only exists for whoever already knows it is there. `positioned` spent several releases
  * in exactly that state: the Scaladoc on `tooltip` told readers to "see `positioned`", the toast stack was built on
  * it, and it appeared on none of the published pages.
  *
  * This suite closes that gap for good. It reads the `export Element.{…}` block out of the DSL source — that block is
  * the definition of what is public, so it cannot fall behind the code the way a hand-kept list would — and asserts
  * every name in it is mentioned in the prose that ships to the site and the Wiki.
  *
  * The source is *parsed*, not reflected over: no `java.lang.reflect` anywhere, and this file is a test source, which
  * the CI reflection greps — they scan main sources only — do not look at in any case.
  */
final class DocumentedFactorySpec extends AnyFunSuite:

  private val ExportBlock = """export Element\.\{([^}]*)\}""".r

  /** Names deliberately left out of the prose. Empty on purpose: every exported factory is currently documented, and a
    * new entry here has to carry a comment saying why the name is not worth a reader's time. Weakening the match below
    * instead would hide the next `positioned`.
    */
  private val UndocumentedByDesign: Set[String] = Set.empty

  private def exportedFactories(root: Path): Seq[String] =
    val source = Files.readString(root.resolve("dsl/src/main/scala/io/worxbend/tui/dsl/dsl.scala"))
    ExportBlock
      .findFirstMatchIn(source)
      .map(_.group(1).split(",").iterator.map(_.trim).filter(_.nonEmpty).toVector)
      .getOrElse(Vector.empty)

  /** Matches a factory name written in *code voice*: as a call, `positioned(...)`, or as an inline code span,
    * `` `positioned` ``. A bare word is not enough — `text`, `line`, `list`, `row` and `image` are ordinary English,
    * and a check they satisfied by accident would pass for a name nobody had documented.
    */
  private def mentionOf(name: String): scala.util.matching.Regex =
    s"""(?<![A-Za-z0-9_.])${java.util.regex.Pattern.quote(name)}(?=\\(|`)""".r

  test("the exported factory block is in alphabetical order"):
    val root      = DocumentationSources.repoRoot
      .getOrElse(cancel("not running from a checkout: no build.mill above the working directory"))
    val factories = exportedFactories(root)
    assert(
      factories.sizeIs > 50,
      s"read ${factories.size} names out of the `export Element.{…}` block in dsl.scala — the block moved or was " +
        "renamed, and this suite is no longer checking anything",
    )

    val sorted     = factories.sortBy(_.toLowerCase)
    val firstWrong = factories.zip(sorted).collectFirst { case (actual, expected) if actual != expected => actual }
    assert(
      firstWrong.isEmpty,
      s"`${firstWrong.getOrElse("")}` is out of place in the `export Element.{…}` block in dsl.scala. The block is " +
        "the list a reader scans to find out whether a factory exists, so it is kept in case-insensitive alphabetical " +
        "order — insert your new factory at its alphabetical position rather than at the end.",
    )

  test("every exported element factory is mentioned in the published documentation"):
    val root      = DocumentationSources.repoRoot
      .getOrElse(cancel("not running from a checkout: no build.mill above the working directory"))
    val factories = exportedFactories(root)
    assert(
      factories.sizeIs > 50,
      s"read ${factories.size} names out of the `export Element.{…}` block in dsl.scala — the block moved or was " +
        "renamed, and this suite is no longer checking anything",
    )

    val prose = DocumentationSources
      .markdownSources(root)
      .map(page => Files.readAllLines(page).asScala.mkString("\n"))
      .mkString("\n")
    assert(prose.nonEmpty, "found no documentation pages — website/docs and README.md are both missing or empty")

    val missing = factories.filterNot(UndocumentedByDesign).filterNot(mentionOf(_).findFirstIn(prose).isDefined)
    assert(
      missing.isEmpty,
      "these factories are exported to every application but named nowhere in the published prose:\n" +
        missing.map(name => s"  $name").mkString("\n") +
        "\nDocument each one on the page it belongs to, or add it to UndocumentedByDesign with a reason.",
    )
