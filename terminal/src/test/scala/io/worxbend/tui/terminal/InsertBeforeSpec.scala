package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Color, Event, Position, Rect, Size, Style, Widget}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.Duration

/** Covers [[Backend.insertBefore]]: the styled counterpart of `printAbove`, which hands a widget a block of rows and
  * puts what it paints into the terminal's scrollback.
  *
  * Three things are pinned here — the fallback every backend inherits, the plain-text flattening that fallback uses,
  * and `HeadlessBackend`'s richer recording, which is what a test above this module asserts against.
  */
final class InsertBeforeSpec extends AnyFunSuite:

  /** A backend that implements only what [[Backend]] leaves abstract, so what it does with `insertBefore` is the
    * trait's own default — the same code a third-party backend written before this method existed inherits.
    */
  private final class RecordingBackend(terminalSize: Size) extends Backend:
    val printed: scala.collection.mutable.ArrayBuffer[String] = scala.collection.mutable.ArrayBuffer.empty

    def size: Either[BackendError, Size]                                    = Right(terminalSize)
    def draw(buffer: Buffer): Either[BackendError, Unit]                    =
      val _ = buffer
      Right(())
    def enableRawMode(): Either[BackendError, Unit]                         = Right(())
    def disableRawMode(): Either[BackendError, Unit]                        = Right(())
    def enterAlternateScreen(): Either[BackendError, Unit]                  = Right(())
    def leaveAlternateScreen(): Either[BackendError, Unit]                  = Right(())
    def enableMouseCapture(): Either[BackendError, Unit]                    = Right(())
    def disableMouseCapture(): Either[BackendError, Unit]                   = Right(())
    def hideCursor(): Either[BackendError, Unit]                            = Right(())
    def showCursor(): Either[BackendError, Unit]                            = Right(())
    def readEvent(timeout: Duration): Either[BackendError, Option[Event]]   =
      val _ = timeout
      Right(None)
    override def printAbove(lines: Seq[String]): Either[BackendError, Unit] =
      printed ++= lines
      Right(())
    def close(): Either[BackendError, Unit]                                 = Right(())

  private def text(line: String, style: Style = Style.Default): Widget =
    (area: Rect, buffer: Buffer) => buffer.setString(area.x, area.y, line, style)

  test("the inherited default renders the block and emits its rows as plain text"):
    val backend = RecordingBackend(Size(20, 6))
    assert(backend.insertBefore(1, text("built in 3.1s", Style.Default.bold)) == Right(()))
    assert(backend.printed.toSeq == Seq("built in 3.1s"))

  test("a block taller than one row emits one line per row, in order"):
    val backend        = RecordingBackend(Size(20, 6))
    val widget: Widget = (area, buffer) =>
      buffer.setString(area.x, area.y, "first", Style.Default)
      buffer.setString(area.x, area.y + 1, "second", Style.Default)
    assert(backend.insertBefore(2, widget) == Right(()))
    assert(backend.printed.toSeq == Seq("first", "second"))

  test("a row the widget left blank still emits a line, so the block keeps its shape"):
    val backend        = RecordingBackend(Size(20, 6))
    val widget: Widget = (area, buffer) => buffer.setString(area.x, area.y + 1, "under a gap", Style.Default)
    assert(backend.insertBefore(2, widget) == Right(()))
    assert(backend.printed.toSeq == Seq("", "under a gap"))

  test("a height of zero or less inserts nothing at all"):
    val backend = RecordingBackend(Size(20, 6))
    assert(backend.insertBefore(0, text("never printed")) == Right(()))
    assert(backend.insertBefore(-4, text("never printed")) == Right(()))
    assert(backend.printed.isEmpty)

  test("the widget is not even asked to render for a non-positive height"):
    // it may be an expensive draw, and a caller that computed a height of zero from an empty list should pay nothing
    val backend        = RecordingBackend(Size(20, 6))
    var rendered       = false
    val widget: Widget = (_, _) => rendered = true
    val _              = backend.insertBefore(0, widget)
    assert(!rendered)

  test("a widget wider than the terminal is clipped by the buffer, not by the caller"):
    val backend = RecordingBackend(Size(8, 6))
    assert(backend.insertBefore(1, text("far too long to fit")) == Right(()))
    // eight columns fit; the eighth is the space after "too", and trailing blanks are trimmed on the way out
    assert(backend.printed.toSeq == Seq("far too"))

  // ---------------------------------------------------------------- flattening to plain text

  test("trailing padding is trimmed, so an inserted line does not run to the window's edge"):
    val buffer = Buffer(Rect(0, 0, 12, 1))
    buffer.setString(0, 0, "hi", Style.Default)
    assert(Backend.plainRows(buffer) == Seq("hi"))

  test("a two-column grapheme is written once, not once per column it occupies"):
    // the continuation column is a reserved blank the terminal never draws; printing it would shift the rest of the
    // line one column to the right
    val buffer = Buffer(Rect(0, 0, 12, 1))
    buffer.setString(0, 0, "漢字ok", Style.Default)
    assert(Backend.plainRows(buffer) == Seq("漢字ok"))

  test("combining marks travel with the character they modify"):
    val buffer = Buffer(Rect(0, 0, 12, 1))
    buffer.setString(0, 0, "café", Style.Default)
    assert(Backend.plainRows(buffer) == Seq("café"))

  test("an emoji built from a zero-width joiner sequence survives as one cell"):
    val family = "👩‍💻" // woman technologist: woman + ZWJ + laptop
    val buffer = Buffer(Rect(0, 0, 12, 1))
    buffer.setString(0, 0, family, Style.Default)
    assert(Backend.plainRows(buffer) == Seq(family))

  test("a blank row flattens to an empty string rather than a row of spaces"):
    assert(Backend.plainRows(Buffer(Rect(0, 0, 12, 2))) == Seq("", ""))

  // ---------------------------------------------------------------- the headless recording

  test("the headless backend keeps the rendered block, styling included"):
    val backend = HeadlessBackend(Size(20, 6))
    val red     = Style.Default.withFg(Color.Red).bold
    assert(backend.insertBefore(1, text("ERROR disk full", red)) == Right(()))
    val block   = backend.insertedAbove.head
    assert(block.area == Rect(0, 0, 20, 1))
    assert(block.get(0, 0).symbol == "E")
    assert(block.get(0, 0).style == red)

  test("the headless backend also records the block's text, so plain-text assertions keep working"):
    val backend = HeadlessBackend(Size(20, 6))
    val _       = backend.printAbove(Seq("plain line"))
    val _       = backend.insertBefore(1, text("styled line"))
    assert(backend.printedAbove == Seq("plain line", "styled line"))

  test("the headless backend records nothing for a non-positive height"):
    val backend = HeadlessBackend(Size(20, 6))
    val _       = backend.insertBefore(0, text("never printed"))
    assert(backend.insertedAbove.isEmpty)
    assert(backend.printedAbove.isEmpty)

  test("blocks are kept in the order they were inserted"):
    val backend = HeadlessBackend(Size(20, 6))
    val _       = backend.insertBefore(1, text("one"))
    val _       = backend.insertBefore(1, text("two"))
    assert(backend.insertedAbove.map(_.get(0, 0).symbol) == Seq("o", "t"))

  test("a widget that draws outside its block cannot reach past it"):
    // the buffer clips, so an insertion cannot scribble on rows the caller did not ask for
    val backend        = HeadlessBackend(Size(20, 6))
    val widget: Widget = (area, buffer) => buffer.setString(area.x, area.y + 5, "escaped", Style.Default)
    val _              = backend.insertBefore(1, widget)
    assert(backend.printedAbove == Seq(""))

  test("the block is as wide as the terminal is now, so a resize changes the next block's width"):
    val backend = HeadlessBackend(Size(20, 6))
    val _       = backend.insertBefore(1, text("x"))
    backend.resizeTo(Size(9, 6))
    val _       = backend.insertBefore(1, text("x"))
    assert(backend.insertedAbove.map(_.area.width) == Seq(20, 9))

  test("the caret and the cursor are untouched by an insertion"):
    // it emits output, it does not draw a frame: nothing about the live UI's state may change
    val backend = HeadlessBackend(Size(20, 6))
    val _       = backend.setCursorPosition(Position(3, 1))
    val _       = backend.insertBefore(1, text("log line"))
    assert(backend.cursorPosition.contains(Position(3, 1)))
    assert(backend.drawCount == 0)
