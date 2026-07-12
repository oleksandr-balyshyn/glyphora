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
