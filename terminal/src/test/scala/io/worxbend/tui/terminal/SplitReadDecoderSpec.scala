package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Event, KeyCode, KeyEvent, KeyModifiers}

import org.scalatest.funsuite.AnyFunSuite

import io.worxbend.tui.terminal.InputDecoder.{EndOfStream, ReadExpired}
import io.worxbend.tui.terminal.ScriptedInput.{Esc, csi, decoder, ss3}

/** What the decoder does when the bytes of one event arrive in more than one read.
  *
  * Every other decoder suite drives a stream that is always ready: the next character is there the instant it is asked
  * for, so "the sequence arrived in two reads" is a case none of them can express. On a real terminal it is the normal
  * case — a paste of a few kilobytes crosses an ssh link in many packets, and an arrow key pressed at the wrong moment
  * straddles one — and it is precisely the behaviour that moves when the reader underneath changes: JLine 4.1.1 flipped
  * its raw-mode `VMIN`/`VTIME` from `0`/`1` to `1`/`0`, which changes exactly when a read returns empty-handed rather
  * than waiting.
  *
  * The vocabulary is [[InputDecoder]]'s own. A script entry of [[ReadExpired]] is a read that found nothing *yet*, and
  * the very next one may produce a character; [[EndOfStream]] is the stream being gone for good. Collapsing the two is
  * the defect this suite exists to keep out, and it is a different defect at each depth: at an event's first byte it
  * makes a quiet moment look like a closed terminal, and inside a bracketed paste it used to truncate the payload and
  * hand the remainder to the focused widget one keystroke at a time.
  */
final class SplitReadDecoderSpec extends AnyFunSuite:

  /** A scripted stream that counts its reads and says what an exhausted script answers with.
    *
    * The count is the only way to tell "gave up immediately" from "waited out a stall" from "spun forever": all three
    * can produce the same events, and only the number of reads separates them.
    */
  private final class CountingScript(script: Seq[Int], exhausted: Int):
    private val remaining = script.iterator
    private var readCount = 0

    val decoder: InputDecoder =
      InputDecoder(_ => { readCount += 1; if remaining.hasNext then remaining.next() else exhausted })

    def reads: Int = readCount

  /** The events `calls` successive `decode` calls produce, with the empty answers dropped. */
  private def drain(input: InputDecoder, calls: Int): List[Event] =
    val events = List.newBuilder[Event]
    var call   = 0
    while call < calls do
      input.decode(10L).foreach(event => events += event)
      call += 1
    events.result()

  private def key(code: KeyCode): Event                          = Event.Key(KeyEvent.of(code))
  private def key(code: KeyCode, modifiers: KeyModifiers): Event = Event.Key(KeyEvent(code, modifiers))

  test("a read that found nothing yet is not the end of the stream"):
    // the two negative answers mean opposite things: "ask again" and "never again". A decoder that reports EndOfInput
    // for a quiet moment shuts the application down between keystrokes.
    val input = decoder('a'.toInt, ReadExpired, ReadExpired, 'b'.toInt)
    assert(input.decode(10L) == Some(key(KeyCode.Char('a'))))
    assert(input.decode(10L).isEmpty)
    assert(input.decode(10L).isEmpty)
    assert(input.decode(10L) == Some(key(KeyCode.Char('b'))))

  test("gaps between whole events change nothing about what the stream decodes to"):
    val session = csi("A") ++ Seq(ReadExpired) ++ csi("1;5C") ++ Seq(ReadExpired, ReadExpired) ++
      ss3("P") ++ Seq(ReadExpired) ++ "ab".map(_.toInt)
    assert(
      drain(decoder(session*), 12) == List(
        key(KeyCode.Up),
        key(KeyCode.Right, KeyModifiers.Ctrl),
        key(KeyCode.F(1)),
        key(KeyCode.Char('a')),
        key(KeyCode.Char('b')),
      )
    )

  test("an ESC whose sequence never arrives reports Escape and eats nothing after it"):
    // this is what the escape timeout is for, and the character that arrives afterwards must still be its own key
    val input = decoder(Esc, ReadExpired, 'x'.toInt)
    assert(drain(input, 3) == List(key(KeyCode.Escape), key(KeyCode.Char('x'))))

  test("a CSI torn by a gap is dropped and the next sequence still decodes"):
    // the torn sequence must not be reported as an invented key, and — the half that only a split read can check — the
    // decoder must not stay one byte out of step afterwards
    val input = decoder((csi("1;5") ++ Seq(ReadExpired) ++ csi("A"))*)
    assert(drain(input, 4) == List(key(KeyCode.Up)))

  test("a final byte arriving after the gap leaves the stream aligned, not desynced"):
    // `ESC [` then a stall then `A`: the sequence is already abandoned by the time `A` arrives, so `A` is an ordinary
    // keystroke — and, the point of the test, so is the `q` behind it. Desyncing here mangles every key that follows.
    val input = decoder(Esc, '['.toInt, ReadExpired, 'A'.toInt, 'q'.toInt)
    assert(drain(input, 5) == List(key(KeyCode.Char('A')), key(KeyCode.Char('q'))))

  test("an astral character split across two reads never reports an unpaired surrogate half"):
    // the reader hands back UTF-16 code units, so an emoji is two of them; delivering one alone corrupts whatever text
    // model it lands in, and the key typed after it must still arrive
    val emoji  = "😀".map(_.toInt)
    val events = drain(decoder(emoji.head, ReadExpired, emoji.last, 'q'.toInt), 5)
    assert(events.forall {
      case Event.Key(KeyEvent(KeyCode.Char(codePoint), _)) => !Character.isSurrogate(codePoint.toChar)
      case _                                               => true
    })
    assert(events.contains(key(KeyCode.Char('q'))))

  test("a paste stalled mid-payload is one event, and its text is never dispatched as keystrokes"):
    // 200ms of silence inside a paste used to end it there, leaving the rest of the payload and the `CSI 201~`
    // terminator in the buffer to be decoded as ordinary keys: the `q` quits the application, the newline submits
    val script = csi("200~") ++ "qui".map(_.toInt) ++ Seq(ReadExpired) ++ "t\nnow".map(_.toInt) ++ csi("201~")
    assert(drain(decoder(script*), 6) == List(Event.Paste("quit\nnow")))

  test("a paste that stalls before its first character still arrives whole"):
    val script = csi("200~") ++ Seq(ReadExpired, ReadExpired) ++ "x".map(_.toInt) ++ csi("201~")
    assert(drain(decoder(script*), 4) == List(Event.Paste("x")))

  test("a paste cut off by the end of the stream ends at once, without waiting out a stall"):
    // end of file is permanent: the rest of the payload can never arrive, so there is nothing to wait for. Reading
    // again would be the spin-after-EOF this decoder's whole negative-value vocabulary exists to prevent.
    val script = CountingScript(csi("200~") ++ "half".map(_.toInt), EndOfStream)
    val input  = script.decoder
    assert(input.decode(10L) == Some(Event.Paste("half")))
    // six for `ESC [ 2 0 0 ~`, four payload characters, one that reported the end — and not one read more
    assert(script.reads == 11)
    assert(input.decode(10L) == Some(Event.EndOfInput))

  test("a paste whose sender goes silent gives up after a bounded wait rather than blocking forever"):
    // the other side of the stall budget: waiting out a gap must not become waiting out a terminal that dropped the
    // terminator on the floor, because this runs on the render thread
    val script = CountingScript(csi("200~") ++ "half".map(_.toInt), ReadExpired)
    val input  = script.decoder
    assert(input.decode(10L) == Some(Event.Paste("half")))
    assert(script.reads < 64)

  test("an X10 mouse report whose button byte is below the +32 bias is dropped, not read as a scroll"):
    // `ESC [ M NUL NUL NUL` — three control bytes anything sharing the tty can leave behind. Subtracting the bias from
    // 0 gives -32, whose two's-complement bits have the wheel bit set, so this used to decode as a scroll-up at the
    // origin and scroll whatever list had focus.
    assert(decoder(Esc, '['.toInt, 'M'.toInt, 0, 0, 0).decode(10L).isEmpty)

  test("an X10 mouse report whose coordinate byte is below the +32 bias is dropped too"):
    // every byte of a real report carries the bias, so a smaller one is not a coordinate; it used to be clamped to 0
    // and reported as a click at the origin
    assert(decoder(Esc, '['.toInt, 'M'.toInt, 32, 31, 32 + 5).decode(10L).isEmpty)
    assert(decoder(Esc, '['.toInt, 'M'.toInt, 32, 32 + 5, 31).decode(10L).isEmpty)
