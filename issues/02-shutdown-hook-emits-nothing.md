# The restore shutdown hook never writes a byte

**Labels:** `bug`, `critical`, `terminal-lifecycle`, `tui-runtime`

## Problem

`TerminalRunner` registers a hook specifically for signal-terminated exits
(`runtime/src/main/scala/io/worxbend/tui/runtime/TerminalRunner.scala:34-35`):

```scala
val restoreOnShutdown = new Thread(() => backend.close(), "glyphora-terminal-restore")
Runtime.getRuntime.addShutdownHook(restoreOnShutdown)
```

It runs, and emits nothing. Measured three times each on SIGTERM, SIGHUP and `System.exit`,
identical every time:

```
== sterm ==  APP_EXIT=143
  alt-leave=0  cursor-show=0  bp-off=0  kitty-pop=0
```

A tracing decorator shows `close()` entering and exiting instantly with no output. The guard
flags are correct — a probe reading them reflectively inside a shutdown hook reports
`cursorHidden=true alt=true savedAttributes=Some(...)` — and adding `@volatile` to all four
fields changed nothing (re-measured: still `alt-leave=0`).

The cause, from the same probe:

```
java.lang.IllegalStateException: Terminal has been closed
    at org.jline.terminal.impl.AbstractTerminal.checkClosed(AbstractTerminal.java:143)
    at org.jline.terminal.impl.PosixSysTerminal.writer(PosixSysTerminal.java:124)
```

`PosixSysTerminal`'s constructor registers its own closer (`PosixSysTerminal.java:102`,
`ShutdownHooks.add(closer)`). It wins the race; afterwards every `terminal.writer()` call
throws, and `JLine3Backend.close()` funnels each teardown step through `attempt` →
`bestEffort` (`JLine3Backend.scala:176-177`), which discards the failure silently.

The comment at `TerminalRunner.scala:30-33` asserting the hook protects against SIGTERM/SIGHUP
is therefore wrong.

## Proposal

Make the hook independent of JLine's terminal object by writing the restore bytes straight to
the process stdout file descriptor. Every sequence involved is idempotent (XTerm
`ctlseqs.ms`, "DEC Private Mode Reset"), so re-emitting modes that were never enabled is safe.

```scala
// TerminalRunner.scala
private val RestoreSequence =
  "[?25h[?1049l[?2004l[?1004l[?1006l[?1002l[?1000l[<u[0m"

val restoreOnShutdown = new Thread(
  () =>
    backend.close()                                  // preferred path when the terminal is still open
    try
      val out = java.io.FileOutputStream(java.io.FileDescriptor.out)
      out.write(RestoreSequence.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      out.flush()
    catch case scala.util.control.NonFatal(_) => (),
  "glyphora-terminal-restore",
)
```

This does not restore termios; JLine's own closer does that (verified: `stty` is clean after
SIGTERM today). Issue #01 must land as well so Ctrl+C reaches a hook at all.

Also: `bestEffort` should log at debug rather than discarding, so the next silent teardown
failure is discoverable.

## Acceptance criteria

- [ ] `kill -TERM` in a PTY emits `ESC[?1049l` and `ESC[?25h`
- [ ] `kill -HUP` in a PTY does the same
- [ ] `System.exit` from a key handler does the same
- [ ] `stty -a` after each equals the pre-launch snapshot
- [ ] Normal return and an uncaught `view` exception still restore exactly once (no duplicate sequences)
- [ ] The misleading comment at `TerminalRunner.scala:30-33` is corrected
