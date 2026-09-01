package io.worxbend.tui.testsupport

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using

/** Runs `body` against a throwaway directory named `prefix`, deleting the whole tree afterwards, deepest entry first,
  * whether or not `body` succeeded. Test-only: nothing here is published.
  */
private[testsupport] object TempWorkspace:
  def withWorkspace(prefix: String)(body: Path => Unit): Unit =
    val workspace = Files.createTempDirectory(prefix)
    try body(workspace)
    finally
      val entries = Using.resource(Files.walk(workspace))(_.iterator.asScala.toVector)
      entries.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
