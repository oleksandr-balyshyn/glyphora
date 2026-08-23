package io.worxbend.tui.terminal

import java.io.IOException

import org.scalatest.funsuite.AnyFunSuite

/** Pins the exact wording of [[BackendError.message]], because CI depends on the text and not just on the type.
  *
  * The `native-image` job in `.github/workflows/ci.yml` runs every example's binary with no TTY attached and asserts
  * the process both exits 1 and prints `terminal not supported`. That string comes from here, by way of `TuiApp.main`,
  * which prints `glyphora: ${error.message}`. Nothing in the compiler connects the two: rewording the case below used
  * to leave the workflow grepping for text no binary printed any more, and a gate that can never pass is a gate
  * contributors learn to ignore.
  *
  * These assertions are the fast half of that contract. If one of them fails, either update the workflow's `case`
  * pattern in the same commit or put the wording back.
  */
final class BackendErrorMessageSpec extends AnyFunSuite:

  test("UnsupportedTerminal's message is the string CI greps for"):
    assert(BackendError.UnsupportedTerminal("x").message == "terminal not supported: x")
    assert(
      BackendError.UnsupportedTerminal("dumb terminal (no TTY attached)").message ==
        "terminal not supported: dumb terminal (no TTY attached)"
    )

  test("Io reports the cause's message, or its class when the cause has none"):
    assert(BackendError.Io(IOException("Stream closed")).message == "terminal I/O failed: Stream closed")
    assert(BackendError.Io(IOException()).message == "terminal I/O failed: java.io.IOException")

  test("NotInRawMode reads as a sentence rather than a constructor call"):
    assert(BackendError.NotInRawMode.message == "the terminal is not in raw mode")
