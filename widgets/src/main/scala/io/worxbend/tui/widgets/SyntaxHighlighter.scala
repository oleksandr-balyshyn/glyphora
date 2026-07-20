package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Color, Line, Span, Style, Text}

/** Styles for each token kind a [[SyntaxHighlighter]] emits; override entries to re-theme. */
final case class SyntaxTheme(
    keyword: Style = Style.Default.withFg(Color.Magenta),
    string: Style = Style.Default.withFg(Color.Green),
    number: Style = Style.Default.withFg(Color.Yellow),
    comment: Style = Style.Default.dim.italic,
    function: Style = Style.Default.withFg(Color.Cyan),
    variable: Style = Style.Default.withFg(Color.Blue),
    default: Style = Style.Default,
)

/** A language the highlighter understands. Highlighting is line-oriented (block comments and triple-quoted strings that
  * span lines fall back to plain text), which is the right trade-off for the code snippets in help screens, READMEs and
  * Markdown fences rather than a full editor.
  */
enum Language:
  case Scala, Json, Bash, Generic

object Language:
  /** Resolves a fence info-string / name (case-insensitive) to a [[Language]]; unknown names map to [[Generic]]. */
  def of(name: String): Language =
    name.trim.toLowerCase match
      case "scala" | "sc" | "sbt"                      => Scala
      case "json"                                      => Json
      case "bash" | "sh" | "shell" | "zsh" | "console" => Bash
      case _                                           => Generic

/** A pragmatic, dependency-free syntax highlighter: a per-language scanner turns a line of code into styled [[Span]]s.
  * Recognises line comments, single-line strings, numbers, per-language keywords, `name(` call sites, and `$variables`
  * (shell). Backend-agnostic and unit-tested against the produced spans.
  */
object SyntaxHighlighter:

  /** Highlights multi-line `code` as a [[Text]] (one [[Line]] per source line). */
  def highlight(code: String, language: Language, theme: SyntaxTheme = SyntaxTheme()): Text =
    Text(code.split("\n", -1).toIndexedSeq.map(line => highlightLine(line, language, theme)))

  /** Highlights a single line of `code`. */
  def highlightLine(line: String, language: Language, theme: SyntaxTheme = SyntaxTheme()): Line =
    val spec  = specFor(language)
    val spans = Seq.newBuilder[Span]
    val plain = StringBuilder()

    def flush(): Unit =
      if plain.nonEmpty then
        spans += Span(plain.result(), theme.default)
        plain.clear()

    def emit(content: String, style: Style): Unit =
      flush()
      spans += Span(content, style)

    var i = 0
    val n = line.length
    while i < n do
      val c = line.charAt(i)
      spec.lineComment match
        case Some(marker) if line.startsWith(marker, i) =>
          emit(line.drop(i), theme.comment)
          i = n
        case _                                          =>
          if spec.stringDelims.contains(c) then
            val end = scanString(line, i, c)
            emit(line.slice(i, end), theme.string)
            i = end
          else if c == '$' && spec.shellVariables && i + 1 < n && isIdentChar(line.charAt(i + 1)) then
            val end = scanWhile(line, i + 1, isIdentChar)
            emit(line.slice(i, end), theme.variable)
            i = end
          else if c.isDigit && !isIdentChar(prevChar(line, i)) then
            val end = scanWhile(line, i, ch => ch.isDigit || ch == '.' || ch == 'x' || ch == 'e' || ch == '_')
            emit(line.slice(i, end), theme.number)
            i = end
          else if isIdentStart(c) then
            val end    = scanWhile(line, i, isIdentChar)
            val word   = line.slice(i, end)
            val isCall = end < n && line.charAt(end) == '('
            if spec.keywords.contains(word) then emit(word, theme.keyword)
            else if isCall then emit(word, theme.function)
            else plain ++= word
            i = end
          else
            plain += c
            i += 1
    flush()
    Line(spans.result())

  private def prevChar(line: String, i: Int): Char = if i > 0 then line.charAt(i - 1) else ' '

  private def isIdentStart(c: Char): Boolean = c.isLetter || c == '_'
  private def isIdentChar(c: Char): Boolean  = c.isLetterOrDigit || c == '_'

  private def scanWhile(line: String, start: Int, pred: Char => Boolean): Int =
    var i = start
    while i < line.length && pred(line.charAt(i)) do i += 1
    i

  /** Consumes a string literal opened by `delim` at `start`, honouring backslash escapes; returns the index just past
    * the closing delimiter (or end of line if unterminated).
    */
  private def scanString(line: String, start: Int, delim: Char): Int =
    var i = start + 1
    while i < line.length do
      val c = line.charAt(i)
      if c == '\\' then i += 2
      else if c == delim then return i + 1
      else i += 1
    line.length

  private final case class LanguageSpec(
      keywords: Set[String],
      lineComment: Option[String],
      stringDelims: Set[Char],
      shellVariables: Boolean = false,
  )

  private def specFor(language: Language): LanguageSpec =
    language match
      case Language.Scala   => scalaSpec
      case Language.Json    => jsonSpec
      case Language.Bash    => bashSpec
      case Language.Generic => genericSpec

  private val scalaSpec = LanguageSpec(
    keywords = Set(
      "def",
      "val",
      "var",
      "class",
      "object",
      "trait",
      "extends",
      "with",
      "given",
      "using",
      "import",
      "package",
      "if",
      "else",
      "match",
      "case",
      "for",
      "while",
      "do",
      "yield",
      "new",
      "type",
      "sealed",
      "final",
      "override",
      "private",
      "protected",
      "implicit",
      "lazy",
      "return",
      "true",
      "false",
      "null",
      "this",
      "super",
      "enum",
      "then",
      "abstract",
      "inline",
      "throw",
      "try",
      "catch",
      "finally",
    ),
    lineComment = Some("//"),
    stringDelims = Set('"'),
  )

  private val jsonSpec = LanguageSpec(
    keywords = Set("true", "false", "null"),
    lineComment = None,
    stringDelims = Set('"'),
  )

  private val bashSpec = LanguageSpec(
    keywords = Set(
      "if",
      "then",
      "fi",
      "else",
      "elif",
      "for",
      "while",
      "do",
      "done",
      "case",
      "esac",
      "function",
      "in",
      "return",
      "export",
      "local",
      "echo",
      "cd",
      "source",
    ),
    lineComment = Some("#"),
    stringDelims = Set('"', '\''),
    shellVariables = true,
  )

  private val genericSpec = LanguageSpec(
    keywords = Set.empty,
    lineComment = None,
    stringDelims = Set('"', '\''),
  )
