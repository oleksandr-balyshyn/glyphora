package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect}
import org.scalatest.funsuite.AnyFunSuite

class ScratchMdPerfSpec extends AnyFunSuite:

  private def doc(lines: Int): String =
    (1 to lines).map(i => s"# Head $i\n\nsome **bold** and *em* and `code` text [l](http://x) line $i\n").mkString

  private def timeFrames(src: String, frames: Int): Long =
    val md   = Markdown(src)
    val area = Rect(0, 0, 80, 50)
    val buf  = Buffer(area)
    val t0   = System.nanoTime()
    var i    = 0
    while i < frames do
      md.heightAt(80)
      md.render(area, buf)
      i += 1
    System.nanoTime() - t0

  test("scaling") {
    val small = doc(100)
    val big   = doc(1000)
    timeFrames(small, 20) // warm
    val ts = timeFrames(small, 60)
    val tb = timeFrames(big, 60)
    info(s"small(100 blocks) 60 frames: ${ts / 1000000}ms; big(1000 blocks): ${tb / 1000000}ms; ratio ${tb.toDouble / ts}")
    info(s"per-frame big: ${tb / 60 / 1000000.0}ms")
  }
