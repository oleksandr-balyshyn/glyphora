package io.worxbend.tui.dsl

import org.scalatest.funsuite.AnyFunSuite

/** [[Theme]] states one invariant its type cannot enforce: `markdown.syntax` and `syntax` are the same palette, so
  * fenced code inside a `markdown` element and a standalone `syntaxHighlight` element colour a keyword identically.
  *
  * Nothing stops a theme from writing the two independently — `Dark` did, and agreed with itself only because
  * `MarkdownTheme`'s `syntax` parameter happens to default to `SyntaxTheme()`. Retuning the dark highlighter would have
  * moved one and silently left the other on the widget default. This test is the guard the type system is not.
  */
final class ThemePalettesSpec extends AnyFunSuite:

  test("every built-in theme highlights fenced code the way it highlights a standalone snippet"):
    Seq(Theme.Dark, Theme.Light, Theme.HighContrast).foreach: theme =>
      assert(theme.markdown.syntax == theme.syntax, s"${theme.name} has two different code palettes")
