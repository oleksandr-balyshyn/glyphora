package io.worxbend.tui.terminal

import io.worxbend.tui.core.Style

import org.scalatest.funsuite.AnyFunSuite

final class LinkSequenceSpec extends AnyFunSuite:

  private val Esc = ""

  test("linkOpen and LinkClose emit OSC 8 sequences"):
    assert(AnsiSequences.linkOpen("https://example.com") == s"$Esc]8;;https://example.com$Esc\\")
    assert(AnsiSequences.LinkClose == s"$Esc]8;;$Esc\\")

  test("Style.withLink attaches a target and patch overlays it"):
    val linked = Style.Default.withLink("https://a")
    assert(linked.link.contains("https://a"))
    assert(Style.Default.patch(linked).link.contains("https://a"))
    assert(linked.patch(Style.Default).link.contains("https://a"))

  test("clipboardCopy emits a base64-encoded OSC 52 sequence"):
    val sequence = AnsiSequences.clipboardCopy("hi ✓")
    assert(sequence.startsWith(s"$Esc]52;c;"))
    assert(sequence.endsWith(s"$Esc\\"))
    val payload  = sequence.stripPrefix(s"$Esc]52;c;").stripSuffix(s"$Esc\\")
    val decoded  = String(java.util.Base64.getDecoder.decode(payload), java.nio.charset.StandardCharsets.UTF_8)
    assert(decoded == "hi ✓")

  test("setTitle emits an OSC 2 sequence terminated by ST"):
    assert(AnsiSequences.setTitle("build ok") == s"$Esc]2;build ok$Esc\\")

  test("the XTerm title stack push and pop are CSI 22;2t and CSI 23;2t"):
    assert(AnsiSequences.PushTitle == s"$Esc[22;2t")
    assert(AnsiSequences.PopTitle == s"$Esc[23;2t")

  test("the emergency restore string does not pop the title stack"):
    // RestoreAll runs from a shutdown hook that cannot know whether a title was ever pushed, and a pop is not
    // idempotent: an unmatched one would discard an entry that belonged to the shell, not to this app
    assert(!AnsiSequences.RestoreAll.contains(AnsiSequences.PopTitle))

  test("the cursor save and restore sequences are the two-byte DECSC and DECRC forms"):
    // not `CSI s` / `CSI u`: `CSI s` is also "set left and right margin" on terminals with margin support enabled,
    // so the same bytes would mean two different things depending on a mode this library never sets
    assert(AnsiSequences.SaveCursor == s"${Esc}7")
    assert(AnsiSequences.RestoreCursor == s"${Esc}8")

  test("the emergency restore string does not restore the cursor"):
    // a terminal whose save register was never written restores to the home position rather than doing nothing, so an
    // unmatched DECRC from the shutdown hook would move a shell cursor that this app never touched
    assert(!AnsiSequences.RestoreAll.contains(AnsiSequences.RestoreCursor))
