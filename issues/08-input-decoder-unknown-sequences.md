# SS3 arrows, X10 mouse, and synthetic `Escape` from unparsed sequences

**Labels:** `bug`, `high`, `input`, `tui-terminal`

## Problem

Three related defects in `terminal/src/main/scala/io/worxbend/tui/terminal/InputDecoder.scala`.

### a) Unknown sequences become an `Escape` keypress

```scala
// :64-67
case 'u'  => decodeKittyKey(numbers)
case '~'  => decodeTilde(numbers)
case _    => key(KeyCode.Escape)
```

```scala
// :123-131 — SS3 handles only P Q R S H F
private def decodeSs3(): Event =
  read(EscapeTimeoutMillis) match
    case 'P' => key(KeyCode.F(1))
    ...
    case _   => key(KeyCode.Escape)
```

Measured:

```
ESC O A     (Up, DECCKM application cursor keys)  ->  Key(KeyEvent(Escape,0))
ESC [ ?1$p  (DECRPM reply)                        ->  Key(KeyEvent(Escape,0))
ESC [ 1;5   (torn sequence, read timeout)         ->  Key(KeyEvent(Escape,0))
```

Terminals emit reply and capability sequences the decoder does not know — Primary DA
(`ESC[?62;…c`), DSR cursor reports (`ESC[r;cR`), XTVERSION, kitty's `ESC[?…u` reply. Each
becomes a synthetic `Escape`, which in almost every TUI convention means "close this dialog".

### b) SS3 cursor keys are unhandled

Per XTerm `ctlseqs.ms`, `ESC O A`–`ESC O D` are the cursor keys under DECCKM (`?1h`). tmux with
`xterm-keys on`, and any app that shells out to a program which enables DECCKM, will deliver
them.

### c) Legacy X10 mouse reports become four bogus keypresses

```scala
// :49-50
val isSgrMouse = params.startsWith("<")
if isSgrMouse && (finalByte == 'M' || finalByte == 'm') then decodeSgrMouse(...)
```

An X10 report `ESC [ M <btn> <x> <y>` has an empty parameter string, so `M` is taken as the
final byte, the branch is skipped, and the three coordinate bytes are re-decoded as text:

```
ESC [ M ' ' 'A' 'A'  ->  Key(Escape) | Key(Char( )) | Key(Char(A)) | Key(Char(A))
```

`enableMouseCapture` requests SGR 1006 (`AnsiSequences.scala:15`), but a terminal that does not
implement it keeps sending X10 — that is the point of the negotiation. Injecting `Escape` plus
arbitrary printable characters into an app's key path is how a mouse click triggers a delete
binding.

(SGR 1006 itself decodes correctly: press, release, drag, wheel and column 300 all verified.)

## Proposal

1. Add the SS3 arrows to `decodeSs3`:

```scala
case 'A' => key(KeyCode.Up)
case 'B' => key(KeyCode.Down)
case 'C' => key(KeyCode.Right)
case 'D' => key(KeyCode.Left)
```

2. Handle the empty-parameter `M` before the SGR branch in `decodeCsiFinal`:

```scala
if params.isEmpty && finalByte == 'M' then
  val b = read(EscapeTimeoutMillis); val x = read(EscapeTimeoutMillis); val y = read(EscapeTimeoutMillis)
  if b < 0 || x < 0 || y < 0 then None
  else Some(decodeX10Mouse(b - 32, x - 32, y - 32))
```

Document that X10 coordinates saturate at 223 (the byte is `32 + coord`), so clicks past column
223 are reported at 223 rather than wrongly.

3. Change `decodeCsiFinal` and `decodeSs3` to return `Option[Event]` and have `decode` skip to
   the next event on `None`. Never synthesize `Escape` from an unparsed sequence.

Also worth doing in the same pass: `EscapeTimeoutMillis` is hardcoded at 50 ms
(`InputDecoder.scala:167`). Over a high-latency link a real `ESC [ A` can split across that
window and decode as three junk events. Make it a constructor parameter surfaced through
`RunnerConfig`.

## Acceptance criteria

- [ ] `ESC O A/B/C/D` decode to `Up`/`Down`/`Right`/`Left`
- [ ] `ESC[?62;1;4c` (Primary DA) produces no event
- [ ] `ESC[24;80R` (DSR) produces no event
- [ ] A torn CSI that times out mid-sequence produces no event
- [ ] `ESC[M` + `(32, 32+33, 32+33)` decodes to one `MouseEvent(32, 32, Down)` and consumes six bytes
- [ ] The escape timeout is configurable through `RunnerConfig`
- [ ] Fixtures replayed byte-at-a-time and in randomly-split chunks give identical results
