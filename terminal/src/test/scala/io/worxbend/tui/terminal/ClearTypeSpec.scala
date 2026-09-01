package io.worxbend.tui.terminal

import io.worxbend.tui.core.Size

import org.scalatest.funsuite.AnyFunSuite

/** The erase forms and the backend operation that emits them.
  *
  * There used to be exactly one erase in the library, `CSI 2J`, written privately when the alternate screen was
  * entered. That is the one form an app drawing under a shell prompt must never use: it takes the user's scrollback
  * with it. The cursor-relative forms are what such an app needs instead, and they were absent.
  */
final class ClearTypeSpec extends AnyFunSuite:

  private val Esc = '\u001b'

  test("each clear kind maps to its ECMA-48 erase sequence"):
    assert(AnsiSequences.clear(ClearType.All) == s"$Esc[2J")
    assert(AnsiSequences.clear(ClearType.AfterCursor) == s"$Esc[0J")
    assert(AnsiSequences.clear(ClearType.BeforeCursor) == s"$Esc[1J")
    assert(AnsiSequences.clear(ClearType.CurrentLine) == s"$Esc[2K")
    assert(AnsiSequences.clear(ClearType.UntilNewLine) == s"$Esc[0K")

  test("every kind produces a distinct sequence"):
    // a copy-paste in the match above would otherwise be invisible: two kinds erasing the same thing still "works"
    val emitted = ClearType.values.toSeq.map(AnsiSequences.clear)
    assert(emitted.distinct.size == emitted.size)

  test("the whole-screen constant and the whole-screen kind are the same string"):
    // one spelling, so a correction to the sequence cannot land in one place and not the other
    assert(AnsiSequences.ClearScreen == AnsiSequences.clear(ClearType.All))

  test("the headless backend records every erase in order"):
    val backend = HeadlessBackend(Size(10, 3))
    assert(backend.clearedRegions.isEmpty)
    assert(backend.clearRegion(ClearType.CurrentLine).isRight)
    assert(backend.clearRegion(ClearType.AfterCursor).isRight)
    assert(backend.clearedRegions == Seq(ClearType.CurrentLine, ClearType.AfterCursor))
