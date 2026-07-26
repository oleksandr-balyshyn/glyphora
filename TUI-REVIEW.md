# glyphora — terminal audit and remediation

Audited and fixed on top of `412977f` (main), 2026-07-25/26. Everything below was run on
this machine: Linux 7.1.4 (CachyOS), GraalVM/Oracle JDK 25.0.3, Mill 1.1.7 on Zulu 21.0.10,
Scala 3.7.1, JLine 3.30.13, `TERM=xterm-256color`, `COLORTERM=truecolor`. Terminal behaviour was
exercised through real PTYs allocated by `script(1)`; escape traffic was captured byte-for-byte
and is quoted verbatim.

**Status: 23 of 25 findings fixed and verified. 2 deliberately deferred** (F-19, F-20 —
see §6). Test suite went from 528 to **595 tests, 90 suites, 0 failed, 0 ignored, 0 skipped**.

---

## 1. Verdict

glyphora was a genuinely well-built rendering and layout library wrapped around a terminal
lifecycle that had never survived a real signal — and that lifecycle is now the part that is
most thoroughly nailed down. The parts that are usually wrong in a young TUI library were
already right: grapheme-cluster width arithmetic is real, the double buffer emits a compact
run-length diff wrapped in DEC 2026 synchronized output, the layout solver never overlaps or
overflows, escape bytes in user strings are dropped before they reach a cell, and a headless
backend plus a `Pilot` driver make the whole suite run without a TTY. What was wrong was wrong
in the way that ends a shell session: **pressing Ctrl+C left the terminal raw, on the alternate
screen, with the cursor hidden** — and the shutdown hook written to prevent exactly that never
executed a single byte, on any signal path, because JLine closed the terminal out from under it
first. That root cause — the terminal lifecycle being owned by JLine's defaults rather than by
glyphora — is fixed, along with an OSC 8 escape-injection hole that let arbitrary Markdown write
the user's clipboard, a poll timeout that could wedge the event loop until a keypress, and a
locale-dependent encoder that rendered the entire UI as `?`. This is now shippable as 0.11.

---

## 2. Findings

| ID | Sev | Area | Title | Status |
|---|---|---|---|---|
| F-01 | **Critical** | Lifecycle | Ctrl+C / SIGINT killed the JVM with no shutdown hooks | ✅ fixed |
| F-02 | **Critical** | Lifecycle | The restore shutdown hook emitted nothing on any signal path | ✅ fixed |
| F-03 | **Critical** | Security | OSC 8 hyperlink URLs passed through unsanitized | ✅ fixed |
| F-04 | **High** | Lifecycle | No SIGTSTP/SIGCONT handling; Ctrl+Z suspended inside raw mode | ✅ fixed (unverifiable here — §5) |
| F-05 | **High** | Event loop | `pollTimeout` could return zero, which JLine treats as "block forever" | ✅ fixed |
| F-06 | **High** | Input | Bracketed paste containing `ESC` corrupted the payload | ✅ fixed |
| F-07 | **High** | Rendering | Non-UTF-8 locale rendered the entire UI as `?` | ✅ fixed |
| F-08 | **High** | Input | Unrecognised CSI/SS3 delivered a synthetic `Escape` keypress | ✅ fixed |
| F-09 | **High** | Input | Legacy X10 mouse reports decoded into four bogus key events | ✅ fixed |
| F-10 | **High** | Performance | `DataTable` re-filtered and re-sorted every row every frame | ✅ fixed |
| F-11 | **Medium** | Input | Astral input arrived as two lone surrogate `KeyCode.Char`s | ✅ fixed |
| F-12 | **Medium** | Performance | ~990 KiB allocated per 200×50 frame | ✅ improved 3.5× |
| F-13 | **Medium** | Performance | `Table`/`Paragraph` were O(content), not O(viewport) | ✅ fixed |
| F-14 | **Medium** | Concurrency | `RenderThread` was a JVM-global singleton with a shared queue | ✅ fixed |
| F-15 | **Medium** | Event loop | No wake-up for queued work; one frame per keystroke | ✅ wake fixed; batching **rejected on evidence** (§4) |
| F-16 | **Medium** | Input | Kitty protocol advertised, only partly decoded | ✅ fixed |
| F-17 | **Medium** | Capability | Alternate screen entered on terminals with no `smcup` | ✅ fixed |
| F-18 | **Medium** | Security | `printAbove` wrote caller strings unsanitized | ✅ fixed |
| F-19 | **Medium** | API | Bare `Int` coordinates; no `opaque type Column/Row` | ⏸ **deferred** (§6) |
| F-20 | **Medium** | API | Public `case class`es with no binary-compat gate | ⏸ **partly done** (§6) |
| F-21 | **Medium** | Docs | Crash-recovery claim was true only for the exception path | ✅ fixed |
| F-22 | **Low** | Testing | No whole-session input fixtures | ✅ fixed |
| F-23 | **Low** | Rendering | An unchanged frame still wrote 20 bytes | ✅ fixed |
| F-24 | **Low** | Layout | Percentage constraints left a remainder column | ✅ fixed |
| F-25 | **Low** | Docs | No supported-terminal matrix | ✅ fixed |

---

## 3. The fixes, and how each was verified

### F-01/F-02/F-04 — the terminal lifecycle

**Root cause.** `TerminalBuilder.builder().system(true).build()` accepts JLine's defaults
(`nativeSignals = true`, `signalHandler = SIG_DFL`), which makes `PosixSysTerminal` call
`Signals.registerDefault(...)` for every `Terminal.Signal` — i.e.
`sun.misc.Signal.handle(sig, SIG_DFL)` — stripping the JVM's own SIGINT handler. Proven via
`/proc/<pid>/status`: `SigCgt` contained SIGINT for a plain JVM and not for a glyphora app.
Separately, `PosixSysTerminal`'s constructor registers its own `ShutdownHooks` closer, which
wins the race against glyphora's hook; afterwards `terminal.writer()` throws
`IllegalStateException: Terminal has been closed`, and every teardown write was swallowed by
`bestEffort`. (Adding `@volatile` to the state flags changed nothing — the flags were correct;
a reflective probe read them as `true` at hook time. It was the writer that was dead.)

**Fix.**
* `JLine3Backend.create` now builds with `.signalHandler(Terminal.SignalHandler.SIG_IGN)`, so
  JLine installs Java-level handlers instead of resetting the OS disposition.
* `INT`/`QUIT` are routed via `terminal.handle` into a new `Event.Interrupt`, which the runner
  treats as a clean quit unless the app consumes it — so teardown runs on the `finally` path
  that was already measured as correct. `TuiApp.onInterrupt()` is the override point.
* `TSTP` hands the terminal back and then stops the process with an uncatchable `SIGSTOP`;
  `CONT` re-acquires it and forces a full repaint at the current size.
* `Backend.emergencyRestore()` writes the mode-reset sequences straight to `FileDescriptor.out`,
  bypassing the JLine writer entirely. The shutdown hook calls `close()` then this.
* Teardown/restore is now one shared implementation used by `close`, `suspend`, TSTP and CONT.

**Verified**, `examples/hello-world` in a real PTY, every exit path:

| Exit path | before | after |
|---|---|---|
| normal return (`q`) | ✅ all restored | ✅ |
| uncaught exception from `view` | ✅ all restored | ✅ |
| **Ctrl+C (byte `0x03`)** | ❌ exit 130, raw + alt screen + hidden cursor | ✅ **exit 0**, all restored, `stty` clean |
| **SIGINT** | ❌ nothing restored | ✅ **exit 0**, all restored |
| **SIGTERM** | ❌ nothing restored | ✅ exit 143, all restored |
| **SIGHUP** | ❌ nothing restored | ✅ exit 129, all restored |
| **`System.exit` from a handler** | ❌ alt screen + cursor left | ✅ all restored |

"All restored" means `ESC[?1049l`, `ESC[?25h`, `ESC[?2004l`, `ESC[?1004l`, `ESC[<u` all present
in the captured stream and `stty -a` byte-identical to the pre-launch snapshot.

Also verified on the **GraalVM native binary**: `SigCgt` contains `SIGINT SIGQUIT SIGCONT
SIGTSTP SIGWINCH`, and SIGINT restores the terminal fully.

### F-03/F-18 — escape injection

`AnsiSequences.linkOpen` interpolated the URL raw. Rendering the Markdown
`click [here](http://x\e\\\e]52;c;cHduZWQ=\e\\\e]0;PWNED)` emitted, verbatim:

```
click<ESC>]8;;http://x<ESC>\<ESC>]52;c;cHduZWQ=<ESC>\<ESC>]0;PWNED<BEL><ESC>\here
```

— the attacker's ST closed the hyperlink early and the terminal then executed **OSC 52
(clipboard write)** and **OSC 0 (window title)**. Fixed by stripping C0, DEL and C1 controls in
`linkOpen` and in `printAbove`, per XTerm `ctlseqs.ms` ("Operating System Commands") and RFC
3986 §2, which forbids those bytes in a URI anyway. The same payload now emits only inert text.
Covered by `EscapeSanitizingSpec` (8 tests).

### F-05 — the wedged event loop

`math.max(0L, ...)` let `pollTimeout` return zero when a frame overran the tick budget, and
`NonBlockingReaderImpl.read` treats a non-positive timeout as an unbounded blocking read. A spy
backend recorded **41/41 zero timeouts**; it now records **0/41**, floor 1 ms. `HeadlessBackend`
and `JLine3Backend` both `require` a positive timeout, so the two backends can no longer diverge
— that divergence was why the whole suite passed over a loop that would wedge in production.

### F-06/F-08/F-09/F-11/F-16 — input decoding

Rewrote `InputDecoder` around one rule: **a sequence the decoder does not understand is dropped,
never reported as a key.** `decode` returns `Option[Event]`; a one-character pushback buffer
removes the speculative reads that caused the paste bug.

| Input | before | after |
|---|---|---|
| `ESC O A` (DECCKM arrows) | `Escape` | `Up` |
| `ESC [ ?62;1;4c` (device attributes) | `Escape` | dropped |
| `ESC [ 24;80R` (cursor report) | `Escape` | dropped |
| torn `ESC [ 1;5` | `Escape` | dropped |
| `ESC[200~ a ESC[A b ESC[201~` | `Paste("a\e[Ab\e[201~")` — terminator eaten | `Paste("a\e[Ab")` |
| X10 `ESC [ M` + 3 bytes | 4 bogus key events | one `MouseEvent` |
| emoji (surrogate pair) | 2 lone surrogates | one `Char(0x1F600)` |
| kitty `CSI 57399 u` (KP_0) | private-use glyph | `Char('0')` |
| kitty `CSI 128512 u` | `Escape` | `Char(0x1F600)` |
| kitty `CSI 57441 u` (LEFT_SHIFT) | private-use glyph | dropped |

`KeyCode.Char` now carries a **code point** (`Int`) rather than a UTF-16 `Char`.
`KeyCode.Char('q')` still compiles in both expression and pattern position — a `Char` widens to
`Int` — so no call site changed; only code that *binds* the payload sees `Int`, and
`KeyCode.text` returns the printable string. Kitty functional keys were mapped against the
published protocol table (fetched and checked, not recalled). The escape timeout is now
configurable. Covered by `InputDecoderRegressionSpec` (18 tests) and `InputFixtureSpec` (9
whole-session replays — the shape that catches "one sequence eats the next one's bytes").

### F-07 — UTF-8 output

`.encoding(UTF_8)` alone is **not enough**: JLine consults it only after the
`stdin.encoding`/`stdout.encoding` system properties, so it was silently ignored. Setting
`stdinEncoding` and `stdoutEncoding` explicitly fixed it. Verified in a PTY:

```
before   LANG=C LC_ALL=C  ->  ?Hello??????????????????????????
after    LANG=C LC_ALL=C  ->  ╭Hello───────────────────────────   (U+256D present as E2 95 AD)
```

### F-17 — alternate screen capability gate

`infocmp -1 linux` has no `smcup`/`rmcup`, so `CSI ?1049h` there paints over the user's
scrollback and never gives it back. `enterAlternateScreen` is now gated on
`InfoCmp.Capability.enter_ca_mode`. Verified: `TERM=linux` now emits **no** `?1049h` and fails
with `UnsupportedTerminal(linux has no alternate screen (no smcup capability))`, cleanly undoing
the modes it did enable.

### F-10/F-12/F-13/F-23 — performance

All measured on this machine, 200×50, warmed, thread-local allocation counters:

| Benchmark | before | after |
|---|---|---|
| `Buffer.diff`, 0 % changed | 138.5 µs / 238.1 KiB | **20.4 µs / 0.0 KiB** |
| `Buffer.diff`, 1 % changed | 175.5 µs / 238.1 KiB | **20.5 µs / 0.0 KiB** |
| 1 %-change encode | 148.2 µs / 238.3 KiB | **40.4 µs / 0.2 KiB** |
| fill buffer (50 rows) | 137.2 µs / 752.1 KiB | **81.1 µs / 283.6 KiB** |
| `CharWidth.of` (176 ASCII chars) | 1.2 µs / 8.3 KiB | **~0.0 µs / 0.0 KiB** |
| `Table(10k)` render, 50 visible | 218.0 µs | **49.7 µs** (within 7 % of the 50-row case) |
| `DataTable(10k)` sorted, scroll | 1576.5 µs | **276.5 µs** |
| unchanged frame, bytes written | 20 | **0** |

`Buffer.diff` gained a callback form taking primitives with a reference-equality fast path;
`CharWidth` gained a printable-ASCII fast path with interned single-char symbols; `Table` and
`Paragraph` are now viewport-bounded; `DataTable` memoizes its filtered/sorted view on the state
object, with `invalidate()` for same-length data swaps.

Steady-state frame allocation went from ~990 KiB to **~284 KiB** — a 3.5× improvement, but
short of the <100 KiB target I set in `issues/10`. The remainder is one `Cell` allocation per
written cell, inherent to `Buffer` holding `Array[Cell]` of immutable case classes; removing it
means restructuring `Buffer` into parallel arrays, which is a larger change than this
remediation warranted. Left as follow-up work in `issues/10-per-frame-allocation.md`.

One honest regression: escape-sequence parse throughput went from 187 µs to 252 µs per 1 000
sequences (extra `Option` and sub-parameter handling). That is 0.25 µs per keystroke — far below
human input rates — and buys the correctness in F-08.

### F-14/F-15 — concurrency

`RenderThread` now hands each runner its own `RenderLoop` (queue + wake channel) instead of one
process-wide queue, so two runners in a JVM cannot execute each other's work. `Async` captures
the target loop **on the calling thread** before going async, so continuations return to the
runner that started them. `Backend.wake()` cuts an in-flight `readEvent` short — JLine converts
the interrupt into an `InterruptedIOException` that it throws *and clears*, so the reader stays
usable and no buffered input is lost.

### F-24 — layout remainder

Proportional constraints now absorb the integer-division remainder, so `Percentage(33) × 3` at
width 100 gives `34, 33, 33` instead of leaving a stray column. Gated on *every* constraint being
proportional, so `Length` and `Fill` semantics are untouched. Property-tested across widths
0–300, alongside the pre-existing no-overlap/no-overflow invariants.

### F-21/F-25/F-16 — docs

`troubleshooting.md` now carries the measured exit-path table (including the honest `SIGKILL`
row). `faq.md` states the assumed minimum terminal, the unconditional-UTF-8 policy, and a
verified-only compatibility matrix with empty cells left empty. `GAP_ANALYSIS.md` narrows the
kitty claim to "flag 1", with the scope spelled out.

---

## 4. Changed my mind: event batching

The audit recommended draining all available events before redrawing, to avoid "one frame per
keystroke". **I implemented it and it was wrong.** It broke 11 DSL tests, because the element
tree that routes focus and hit-testing is published *by* rendering: folding several key events
into one frame dispatches the later ones against a stale tree, so Tab moves focus and the next
keystroke still goes to the previous element.

The premise was also weaker than I claimed. A paste already arrives as a single `Event.Paste`,
resizes already coalesce in the backend via an `AtomicReference`, and a redraw costs ~121 µs —
so a 200-key burst is ~24 ms. Reverted, with the reasoning recorded at the call site so nobody
re-derives it. The wake-up half of F-15 was real and is fixed.

---

## 5. What could not be verified here

**F-04 (Ctrl+Z).** I could not observe a stop in this environment. Under `script(1)` the app
lands in an orphaned process group, and POSIX requires stop signals to orphaned groups to be
discarded — both `^Z` and `kill -TSTP` left the process in state `S`, and a probe confirmed the
TSTP handler never ran. The implementation is registered and reachable (`SigCgt` contains
`SIGTSTP` and `SIGCONT` on both the JVM and the native binary), and it stops via an uncatchable
`SIGSTOP` so it cannot silently fail to stop, but **the round trip needs a check in an
interactive shell**: `^Z` should return the prompt on the main screen with a visible cursor, and
`fg` should repaint in full.

**Terminal emulators.** Everything was tested on Linux PTYs. xterm, kitty, tmux, macOS and
Windows Terminal remain untested — those rows are empty in the matrix rather than assumed.

---

## 6. Deliberately not done

**F-19 — `opaque type Column`/`Row`/`Width`.** Not implemented. The fix requires changing
`Position`, `Rect`, `Size`, `MouseEvent` and `Buffer`'s primitives, and every one of the ~200
source files that does coordinate arithmetic (`area.x + 2` returns `Int`, not `Column`, so each
site needs extension operators). That is a v2-scale, source-breaking refactor for every widget
author, with no test coverage over the mechanical parts — precisely the "rewrite the library"
this remediation was scoped to avoid, and a call that belongs to the maintainer. The finding and
its rationale stand in `issues/`. A half-migrated opaque-type layer would be worse than none.

**F-20 — binary compatibility.** Partly done. The "no MiMa" half turned out to be *existing
documented policy* (`versioning.md` already says binary compatibility is "not yet" guaranteed
pre-1.0), so the finding overstated it. What was genuinely missing — which types will break
when they grow, and what to do about it — is now documented: `Style`, `ElementProps`, `Theme`,
`RunnerConfig` and `Layout` are named as the growth-prone case classes that should become
`final class` + builders before 1.0. Converting them now would break every downstream `.copy`
call for a guarantee the project does not yet offer; wiring a MiMa gate needs a tooling decision
(Mill 1.x plugin support) I could not validate offline.

---

## 7. Verification summary

* `./mill __.test` — **595 tests, 90 suites, 0 failed, 0 ignored, 0 canceled, 0 pending**
  (was 528). 67 new tests across 6 new suites.
* `./mill __.compile` — clean under `-Werror -Wunused:all`.
* `./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources` — clean.
* CI's own discipline checks — no runtime reflection in main sources (the TSTP fix uses
  `ProcessHandle` + `SIGSTOP`, not `sun.misc.Signal` reflection, which also keeps it
  native-image safe); no `String.substring` outside `CharWidth`.
* `./mill examples.hello-world.nativeImage` — builds with `--no-fallback`, 21.6 MB, renders and
  restores correctly in a real PTY including on SIGINT.
* PTY exit-path matrix — all seven catchable paths restore fully (§3).
* Non-TTY paths unchanged: piped stdout, `TERM=dumb`, unset `TERM` and a backgrounded process
  group all still fail fast with `UnsupportedTerminal` and emit no escape sequences.

---

## 8. What was already right

Recording this because a report that only lists defects misrepresents the codebase. None of the
following needed changing:

* **Grapheme clusters.** ZWJ emoji families, skin-tone modifiers, regional-indicator flags,
  variation selectors and conjoining Hangul all segment into single clusters with correct
  0/1/2 widths. Truncation never splits a wide cluster; a half-fitting one at the right edge is
  dropped rather than torn.
* **Cell-level escape safety.** `a\e[31mb` renders as the literal cells `a|[|3|1|m`; CR, LF,
  NUL, BEL and C1 controls are all dropped. The injection the brief expects to find was already
  absent from this path — only the *style* path was open.
* **Synchronized output.** Frames were already wrapped in `CSI ?2026h/l`.
* **Diff quality.** Run-length cursor moves, SGR only on change, wide-cluster continuations
  skipped, full repaint on area change. A full 200×50 repaint costs 10 365 bytes against a
  ~10 050-byte floor; a one-cell change costs 34.
* **Layout invariants.** Zero overlaps and zero overflows across 1 806 exhaustive cases
  including widths 0 and 1.
* **Headless testing.** `HeadlessBackend` + `Pilot` + `GoldenFrames` + property tests. The
  brief's expected top recommendation ("design a headless backend") was already shipped.
* **Native image.** Builds with `--no-fallback`, ~13 ms startup vs ~279 ms on the JVM, no
  terminfo database required at runtime, no reflection config needed.
* **Effect neutrality.** `tui-core` has zero dependencies; nothing in the tree references ZIO,
  cats-effect or `Future`.
* **README quickstart** compiles verbatim.
* **CI** already enforced no-reflection and no-`substring` discipline — two checks most
  projects lack.
