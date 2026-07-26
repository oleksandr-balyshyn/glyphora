# A paste containing ESC corrupts the payload and swallows the terminator

**Labels:** `bug`, `high`, `input`, `tui-terminal`

## Problem

`terminal/src/main/scala/io/worxbend/tui/terminal/InputDecoder.scala:105-118`:

```scala
else if c == 0x1b then
  val tail       = Array(read(...), read(...), read(...), read(...), read(...))
  val terminator = tail.sameElements(Array('['.toInt, '2'.toInt, '0'.toInt, '1'.toInt, '~'.toInt))
  if terminator then done = true
  else
    content.append(0x1b.toChar)
    tail.filter(_ >= 0).foreach(t => content.append(t.toChar))
```

Feeding `ESC[200~ a ESC[A b ESC[201~` decodes to:

```
Paste(a<ESC>[Ab<ESC>[201~)
```

The five bytes consumed speculatively after the inner `ESC` are `[`, `A`, `b`, `ESC`, `[` —
which includes the *real terminator's* leading `ESC [`. The mismatch appends all five, the scan
resumes misaligned, `201~` ends up inside the payload, and the paste only terminates on the
200 ms `PasteTimeoutMillis` fallback.

Any five-byte lookahead that does not push back on mismatch is unsound: the byte preceding a
real terminator is arbitrary. And bracketed-paste payloads are not guaranteed control-free —
XTerm's `allowPasteControls` is a per-terminal policy (`ctlseqs.ms`, DEC private mode 2004),
and kitty, WezTerm and tmux forward more than xterm's default does.

## Proposal

Never speculate; accumulate and test the tail.

```scala
private def decodePaste(): Event =
  val content    = StringBuilder()
  val terminator = s"$Esc[201~"   // "[201~"
  var done       = false
  while !done && content.length < PasteLimit do
    val c = read(PasteTimeoutMillis)
    if c < 0 then done = true
    else
      content.append(c.toChar)
      if content.endsWith(terminator) then
        content.setLength(content.length - terminator.length)
        done = true
  Event.Paste(content.result())
```

`StringBuilder.endsWith` is O(6) per character; at the 1 MiB `PasteLimit` that is negligible
against the per-character `read` already in the loop.

While here, consider whether `PasteLimit` silently truncating a large paste should instead
emit multiple `Event.Paste`s — today a 2 MiB paste loses its tail with no signal.

## Acceptance criteria

- [ ] `ESC[200~ a ESC[A b ESC[201~` decodes to exactly `Paste("a[Ab")`
- [ ] The following `decode` call returns `None`, not trailing garbage
- [ ] A paste containing embedded newlines round-trips unchanged
- [ ] A paste whose text contains the literal string `[201~` (no ESC) round-trips unchanged
- [ ] An unterminated paste still ends on `PasteTimeoutMillis` with the bytes received so far
