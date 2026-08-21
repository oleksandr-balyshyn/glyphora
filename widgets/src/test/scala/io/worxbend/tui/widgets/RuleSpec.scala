package io.worxbend.tui.widgets

import io.worxbend.tui.core.Direction
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class RuleSpec extends AnyFunSuite:

  test("a horizontal rule spans the width with an inline label"):
    assert(trimmedLines(rendered(Rule(), 6, 1)) == Seq("──────"))
    assert(trimmedLines(rendered(Rule(label = Some("cfg")), 10, 1)) == Seq("── cfg ───"))

  test("a vertical rule spans the height"):
    assert(trimmedLines(rendered(Rule(orientation = Direction.Vertical), 1, 3)) == Seq("│", "│", "│"))
