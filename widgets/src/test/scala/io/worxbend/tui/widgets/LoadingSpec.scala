package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.DurationInt

final class LoadingSpec extends AnyFunSuite:

  test("a skeleton fills the area with shade and a sweeping band"):
    val lines = trimmedLines(rendered(Skeleton(300.millis), 10, 2))
    assert(lines.forall(_.length == 10))
    assert(lines.head.contains("▒"))
    assert(lines.head.contains("░"))

  test("the skeleton band moves with the phase"):
    val early = trimmedLines(rendered(Skeleton(200.millis), 10, 1)).head
    val later = trimmedLines(rendered(Skeleton(500.millis), 10, 1)).head
    assert(early != later)

  /** One `period` is the whole round trip, so the segment is at the left edge at 0, at the far edge halfway through,
    * and back where it started after a full period — whatever the app's tick rate.
    */
  test("an indeterminate bar bounces its segment between the edges"):
    val period   = 1600.millis
    val atStart  = trimmedLines(rendered(IndeterminateBar(0.millis), 12, 1)).head
    assert(atStart.startsWith("━"))
    val halfway  = trimmedLines(rendered(IndeterminateBar(period / 2), 12, 1)).head
    assert(halfway.endsWith("━"), s"halfway through the bar should be at the far edge, got '$halfway'")
    val returned = trimmedLines(rendered(IndeterminateBar(period), 12, 1)).head
    assert(returned == atStart, s"a full period should return to the start, got '$returned'")

  test("a marquee rotates its content through the area"):
    val at0     = trimmedLines(rendered(Marquee("news", 0.millis), 6, 1)).head
    assert(at0.startsWith("news"))
    val at1     = trimmedLines(rendered(Marquee("news", 125.millis), 6, 1)).head
    assert(at1.startsWith("ews"))
    // wraps around: phase equal to content+gap is back at the start
    val wrapped = trimmedLines(rendered(Marquee("news", 1000.millis), 6, 1)).head
    assert(wrapped == at0)
