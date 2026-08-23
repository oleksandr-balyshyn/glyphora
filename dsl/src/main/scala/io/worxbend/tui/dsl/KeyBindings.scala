package io.worxbend.tui.dsl

import io.worxbend.tui.core.KeyEvent

/** One declared application key: the keys that fire it, the short label shown in status-bar hints, a description for
  * the help overlay, and the action.
  *
  * `triggers` is a sequence because one command often answers to several keys — a vim-flavoured app wants `j` and
  * `down` to mean the same thing. Declaring the action twice would work for dispatch but would double it everywhere
  * else: two hints in the status bar, two rows in the help overlay, two identical entries in the command palette.
  * `label` is chosen rather than derived from `triggers`, so the app decides which of them the user is shown.
  */
final case class KeyBinding(
    triggers: Seq[KeyEvent],
    label: String,
    description: String,
    action: () => Unit,
    showInHints: Boolean = true,
)

/** Declares one binding from a key spec string: `"q"`, `"ctrl+s"`, `"shift+tab"`, `"esc"`, `"f2"`, `"up"`, `"+"`… A
  * malformed spec is a programmer error and throws at construction (bindings are static app declarations).
  *
  * The spec vocabulary is [[io.worxbend.tui.core.KeyEvent.parse]]'s — the same one `Pilot.press` takes, so a test
  * presses the string the app declared.
  */
def binding(key: String, description: String)(action: => Unit): KeyBinding =
  KeyBinding(Seq(parseSpec(key)), key, description, () => action)

/** Declares one binding that several keys fire: `binding(Seq("down", "j"), "next")(…)` answers to both and still shows
  * one `down next` hint, because the first spec is the label.
  *
  * A `Seq` rather than a `String*` variadic on purpose: `binding("down", "j")(…)` would be indistinguishable from the
  * single-key `binding(key, description)` form above, and would silently read the description as a second key.
  */
def binding(keys: Seq[String], description: String)(action: => Unit): KeyBinding =
  require(keys.nonEmpty, "a binding needs at least one key spec")
  KeyBinding(keys.map(parseSpec), keys.head, description, () => action)

/** Parses one spec or throws — bindings are static declarations, so a bad spec is a programmer error that should fail
  * at startup rather than turn into a key that never fires.
  */
private def parseSpec(key: String): KeyEvent =
  KeyEvent.parse(key) match
    case Right(trigger) => trigger
    case Left(problem)  => throw IllegalArgumentException(s"bad key spec '$key': $problem")

/** The application's declared keys (bubbles' `key`+`help` pattern): one declaration drives dispatch, the status-bar
  * hints, and the help overlay. `TuiApp` consults these for events no element consumed.
  */
final class KeyBindings private (val bindings: Seq[KeyBinding]):

  /** Runs the first binding any of whose triggers matches; `true` if one fired. */
  def handle(event: KeyEvent): Boolean =
    bindings.find(_.triggers.contains(event)).exists { bound =>
      bound.action()
      true
    }

  /** `(label, description)` pairs for the status bar, in declaration order — one per binding, whatever its trigger
    * count, so a command with several keys is advertised under the one its author chose.
    */
  def hints: Seq[(String, String)] =
    bindings.filter(_.showInHints).map(bound => (bound.label, bound.description))

  def ++(other: KeyBindings): KeyBindings = new KeyBindings(bindings ++ other.bindings)

object KeyBindings:

  val empty: KeyBindings = new KeyBindings(Seq.empty)

  def apply(declared: KeyBinding*): KeyBindings = new KeyBindings(declared)
