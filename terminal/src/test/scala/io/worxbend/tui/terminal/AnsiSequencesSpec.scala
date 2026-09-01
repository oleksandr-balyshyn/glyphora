package io.worxbend.tui.terminal

import org.scalatest.funsuite.AnyFunSuite

final class AnsiSequencesSpec extends AnyFunSuite:

  private val Esc = ""

  test("moveTo converts zero-based coordinates to one-based ANSI"):
    assert(AnsiSequences.moveTo(0, 0) == s"$Esc[1;1H")
    assert(AnsiSequences.moveTo(9, 4) == s"$Esc[5;10H")
