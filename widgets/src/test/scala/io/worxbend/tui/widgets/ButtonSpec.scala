package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class ButtonSpec extends AnyFunSuite:

  test("a button renders its bracketed label centered"):
    assert(trimmedLines(rendered(Button("OK"), 10, 1)) == Seq("  [ OK ]"))

  test("a long button label clips at the area"):
    assert(trimmedLines(rendered(Button("Continue"), 6, 1)) == Seq("[ Cont"))
