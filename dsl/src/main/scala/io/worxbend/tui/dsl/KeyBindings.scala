package io.worxbend.tui.dsl

import io.worxbend.tui.core.{KeyCode, KeyEvent, KeyModifiers}

/** One declared application key: the trigger, the short label shown in status-bar hints, a description for the help
  * overlay, and the action.
  */
final case class KeyBinding(
    trigger: KeyEvent,
    label: String,
    description: String,
    action: () => Unit,
    showInHints: Boolean = true,
)

/** Declares one binding from a key spec string: `"q"`, `"ctrl+s"`, `"shift+tab"`, `"esc"`, `"f2"`, `"up"`… A malformed
  * spec is a programmer error and throws at construction (bindings are static app declarations).
  */
def binding(key: String, description: String)(action: => Unit): KeyBinding =
  KeyBindings.parseKey(key) match
    case Right(trigger) => KeyBinding(trigger, key, description, () => action)
    case Left(problem)  => throw IllegalArgumentException(s"bad key spec '$key': $problem")

/** The application's declared keys (bubbles' `key`+`help` pattern): one declaration drives dispatch, the status-bar
  * hints, and the help overlay. `TuiApp` consults these for events no element consumed.
  */
final class KeyBindings private (val bindings: Seq[KeyBinding]):

  /** Runs the first matching binding; `true` if one fired. */
  def handle(event: KeyEvent): Boolean =
    bindings.find(_.trigger == event).exists { bound =>
      bound.action()
      true
    }

  /** `(label, description)` pairs for the status bar, in declaration order. */
  def hints: Seq[(String, String)] =
    bindings.filter(_.showInHints).map(bound => (bound.label, bound.description))

  def ++(other: KeyBindings): KeyBindings = new KeyBindings(bindings ++ other.bindings)

object KeyBindings:

  val empty: KeyBindings = new KeyBindings(Seq.empty)

  def apply(declared: KeyBinding*): KeyBindings = new KeyBindings(declared)

  /** Parses `"ctrl+shift+x"`-style specs: any of `ctrl`/`alt`/`shift` prefixes plus a key name. */
  def parseKey(spec: String): Either[String, KeyEvent] =
    val parts = spec.trim.toLowerCase.split('+').toList.filter(_.nonEmpty)
    parts match
      case Nil => Left("empty spec")
      case _   =>
        val (modifierNames, keyNames) = parts.partition(ModifierNames.contains)
        keyNames match
          case Seq(keyName) =>
            keyCodeFor(keyName).map { code =>
              val modifiers = modifierNames.foldLeft(KeyModifiers.None)((acc, name) => acc | ModifierNames(name))
              KeyEvent(code, modifiers)
            }
          case Nil          => Left("no key name (only modifiers)")
          case other        => Left(s"multiple key names: ${other.mkString(", ")}")

  private def keyCodeFor(name: String): Either[String, KeyCode] =
    name match
      case "enter"                                                                        => Right(KeyCode.Enter)
      case "esc" | "escape"                                                               => Right(KeyCode.Escape)
      case "tab"                                                                          => Right(KeyCode.Tab)
      case "space"                                                                        => Right(KeyCode.Char(' '))
      case "backspace"                                                                    => Right(KeyCode.Backspace)
      case "delete" | "del"                                                               => Right(KeyCode.Delete)
      case "insert"                                                                       => Right(KeyCode.Insert)
      case "home"                                                                         => Right(KeyCode.Home)
      case "end"                                                                          => Right(KeyCode.End)
      case "pageup" | "pgup"                                                              => Right(KeyCode.PageUp)
      case "pagedown" | "pgdn"                                                            => Right(KeyCode.PageDown)
      case "up"                                                                           => Right(KeyCode.Up)
      case "down"                                                                         => Right(KeyCode.Down)
      case "left"                                                                         => Right(KeyCode.Left)
      case "right"                                                                        => Right(KeyCode.Right)
      case f if f.startsWith("f") && f.drop(1).toIntOption.exists(n => n >= 1 && n <= 12) =>
        Right(KeyCode.F(f.drop(1).toInt))
      case single if single.length == 1 => Right(KeyCode.Char(single.head))
      case unknown                      => Left(s"unknown key '$unknown'")

  private val ModifierNames: Map[String, KeyModifiers] =
    Map("ctrl" -> KeyModifiers.Ctrl, "alt" -> KeyModifiers.Alt, "shift" -> KeyModifiers.Shift)
