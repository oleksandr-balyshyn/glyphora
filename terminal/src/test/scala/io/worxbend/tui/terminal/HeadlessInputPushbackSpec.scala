package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Event, KeyCode, KeyEvent, Size}

import scala.concurrent.duration.DurationInt

import org.scalatest.funsuite.AnyFunSuite

/** [[HeadlessBackend.postInput]] must decode a script exactly as the production read loop does, including the last
  * character of it.
  */
final class HeadlessInputPushbackSpec extends AnyFunSuite:

  /** A CSI torn off by a fresh `ESC` hands that `ESC` back to the decoder instead of consuming it, so the character
    * that ends the script is still inside the decoder when the script iterator runs dry. Ending the drain there loses
    * the Escape keypress that a real terminal would have delivered.
    */
  test("an escape pushed back at the end of the script is still queued"):
    val backend = HeadlessBackend(Size(10, 3))
    backend.postInput(Seq(0x1b, '[', 0x1b))
    assert(backend.pendingEvents == 1)
    assert(backend.readEvent(10.millis) == Right(Some(Event.Key(KeyEvent.of(KeyCode.Escape)))))
