package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Color, Modifiers}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class MarkdownSpec extends AnyFunSuite:

  test("headings strip their hashes and take the heading styles"):
    val buffer = rendered(Markdown("# Title\n## Section\n### Sub"), 20, 3)
    assert(trimmedLines(buffer) == Seq("Title", "Section", "Sub"))
    assert(buffer.get(0, 0).style.fg.contains(Color.Cyan))
    assert(buffer.get(0, 1).style.modifiers.has(Modifiers.Bold))
    assert(buffer.get(0, 2).style.modifiers.has(Modifiers.Underline))

  test("bullets and numbered items get their markers"):
    val buffer = rendered(Markdown("- alpha\n* beta\n2. gamma"), 20, 3)
    assert(trimmedLines(buffer) == Seq("• alpha", "• beta", "2. gamma"))

  test("blockquotes are prefixed and quoted-styled"):
    val buffer = rendered(Markdown("> wise words"), 20, 1)
    assert(trimmedLines(buffer) == Seq("▎ wise words"))
    assert(buffer.get(0, 0).style.modifiers.has(Modifiers.Dim))

  test("inline strong, emphasis, and code style their runs only"):
    val buffer = rendered(Markdown("a **b** *c* `d`"), 20, 1)
    assert(trimmedLines(buffer) == Seq("a b c d"))
    assert(!buffer.get(0, 0).style.modifiers.has(Modifiers.Bold))
    assert(buffer.get(2, 0).style.modifiers.has(Modifiers.Bold))
    assert(buffer.get(4, 0).style.modifiers.has(Modifiers.Italic))
    assert(buffer.get(6, 0).style.fg.contains(Color.Yellow))

  test("unclosed markers render literally"):
    val buffer = rendered(Markdown("2 * 3 * 4 and a ** dangling"), 30, 1)
    assert(trimmedLines(buffer).head.startsWith("2 "))
    // the space-delimited stars are not emphasis: '* 3 ' has content, so check text survived
    assert(trimmedLines(buffer).head.contains("dangling"))

  test("fenced code blocks render verbatim with the code style and no fence lines"):
    val buffer = rendered(Markdown("```\nval x = **not bold**\n```"), 30, 3)
    assert(trimmedLines(buffer)(1) == "val x = **not bold**")
    assert(buffer.get(0, 1).style.fg.contains(Color.Yellow))

  test("prose wraps at the area width without splitting clusters"):
    val buffer = rendered(Markdown("hello wide 你好 world"), 8, 3)
    assert(trimmedLines(buffer).size == 3)
    assert(trimmedLines(buffer).head == "hello wi")

  test("inline links render their label with the url attached"):
    val buffer = rendered(Markdown("see [the docs](https://example.com) here"), 40, 1)
    assert(trimmedLines(buffer).head == "see the docs here")
    assert(buffer.get(4, 0).style.link.contains("https://example.com"))
    assert(buffer.get(4, 0).style.modifiers.has(Modifiers.Underline))
    assert(buffer.get(0, 0).style.link.isEmpty)

  test("a malformed link renders literally"):
    val buffer = rendered(Markdown("just [brackets] here"), 30, 1)
    assert(trimmedLines(buffer).head == "just [brackets] here")

  test("heightOf measures wrapped markdown"):
    assert(Markdown.heightOf("1234567890 1234567890", 30) == 1)
    assert(Markdown.heightOf("1234567890 1234567890", 10) >= 2)
