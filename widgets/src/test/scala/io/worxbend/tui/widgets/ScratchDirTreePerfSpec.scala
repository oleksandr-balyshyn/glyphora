package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect}
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.{Files, Path}

final class ScratchDirTreePerfSpec extends AnyFunSuite:

  private def makeTree(n: Int): Path =
    val root = Files.createTempDirectory("scratch-dt")
    val sub  = Files.createDirectory(root.resolve("big"))
    (0 until n).foreach(i => Files.createFile(sub.resolve(s"file-$i.txt")))
    root

  private def measure(n: Int, frames: Int): (Long, Long) =
    val root  = makeTree(n)
    val state = new DirectoryTreeState(root)
    state.expanded += root.resolve("big")
    state.selected = Some(root.resolve("big").resolve(s"file-${n - 1}.txt"))
    val widget = DirectoryTree()
    val buffer = Buffer.empty(Rect(0, 0, 40, 20))
    val area   = Rect(0, 0, 40, 20)
    // warm cache + JIT
    (0 until 20).foreach(_ => widget.render(area, buffer, state))
    val before = java.lang.management.ManagementFactory
      .getMemoryMXBean.getHeapMemoryUsage.getUsed
    val t0 = System.nanoTime()
    (0 until frames).foreach(_ => widget.render(area, buffer, state))
    val ms = (System.nanoTime() - t0) / 1000000
    val alloc = java.lang.management.ManagementFactory.getMemoryMXBean.getHeapMemoryUsage.getUsed - before
    (ms, alloc)

  test("growth with entry count") {
    val frames = 200
    for n <- Seq(1000, 10000, 40000) do
      val (ms, alloc) = measure(n, frames)
      info(s"n=$n frames=$frames renderTime=${ms}ms heapDelta=${alloc / 1048576}MB (viewport 20 rows)")
  }
