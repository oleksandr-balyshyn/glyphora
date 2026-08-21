package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.DurationInt

final class SkeletonSpec extends AnyFunSuite:

  test("a skeleton fills the area with shade and a sweeping band"):
    val lines = trimmedLines(rendered(Skeleton(300.millis), 10, 2))
    assert(lines.forall(_.length == 10))
    assert(lines.head.contains("▒"))
    assert(lines.head.contains("░"))

  test("the skeleton band moves with the phase"):
    val early = trimmedLines(rendered(Skeleton(200.millis), 10, 1)).head
    val later = trimmedLines(rendered(Skeleton(500.millis), 10, 1)).head
    assert(early != later)
