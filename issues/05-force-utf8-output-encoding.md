# The whole UI renders as `?` under a non-UTF-8 locale

**Labels:** `bug`, `high`, `rendering`, `tui-terminal`

## Problem

JLine wraps the pty in a locale-derived encoder
(`org/jline/terminal/impl/PosixSysTerminal.java:90`):

```java
this.writer = new PrintWriter(new OutputStreamWriter(output, outputEncoding()));
```

and `JLine3Backend.create` never overrides it
(`terminal/src/main/scala/io/worxbend/tui/terminal/JLine3Backend.scala:196`):

```scala
val terminal = TerminalBuilder.builder().system(true).build()
```

Same binary, same PTY, two locales:

```
LANG=C LC_ALL=C     ->  ?Hello??????????????????????????????????????
LANG=en_US.UTF-8    ->  ╭Hello──────────────────────────────────────
```

Every border, rule, gauge, sparkline, spinner and box glyph in `tui-widgets` is non-ASCII, so
under a POSIX locale — the default in many container images, cron environments and CI runners
— glyphora produces an unreadable screen with no diagnostic.

The process locale is the wrong source of truth: terminal emulators overwhelmingly decode
UTF-8 regardless of `LANG`, and every comparator (crossterm, Bubble Tea, notcurses) writes
UTF-8 bytes unconditionally.

## Proposal

Force the output encoding at construction:

```scala
val terminal = TerminalBuilder.builder()
  .system(true)
  .encoding(java.nio.charset.StandardCharsets.UTF_8)
  .build()
```

(Same edit as issue #01 — land them together.)

Document the assumption in `website/docs/faq.md`: glyphora writes UTF-8 unconditionally and
expects a UTF-8-capable terminal; there is no ASCII fallback border set.

Optionally offer an opt-out for genuinely ASCII-only terminals by way of an ASCII border
preset in `Borders`, rather than by changing the encoding.

## Acceptance criteria

- [ ] `LC_ALL=C` in a PTY: `U+256D` (`╭`) appears in the captured stream as bytes `E2 95 AD`
- [ ] `LANG` unset behaves identically
- [ ] `terminal.encoding()` is asserted to be UTF-8 in a backend test
- [ ] The UTF-8 requirement is stated in the FAQ
