package io.worxbend.tui.examples.todolist

import io.worxbend.tui.dsl.*
import io.worxbend.tui.widgets.{ListState, TextInputState}

/** todo-list (PLAN.md §8, example 3): a `list` + `input` with focus switching — multi-widget focus management and
  * list-selection state, end to end.
  *
  * Keys: type + `Enter` to add · `Tab` to switch focus · `↑`/`↓` to select · `d` to delete · `Esc` to quit.
  */
final class TodoApp extends TuiApp:

  val items: Signal[Vector[String]] = Signal(Vector.empty)
  val inputState: TextInputState = TextInputState()
  val listState: ListState = ListState()

  def view(using ReactiveScope): Element =
    panel("Todo")(
      input(inputState, placeholder = "what needs doing?").onKeyEvent {
        case KeyEvent(KeyCode.Enter, _) =>
          addItem()
          true
        case _ => false
      },
      spacer(1),
      list(items.get.map(item => s"· $item"), listState).onKeyEvent {
        case KeyEvent(KeyCode.Char('d'), _) =>
          deleteSelected()
          true
        case _ => false
      },
      text("Enter: add · Tab: switch · ↑/↓: select · d: delete · Esc: quit").dim,
    ).rounded.onKeyEvent {
      case KeyEvent(KeyCode.Escape, _) =>
        quit()
        true
      case _ => false
    }

  private def addItem(): Unit =
    val value = inputState.value.trim
    if value.nonEmpty then
      items.update(_ :+ value)
      inputState.clear()

  private def deleteSelected(): Unit =
    listState.selected.foreach { index =>
      items.update(_.patch(index, Nil, 1))
      val remaining = items.peek.size
      listState.selected = if remaining == 0 then None else Some(math.min(index, remaining - 1))
    }

object Main:
  def main(args: Array[String]): Unit =
    TodoApp().run().left.foreach(error => println(s"failed to run: $error"))
