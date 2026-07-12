package io.worxbend.tui.examples.formdemo

import io.worxbend.tui.dsl.*
import io.worxbend.tui.macros.{deriveForm, Field}

/** form-demo (PLAN.md §8, example 5): a form derived at compile time from a case class (`deriveForm`, zero reflection)
  * with cue4s-style `Field.mapValidated` validation surfacing errors in the UI.
  *
  * Keys: type in fields · `Tab` next field · `Space` toggles the checkbox · `Ctrl+S` submit · `Esc` quit.
  */
final case class Signup(username: String, age: Int, subscribe: Boolean)

final class FormDemoApp extends TuiApp:

  val formState: FormState[Signup] = FormState.of(
    deriveForm[Signup],
    Field
      .text("username")
      .mapValidated(name => if name.trim.nonEmpty then Right(name.trim) else Left("required")),
    Field
      .int("age")
      .mapValidated(age => if age >= 18 then Right(age) else Left("must be 18 or older")),
  )

  def view(using ReactiveScope): Element =
    panel("Signup")(
      Form(formState),
      spacer,
      formState.result.get match
        case Some(signup) => text(s"submitted: $signup").color(Color.Green)
        case None         => text("Tab: next · Space: toggle · Ctrl+S: submit · Esc: quit").dim,
    ).rounded.onKeyEvent {
      case KeyEvent(KeyCode.Char('s'), m) if m.has(KeyModifiers.Ctrl) =>
        formState.submit()
        true
      case KeyEvent(KeyCode.Escape, _) =>
        quit()
        true
      case _ => false
    }

object Main:
  def main(args: Array[String]): Unit =
    FormDemoApp().run().left.foreach(error => println(s"failed to run: $error"))
