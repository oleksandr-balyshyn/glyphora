package io.worxbend.tui.widgets

import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.{Files, Path}

class ScratchDirTreeSpec extends AnyFunSuite:

  private def makeDir(n: Int): Path =
    val d = Files.createTempDirectory(s"glyphora-scratch-$n-")
    var i = 0
    while i < n do
      Files.createFile(d.resolve(f"entry$i%08d"))
      i += 1
    d

  private def measure(n: Int): Unit =
    val d       = makeDir(n)
    val state   = new DirectoryTreeState(d)
    val rt      = Runtime.getRuntime
    System.gc()
    val before  = rt.totalMemory - rt.freeMemory
    val t0      = System.nanoTime()
    val visible = state.visiblePaths()
    val ms      = (System.nanoTime() - t0) / 1000000.0
    System.gc()
    val after   = rt.totalMemory - rt.freeMemory
    info(f"n=$n%d visible=${visible.size}%d firstListMs=$ms%.1f retainedKB=${(after - before) / 1024}%d")

    // second call: cached?
    val t1 = System.nanoTime()
    state.visiblePaths()
    info(f"n=$n%d cachedWalkMs=${(System.nanoTime() - t1) / 1000000.0}%.1f")

    // is there any cap / truncation marker?
    assert(visible.size == n, s"no cap: got ${visible.size} of $n")

    d.toFile.listFiles().foreach(_.delete())
    Files.delete(d)

  test("scale 20k")(measure(20000))
  test("scale 200k")(measure(200000))
