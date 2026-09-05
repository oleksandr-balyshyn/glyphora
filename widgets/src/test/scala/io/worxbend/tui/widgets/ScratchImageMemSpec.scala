package io.worxbend.tui.widgets

import org.scalatest.funsuite.AnyFunSuite

final class ScratchImageMemSpec extends AnyFunSuite:

  private def writePng(w: Int, h: Int): java.nio.file.Path =
    val img  = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
    val path = java.nio.file.Files.createTempFile(s"scratch-$w-$h-", ".png")
    javax.imageio.ImageIO.write(img, "png", path.toFile)
    path

  private def usedHeap(): Long =
    System.gc()
    Thread.sleep(200)
    System.gc()
    val rt = Runtime.getRuntime
    rt.totalMemory - rt.freeMemory

  test("memory growth"):
    for (w, h) <- Seq((1000, 1000), (2000, 2000), (4000, 3000)) do
      val path  = writePng(w, h)
      val start = usedHeap()
      val t0    = System.nanoTime()
      val image = Image.fromFile(path).toOption.get
      val ms    = (System.nanoTime() - t0) / 1000000
      val after = usedHeap()
      val px    = w.toLong * h
      info(
        f"$w x $h = $px px: retained ${(after - start) / 1048576}%d MB, ${(after - start).toDouble / px}%.1f B/px, $ms ms",
      )
      assert(image.pixels.size == h)
      java.nio.file.Files.deleteIfExists(path)
