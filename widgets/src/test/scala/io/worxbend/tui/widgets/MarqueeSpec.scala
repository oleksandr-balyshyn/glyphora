package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.DurationInt

final class MarqueeSpec extends AnyFunSuite:

  test("a marquee rotates its content through the area"):
    val at0     = trimmedLines(rendered(Marquee("news", 0.millis), 6, 1)).head
    assert(at0.startsWith("news"))
    val at1     = trimmedLines(rendered(Marquee("news", 125.millis), 6, 1)).head
    assert(at1.startsWith("ews"))
    // wraps around: phase equal to content+gap is back at the start
    val wrapped = trimmedLines(rendered(Marquee("news", 1000.millis), 6, 1)).head
    assert(wrapped == at0)
