package io.worxbend.tui.widgets

import org.scalatest.funsuite.AnyFunSuite

final class SyntaxHighlighterSpec extends AnyFunSuite:

  private val theme = SyntaxTheme()

  /** The (content, style) pair for the span that covers `substring`. */
  private def styleOf(line: io.worxbend.tui.core.Line, substring: String): io.worxbend.tui.core.Style =
    line.spans.find(_.content == substring).map(_.style).getOrElse(fail(s"no span '$substring' in $line"))

  test("Language.of maps names case-insensitively and falls back to Generic"):
    assert(Language.of("Scala") == Language.Scala)
    assert(Language.of("SH") == Language.Bash)
    assert(Language.of("json") == Language.Json)
    assert(Language.of("rust") == Language.Generic)
    assert(Language.of("") == Language.Generic)

  test("Scala: keywords, strings and call sites get distinct styles"):
    val line = SyntaxHighlighter.highlightLine("""val name = greet("hi")""", Language.Scala, theme)
    assert(styleOf(line, "val") == theme.keyword)
    assert(styleOf(line, "\"hi\"") == theme.string)
    assert(styleOf(line, "greet") == theme.function)

  test("Scala: a line comment styles the rest of the line"):
    val line    = SyntaxHighlighter.highlightLine("val x = 1 // note", Language.Scala, theme)
    val comment = line.spans.find(_.content.contains("// note")).getOrElse(fail("no comment span"))
    assert(comment.style == theme.comment)
    assert(styleOf(line, "1") == theme.number)

  test("numbers are only recognised at token boundaries, not inside identifiers"):
    val line = SyntaxHighlighter.highlightLine("val user2 = 42", Language.Scala, theme)
    assert(!line.spans.exists(s => s.content == "2" && s.style == theme.number)) // user2 stays one identifier
    assert(styleOf(line, "42") == theme.number)

  test("Bash: shell variables and single-quoted strings are highlighted"):
    val line = SyntaxHighlighter.highlightLine("echo '$HOME' $USER # done", Language.Bash, theme)
    assert(styleOf(line, "echo") == theme.keyword)
    assert(styleOf(line, "'$HOME'") == theme.string) // inside quotes stays a string
    assert(styleOf(line, "$USER") == theme.variable)

  test("JSON: literals and strings, no keyword coloring for bare words"):
    val line = SyntaxHighlighter.highlightLine("""{"on": true, "n": 3}""", Language.Json, theme)
    assert(styleOf(line, "true") == theme.keyword)
    assert(styleOf(line, "\"on\"") == theme.string)
    assert(styleOf(line, "3") == theme.number)

  test("Generic: strings and numbers only, everything else plain"):
    val line = SyntaxHighlighter.highlightLine("thing = \"x\" 7", Language.Generic, theme)
    assert(styleOf(line, "\"x\"") == theme.string)
    assert(styleOf(line, "7") == theme.number)
    assert(styleOf(line, "thing = ") == theme.default) // no keyword styling in generic mode
