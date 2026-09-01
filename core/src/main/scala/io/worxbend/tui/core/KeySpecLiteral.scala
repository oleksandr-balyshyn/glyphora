package io.worxbend.tui.core

import scala.quoted.{Expr, Quotes, Varargs}

/** A key spec checked when the code is compiled rather than when it runs.
  *
  * `key"ctrl+s"` is the same value `KeyEvent.parse("ctrl+s")` returns, except that a spec the parser rejects — a
  * misspelt modifier (`ctlr+s`), a key name that does not exist, or one of the Ctrl combinations no terminal can
  * deliver — stops the build with the parser's own message, instead of throwing the first time the binding is declared
  * or silently declaring a key that can never fire.
  *
  * Only a literal can be checked, because only a literal is known while the code is being compiled. An interpolation
  * with a `$` hole in it, or a spec read from a config file at run time, is a compile error here and must keep going
  * through [[KeyEvent.parse]], which reports the same problem as a `Left`.
  *
  * The result costs nothing at run time: the parse happens during compilation and what is left in the compiled code is
  * the constructor call for the event the spec named.
  */
extension (inline sc: StringContext)
  inline def key(inline args: Any*): KeyEvent = ${ KeySpecLiteral.expand('sc, 'args) }

/** The implementation behind the `key"…"` interpolator above.
  *
  * Public only because an inline method's body is expanded in whichever module *calls* it, so this object has to be
  * reachable from there. It is not an API: call the interpolator, or [[KeyEvent.parse]] for a spec that is not a
  * literal.
  */
object KeySpecLiteral:

  /** Turns the literal parts of a `key"…"` into a [[KeyEvent]] tree, or aborts the compilation with the reason. */
  def expand(sc: Expr[StringContext], args: Expr[Seq[Any]])(using q: Quotes): Expr[KeyEvent] =
    import q.reflect.report
    (sc.value, args) match
      case (Some(context), Varargs(Seq())) =>
        val spec = context.parts.mkString
        KeyEvent.parse(spec) match
          case Right(event)  => encode(event)
          case Left(problem) => report.errorAndAbort(s"bad key spec \"$spec\": $problem", sc)
      case (Some(_), _)                    =>
        report.errorAndAbort(
          "a key spec cannot interpolate a value, because only a literal can be checked while compiling; " +
            "use KeyEvent.parse for a spec built at run time",
          sc,
        )
      case (None, _)                       =>
        report.errorAndAbort("a key spec must be a string literal", sc)

  /** Rebuilds an already-parsed event as code, so the compiled program constructs it rather than parsing a string. */
  private def encode(event: KeyEvent)(using Quotes): Expr[KeyEvent] =
    val code          = encodeCode(event.code)
    // `KeyModifiers` is an opaque type over an `Int`, so its value cannot be lifted into code directly. The held
    // modifiers are ORed back together out of the public constants instead, which needs no access to the bitset.
    val modifiers     = KeyModifiers.Shift :: KeyModifiers.Ctrl :: KeyModifiers.Alt :: Nil
    val held          = modifiers.filter(event.modifiers.hasAny)
    val modifiersExpr = held.foldLeft('{ KeyModifiers.None }) { (accumulated, modifier) =>
      val one = encodeModifier(modifier)
      '{ $accumulated | $one }
    }
    '{ KeyEvent($code, $modifiersExpr) }

  private def encodeModifier(modifier: KeyModifiers)(using Quotes): Expr[KeyModifiers] =
    if modifier == KeyModifiers.Shift then '{ KeyModifiers.Shift }
    else if modifier == KeyModifiers.Ctrl then '{ KeyModifiers.Ctrl }
    else '{ KeyModifiers.Alt }

  /** One arm per [[KeyCode]] case, written as an exhaustive match so that a key code added later fails this file at
    * compile time rather than falling through to a wrong answer.
    */
  private def encodeCode(code: KeyCode)(using q: Quotes): Expr[KeyCode] =
    import q.reflect.report
    code match
      case KeyCode.Char(codePoint) => '{ KeyCode.Char(${ Expr(codePoint) }) }
      case KeyCode.F(n)            => '{ KeyCode.F(${ Expr(n) }) }
      // `MediaKey` is a plain enum with no parameters, so naming its case is enough to rebuild it. `valueOf` is the
      // compiler-generated lookup over that enum's own cases, not a reflective one.
      case KeyCode.Media(media)    => '{ KeyCode.Media(MediaKey.valueOf(${ Expr(media.toString) })) }
      case KeyCode.Modifier(_)     =>
        // no spec names a bare modifier key on purpose (see `KeyEvent.keyCodeFor`), so the parser cannot return one
        // and reaching this arm would mean the two files had drifted apart
        report.errorAndAbort("a bare modifier key cannot be named by a key spec")
      case KeyCode.Enter           => '{ KeyCode.Enter }
      case KeyCode.Escape          => '{ KeyCode.Escape }
      case KeyCode.Backspace       => '{ KeyCode.Backspace }
      case KeyCode.Tab             => '{ KeyCode.Tab }
      case KeyCode.Delete          => '{ KeyCode.Delete }
      case KeyCode.Insert          => '{ KeyCode.Insert }
      case KeyCode.Home            => '{ KeyCode.Home }
      case KeyCode.End             => '{ KeyCode.End }
      case KeyCode.PageUp          => '{ KeyCode.PageUp }
      case KeyCode.PageDown        => '{ KeyCode.PageDown }
      case KeyCode.Up              => '{ KeyCode.Up }
      case KeyCode.Down            => '{ KeyCode.Down }
      case KeyCode.Left            => '{ KeyCode.Left }
      case KeyCode.Right           => '{ KeyCode.Right }
      case KeyCode.CapsLock        => '{ KeyCode.CapsLock }
      case KeyCode.ScrollLock      => '{ KeyCode.ScrollLock }
      case KeyCode.NumLock         => '{ KeyCode.NumLock }
      case KeyCode.PrintScreen     => '{ KeyCode.PrintScreen }
      case KeyCode.Pause           => '{ KeyCode.Pause }
      case KeyCode.Menu            => '{ KeyCode.Menu }
