# Ctrl+Z suspends the process inside raw mode and the alternate screen

**Labels:** `bug`, `high`, `terminal-lifecycle`, `tui-terminal`

## Problem

`JLine3Backend` registers exactly one signal
(`terminal/src/main/scala/io/worxbend/tui/terminal/JLine3Backend.scala:27-30`):

```scala
terminal.handle(
  Terminal.Signal.WINCH,
  _ => pendingResize.set(Some(currentSize)),
)
```

`grep -rn 'Signal.TSTP\|Signal.CONT' --include='*.scala'` returns nothing.

Per issue #01, JLine sets `TSTP` and `CONT` to `SIG_DFL`, so `^Z` stops the process with
`?1049h`, `?25l`, `?2004h`, `?1004h` and `CSI > 1 u` all still in effect, and `SIGCONT` resumes
with the app unaware: no re-setup, no full repaint, and any resize that happened while stopped
is lost.

`Backend.suspend` (`terminal/.../Backend.scala:49`) already implements exactly the
teardown/restore sequence needed (`JLine3Backend.scala:132-151`) — it is simply never wired to
the signal.

**Reproduction limitation, stated honestly:** the stop could not be observed empirically in
this audit. Under `script(1)` the app lands in an orphaned process group, and POSIX requires
SIGTSTP to be discarded for orphaned groups — both `^Z` and `kill -TSTP` left the process in
state `S`. The finding rests on code inspection plus the measured `SigCgt` mask from issue #01.
Reproduce in an interactive shell.

## Proposal

With issue #01's `signalHandler(SIG_IGN)` in place JLine installs Java-level handlers, so:

```scala
terminal.handle(Terminal.Signal.TSTP, _ =>
  if mouseCaptureActive then bestEffort(disableMouseCapture())
  if cursorHidden then bestEffort(showCursor())
  if alternateScreenActive then bestEffort(leaveAlternateScreen())
  if savedAttributes.nonEmpty then bestEffort(disableRawMode())
  terminal.writer().flush()
  Signals.registerDefault("TSTP")      // restore SIG_DFL …
  raiseSelf("TSTP"))                   // … then actually stop

terminal.handle(Terminal.Signal.CONT, _ =>
  terminal.handle(Terminal.Signal.TSTP, tstpHandler)   // re-arm
  bestEffort(enableRawMode())
  bestEffort(enterAlternateScreen())
  bestEffort(hideCursor())
  if mouseWasCaptured then bestEffort(enableMouseCapture())
  pendingResize.set(Some(currentSize)))                // forces a full repaint at the current size
```

`raiseSelf` is `sun.misc.Signal.raise`, reached the same way JLine reaches it
(`org/jline/utils/Signals.java:135-142`). Note this needs an exemption from CI's "Reflection
discipline check" — see open question 3 in `TUI-REVIEW.md`.

Extract the teardown/restore bodies so `suspend`, `close` and the TSTP/CONT handlers share one
implementation rather than three copies.

## Acceptance criteria

- [ ] In an interactive shell, `^Z` returns the prompt on the main screen with a visible cursor and cooked mode
- [ ] `fg` repaints the full UI
- [ ] Resizing the window while stopped produces a correctly-sized frame after `fg`
- [ ] Mouse capture is restored after `fg` when it was enabled before `^Z`
- [ ] Suspending twice in a row works (the TSTP handler is re-armed on CONT)
- [ ] `suspend`, `close` and the signal handlers share one teardown implementation
