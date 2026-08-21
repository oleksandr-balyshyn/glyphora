package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.DurationInt

final class IndeterminateBarSpec extends AnyFunSuite:

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
