# Ctrl+C leaves the terminal in raw mode and on the alternate screen

**Labels:** `bug`, `critical`, `terminal-lifecycle`, `tui-terminal`

## Problem

`JLine3Backend.create` (`terminal/src/main/scala/io/worxbend/tui/terminal/JLine3Backend.scala:196`)
builds the terminal with JLine's defaults:

```scala
val terminal = TerminalBuilder.builder().system(true).build()
```

`TerminalBuilder` defaults to `nativeSignals = true` and `signalHandler = SIG_DFL`
(`TerminalBuilder.java:354-355`), which makes `PosixSysTerminal` call
`Signals.registerDefault(...)` for every `Terminal.Signal` (`PosixSysTerminal.java:92-99`).
That in turn calls `sun.misc.Signal.handle(sig, SIG_DFL)` (`Signals.java:112-116`), removing
the JVM's own SIGINT handler — the one that runs shutdown hooks.

Measured on a running glyphora app versus a plain JVM (`/proc/<pid>/status`):

```
glyphora   SigCgt: SIGHUP SIGQUIT SIGILL ... SIGTERM SIGXFSZ SIGWINCH     <- no SIGINT
plain JVM  SigCgt: SIGHUP SIGINT SIGQUIT SIGILL ... SIGTERM SIGXFSZ
```

`examples/hello-world` in a real PTY, `^C` typed as byte `0x03`:

```
APP_EXIT=130
alt-enter=1  alt-leave=0     cursor-hide=1  cursor-show=0
bp-on=1      bp-off=0        focus-on=1     focus-off=0
kitty-push=1 kitty-pop=0
stty: isig -icanon -iexten -echo  min=0 time=1  -icrnl -ixon
```

The shell is handed back in raw mode, on the alternate screen, with an invisible cursor,
bracketed paste on, focus reporting on and a kitty keyboard flag pushed. `reset` is required.

Related: `TuiApp` documents and implements `Ctrl+C` as a quit key
(`dsl/.../TuiApp.scala:19-20`, `:120-121`), but that code is unreachable — JLine's
`enterRawMode()` leaves `ISIG` set (`AbstractTerminal.java:206-207`), so the line discipline
converts `0x03` to SIGINT before the reader sees it.

## Proposal

Stop letting JLine reset the OS dispositions, and route the signals into the event loop, which
already tears down correctly on the `finally` path.

1. In `JLine3Backend.create`:

```scala
val terminal = TerminalBuilder.builder()
  .system(true)
  .signalHandler(Terminal.SignalHandler.SIG_IGN)   // JLine installs Java handlers instead of SIG_DFL
  .encoding(java.nio.charset.StandardCharsets.UTF_8)
  .build()
```

2. Alongside the existing WINCH registration (`JLine3Backend.scala:27-30`):

```scala
private val pendingInterrupt = java.util.concurrent.atomic.AtomicBoolean(false)
terminal.handle(Terminal.Signal.INT,  _ => pendingInterrupt.set(true))
terminal.handle(Terminal.Signal.QUIT, _ => pendingInterrupt.set(true))
```

3. Add `case Interrupt` to `core.Event`; `readEvent` returns it ahead of the resize check.
4. `TerminalRunner.loop` treats an unconsumed `Event.Interrupt` as `handle.quit()`, so `run`'s
   `finally` performs the teardown that this audit measured as fully correct.
5. Decide whether `TuiApp.bindings` may intercept `Event.Interrupt` before the default quit
   (see open question 2 in `TUI-REVIEW.md`).

## Acceptance criteria

- [ ] `/proc/<pid>/status` `SigCgt` for a running glyphora app includes `SIGINT`
- [ ] Ctrl+C in a PTY emits `ESC[?1049l`, `ESC[?25h`, `ESC[?2004l`, `ESC[?1004l`, `ESC[<u`
- [ ] `stty -a` after Ctrl+C is byte-identical to the pre-launch snapshot
- [ ] `run()` returns normally (not via signal death) so caller code after `run()` executes
- [ ] A `HeadlessBackend` test asserts an unconsumed `Event.Interrupt` quits the loop
- [ ] The exit-path table in `website/docs/troubleshooting.md` is regenerated and shows ✅ for Ctrl+C
