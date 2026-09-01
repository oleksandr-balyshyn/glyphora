package io.worxbend.tui.core

import scala.quoted.{Expr, Quotes, Varargs}

/** The `hex"#ff8800"` literal: a hex colour checked by the compiler instead of at run time.
  *
  * [[Color.hex]] has to return an `Option`, because the string it is handed can come from a configuration file or a
  * command-line flag and may well be malformed. But the overwhelmingly common case is a literal the author typed, and
  * there the `Option` is pure friction: every call site ends in `.get`, `.getOrElse(Color.Red)` or a pattern match that
  * can never take its other branch, and a typo is still only found when that line of code happens to run.
  *
  * This interpolator moves both problems to compile time. `hex"#ff8800"` is checked while the file is compiled — a
  * malformed literal is a compile error naming the offending string — and it expands to the constant
  * `Color.Rgb(255, 136, 0)`, so there is no `Option` to unwrap and no parsing left to do when the program runs.
  *
  * {{{
  * import io.worxbend.tui.core.hex
  *
  * val brand = hex"#c83232"     // Color.Rgb(200, 50, 50), checked by the compiler
  * val short = hex"#f80"        // the three-digit form expands the same way Color.hex does
  * // val oops = hex"#ff88"     // does not compile: "#ff88" is not a 3- or 6-digit hex colour
  * }}}
  *
  * Accepts exactly what [[Color.hex]] accepts: `#rrggbb`, `rrggbb`, `#rgb` or `rgb`, case-insensitive, the leading `#`
  * optional. Interpolated values are rejected, because a value that is not known until the program runs cannot be
  * checked while it is compiled — reach for [[Color.hex]] there and handle the `None` the way the rest of your input
  * handling does.
  *
  * This is compile-time expansion, not runtime reflection: nothing here survives into the produced class files beyond
  * the `Color.Rgb` constant, so native images stay free of reflection configuration.
  */
extension (inline context: StringContext)
  inline def hex(inline args: Any*): Color = ${ ColorLiteral.hexExpr('context, 'args) }

/** The compile-time half of the `hex` interpolator above. Not part of the public surface — the interpolator is. */
private object ColorLiteral:

  /** Checks the literal and produces the `Color.Rgb(r, g, b)` expression it stands for, or reports a compile error.
    *
    * The parsing itself is [[Color.hex]] — one definition of what a hex colour is, running at compile time here and at
    * run time there, so the two can never disagree about which strings are valid.
    */
  def hexExpr(context: Expr[StringContext], args: Expr[Seq[Any]])(using quotes: Quotes): Expr[Color] =
    import quotes.reflect.report
    (context.value, args) match
      case (Some(parts), Varargs(Seq())) if parts.parts.sizeIs == 1 =>
        Color.hex(parts.parts.head) match
          case Some(Color.Rgb(r, g, b)) => '{ Color.Rgb(${ Expr(r) }, ${ Expr(g) }, ${ Expr(b) }) }
          case _                        =>
            report.errorAndAbort(
              s"""hex"${parts.parts.head}" is not a colour: expected 3 or 6 hex digits, with an optional leading '#'."""
            )
      case (Some(_), _)                                             =>
        report.errorAndAbort(
          "hex\"…\" takes a literal only. A value that is not known until the program runs cannot be checked while it "
            + "is compiled — use Color.hex(value) and handle the None it returns for a malformed string."
        )
      case _                                                        =>
        report.errorAndAbort("hex\"…\" needs a string literal known at compile time.")
