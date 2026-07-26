# Escape injection through OSC 8 hyperlink URLs

**Labels:** `security`, `critical`, `tui-terminal`, `tui-widgets`

## Problem

`AnsiSequences.linkOpen` interpolates the URL into an OSC string with no filtering
(`terminal/src/main/scala/io/worxbend/tui/terminal/AnsiSequences.scala:34`):

```scala
def linkOpen(url: String): String = s"$Esc]8;;$url$Esc\\"
```

and the URL comes straight from caller-supplied text — including arbitrary Markdown
(`widgets/src/main/scala/io/worxbend/tui/widgets/Markdown.scala:122-123`):

```scala
val url = text.slice(close + 2, end)
...
else Some((Span(label, theme.link.withLink(url)), end + 1 - index))
```

Rendering the Markdown source

```
click [here](http://x\e\\\e]52;c;cHduZWQ=\e\\\e]0;PWNED)
```

through `Markdown` and the backend's own emitter produces, verbatim:

```
click<ESC>]8;;http://x<ESC>\<ESC>]52;c;cHduZWQ=<ESC>\<ESC>]0;PWNED<BEL><ESC>\here
```

The attacker's `ESC \` (ST) closes the hyperlink OSC early. What follows is executed by the
terminal as **OSC 52 — write the system clipboard** (`cHduZWQ=` decodes to `pwned`) and
**OSC 0 — set the window title**. Per XTerm `ctlseqs.ms` an OSC string terminates at BEL
(`0x07`) or ST (`ESC \` / `0x9C`), so any payload containing either escapes the string context.

`Style.withLink` is also reachable from `Link` (`widgets/.../LinkWidget.scala:15`) and any
user code. Note that the *cell* path is already defended correctly: `CharWidth` gives C0/C1
controls width 0 (`CharWidth.scala:92`) and `setString` drops zero-width clusters
(`Buffer.scala:33`), so `a\e[31mb` renders as the literal cells `a|[|3|1|m`. The style path
bypasses that defence entirely.

The same hole exists in `JLine3Backend.printAbove` (`:156-159`), which writes caller strings
to the terminal unfiltered.

## Proposal

Filter where the sequence is encoded, so every caller is covered without touching widgets:

```scala
// AnsiSequences.scala
def linkOpen(url: String): String =
  val safe = url.filter(c => c >= 0x20 && c != 0x7f && !(c >= 0x80 && c <= 0x9f))
  s"$Esc]8;;$safe$Esc\\"
```

and in `JLine3Backend`:

```scala
private def sanitize(line: String): String =
  line.filter(c => c == '\t' || (c >= 0x20 && c != 0x7f && !(c >= 0x80 && c <= 0x9f)))
```

applied in `printAbove` before each `write`.

Dropping rather than escaping is correct here: a URL cannot legally contain C0/C1 controls
(RFC 3986 §2), so nothing valid is lost.

## Acceptance criteria

- [ ] `AnsiSequences.linkOpen("a\\b")` contains exactly one `ESC` byte (the terminator) and no BEL
- [ ] A golden test renders hostile Markdown and asserts the emitted stream contains no `ESC]52` and no `ESC]0`
- [ ] `printAbove(Seq("a[2Jb"))` writes no `ESC` byte
- [ ] `Link(label, url)` with a hostile `url` is covered by the same assertion
- [ ] `website/docs/widgets.md` notes that link targets are sanitized
