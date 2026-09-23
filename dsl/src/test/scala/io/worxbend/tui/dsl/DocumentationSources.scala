package io.worxbend.tui.dsl

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*
import scala.util.Using

/** Locates the prose this repository publishes, for the suites that assert things about it.
  *
  * Published documentation is not compiled, so the only thing that keeps it honest is a test that reads it. Two suites
  * do that — [[DocumentedKeySpecSpec]] checks that every key spec in the prose parses, [[DocumentedFactorySpec]] checks
  * that every public factory is mentioned somewhere — and both need the same two answers: where is the checkout, and
  * which files count as documentation. They live here once rather than as two copies that can drift apart.
  */
private[dsl] object DocumentationSources:

  /** The test's working directory is not guaranteed, so find the checkout by walking up to the build file. `None` means
    * the suite is not running from a checkout at all, which is a reason to cancel rather than to fail.
    */
  lazy val repoRoot: Option[Path] =
    Iterator
      .iterate(Paths.get("").toAbsolutePath)(_.getParent)
      .takeWhile(_ != null) // scalafix:ok DisableSyntax; `Path.getParent` returns null at the filesystem root
      .find(candidate => Files.isRegularFile(candidate.resolve("build.mill")))

  /** Everything a reader of this project sees as documentation: the pages under `website/docs`, which are published to
    * both GitHub Pages and the Wiki, the top-level README, and each module's own README — the prose the JLine drift
    * survived in across audit rounds because no suite read it.
    */
  def markdownSources(root: Path): Seq[Path] =
    val docs    = root.resolve("website/docs")
    val pages   =
      if Files.isDirectory(docs) then
        Using.resource(Files.list(docs))(_.iterator.asScala.filter(_.toString.endsWith(".md")).toVector)
      else Vector.empty
    val modules = Seq("core", "terminal", "widgets", "runtime", "dsl", "macros", "test-support")
    val readmes = modules.map(name => root.resolve(name).resolve("README.md"))
    (Seq(root.resolve("README.md")) ++ pages ++ readmes).filter(Files.isRegularFile(_))
