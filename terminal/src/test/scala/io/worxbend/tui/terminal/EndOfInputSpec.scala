package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Event, KeyCode, KeyEvent}

import org.scalatest.funsuite.AnyFunSuite

import io.worxbend.tui.terminal.ScriptedInput.NothingAvailable

/** What the decoder does when the input stream ends for good.
  *
  * The reader glyphora sits on top of has two different negative answers. `-2` ("read expired") means *no character
  * arrived within the timeout*, and the very next read may well produce one. `-1` means *end of file*: the other end of
  * the stream is gone — piped input that ran out, a closed terminal — and no read will ever produce a character again.
  * Collapsing the two into a single "nothing available" is what used to make the runner spin at 100% CPU after EOF:
  * every read returned immediately, the loop saw "no event", and went straight back for another read.
  */
final class EndOfInputSpec extends AnyFunSuite:

  /** A decoder whose stream has already ended: every read reports end of file. */
  private def atEof: InputDecoder = InputDecoder(_ => -1)

  test("end of file decodes to the end-of-input event rather than to nothing"):
    assert(atEof.decode(10) == Some(Event.EndOfInput))

  test("a timeout still decodes to no event"):
    assert(InputDecoder(_ => NothingAvailable).decode(10).isEmpty)

  test("end of input is reported again on every later read"):
    // Once the stream is gone it stays gone, so a caller that keeps polling must keep being told, not eventually be
    // told "nothing available" again and resume waiting on a stream that can never speak.
    val decoder = atEof
    assert(decoder.decode(10) == Some(Event.EndOfInput))
    assert(decoder.decode(10) == Some(Event.EndOfInput))

  test("input already buffered is decoded before the end of the stream is reported"):
    val queued  = Iterator('h'.toInt, 'i'.toInt)
    val decoder = InputDecoder(_ => if queued.hasNext then queued.next() else -1)
    assert(decoder.decode(10) == Some(Event.Key(KeyEvent.of(KeyCode.Char('h')))))
    assert(decoder.decode(10) == Some(Event.Key(KeyEvent.of(KeyCode.Char('i')))))
    assert(decoder.decode(10) == Some(Event.EndOfInput))
