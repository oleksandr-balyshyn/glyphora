package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class LoadingSpec extends AnyFunSuite:

  test("a skeleton fills the area with shade and a sweeping band"):
    val lines = trimmedLines(rendered(Skeleton(phase = 3), 10, 2))
    assert(lines.forall(_.length == 10))
    assert(lines.head.contains("▒"))
    assert(lines.head.contains("░"))

  test("the skeleton band moves with the phase"):
    val early = trimmedLines(rendered(Skeleton(phase = 2), 10, 1)).head
    val later = trimmedLines(rendered(Skeleton(phase = 5), 10, 1)).head
    assert(early != later)

  test("an indeterminate bar bounces its segment between the edges"):
    val atStart = trimmedLines(rendered(IndeterminateBar(phase = 0), 12, 1)).head
    assert(atStart.startsWith("━"))
    val bounced = trimmedLines(rendered(IndeterminateBar(phase = 9), 12, 1)).head
    assert(bounced.startsWith("─"))
    assert(bounced.contains("━"))
    // travel = 9, bounce period 18: phase 9 is the far edge, phase 18 back at the start
    assert(trimmedLines(rendered(IndeterminateBar(phase = 18), 12, 1)).head.startsWith("━"))

  test("a marquee rotates its content through the area"):
    val at0     = trimmedLines(rendered(Marquee("news", phase = 0), 6, 1)).head
    assert(at0.startsWith("news"))
    val at1     = trimmedLines(rendered(Marquee("news", phase = 1), 6, 1)).head
    assert(at1.startsWith("ews"))
    // wraps around: phase equal to content+gap is back at the start
    val wrapped = trimmedLines(rendered(Marquee("news", phase = 8), 6, 1)).head
    assert(wrapped == at0)
