package io.worxbend.tui.dsl

import org.scalatest.funsuite.AnyFunSuite

/** Focus in a list built with `column(items.map(row)*)` is positional: insert an item at the top and the highlight
  * stays on the screen position rather than on the item. `each` stamps a key derived from the item, which is what the
  * focus tracker re-anchors against. These tests pin both halves — the stamping, and the reorder behaviour it buys.
  */
final class EachSpec extends AnyFunSuite:

  private final case class Process(pid: Int, name: String)

  private val processes = Seq(Process(1, "init"), Process(42, "editor"), Process(7, "shell"))

  private def row(process: Process): Element = text(process.name).focusable

  test("each stamps the key the function derives and keeps the items in order"):
    val rows = each(processes)(_.pid.toString)(row)
    assert(rows.map(_.props.focusKey) == Seq(Some("1"), Some("42"), Some("7")))
    assert(rows.map(_.widget).size == 3)

  test("the prefixed form namespaces the keys so two lists cannot collide"):
    val left  = each(processes, "left")(_.pid.toString)(row)
    val right = each(processes, "right")(_.pid.toString)(row)
    assert(left.head.props.focusKey.contains("left:1"))
    assert(right.head.props.focusKey.contains("right:1"))
    assert(left.flatMap(_.props.focusKey).intersect(right.flatMap(_.props.focusKey)).isEmpty)

  test("the keys reach the focus pass in depth-first order"):
    val tree = column(each(processes)(_.pid.toString)(row)*)
    assert(FocusPass.focusKeys(tree) == Vector(Some("1"), Some("42"), Some("7")))

  test("focus follows the item when one is inserted above it"):
    val tracker  = FocusTracker()
    val before   = column(each(processes)(_.pid.toString)(row)*)
    tracker.reconcile(FocusPass.focusKeys(before), FocusPass.autofocusRequest(before))
    tracker.focusTo(1) // the editor
    // A deliberate move forgets the remembered key, so reconcile once more to re-derive it from the new index.
    tracker.reconcile(FocusPass.focusKeys(before), FocusPass.autofocusRequest(before))
    assert(tracker.focusedKey.contains("42"))

    val grown    = column(each(Process(99, "daemon") +: processes)(_.pid.toString)(row)*)
    tracker.reconcile(FocusPass.focusKeys(grown), FocusPass.autofocusRequest(grown))
    assert(tracker.focusedIndex == 2, "the editor moved down one, and focus should have moved with it")
    assert(tracker.focusedKey.contains("42"))

  test("without keys the same insertion leaves focus on the position, not the item"):
    val tracker = FocusTracker()
    val before  = column(processes.map(row)*)
    tracker.reconcile(FocusPass.focusKeys(before), FocusPass.autofocusRequest(before))
    tracker.focusTo(1)
    val grown   = column((Process(99, "daemon") +: processes).map(row)*)
    tracker.reconcile(FocusPass.focusKeys(grown), FocusPass.autofocusRequest(grown))
    assert(tracker.focusedIndex == 1, "this is the behaviour `each` exists to replace")

  test("an empty list produces no children and splices cleanly"):
    assert(each(Seq.empty[Process])(_.pid.toString)(row).isEmpty)
    assert(column(each(Seq.empty[Process])(_.pid.toString)(row)*).children.isEmpty)

  test("keys survive an item whose name needs display-width arithmetic to draw"):
    // The key is derived from the item, not from what is painted, so wide characters cannot disturb it.
    val wide = Seq(Process(1, "設定"), Process(2, "👩‍💻 dev"))
    assert(each(wide)(_.pid.toString)(row).flatMap(_.props.focusKey) == Seq("1", "2"))
